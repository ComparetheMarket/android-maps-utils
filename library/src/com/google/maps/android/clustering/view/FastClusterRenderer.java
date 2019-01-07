/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.maps.android.clustering.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import android.os.Message;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The default view for a ClusterManager.
 */
public class FastClusterRenderer<T extends ClusterItem> extends BaseClusterRenderer<T> {

    public FastClusterRenderer(Context context, GoogleMap map, ClusterManager<T> clusterManager) {
        super(context, map, clusterManager);
    }

    @Override
    BaseClusterRenderer.RenderTask createRenderTask(Set<? extends Cluster<T>> clusters) {
        return new FastRenderTask(clusters);
    }

    /**
     * Transforms the current view (represented by FastClusterRenderer.mClusters and FastClusterRenderer.mZoom) to a
     * new zoom level and set of clusters.
     * <p/>
     * This must be run off the UI thread. Work is coordinated in the RenderTask, then queued up to
     * be executed by a MarkerModifier.
     * <p/>
     * There are three stages for the render:
     * <p/>
     * 1. Markers are added to the map
     * <p/>
     * 2. Any old markers are removed from the map
     * <p/>
     * When zooming in, markers are created out from the nearest existing cluster. When zooming
     * out, existing clusters are moved into to the nearest new cluster.
     */
    private class FastRenderTask extends BaseRenderTask<T> {

        private FastRenderTask(Set<? extends Cluster<T>> clusters) {
            super(FastClusterRenderer.this, clusters);
        }

        @Override
        protected Set<MarkerWithPosition> executeWork(LatLngBounds visibleBounds) {
            final MarkerModifier markerModifier = new MarkerModifier();
            final Set<BaseClusterRenderer.MarkerWithPosition> markersToRemove = mMarkers;

            // Create the new markers.
            final Set<MarkerWithPosition> newMarkers = Collections.newSetFromMap(
                    new ConcurrentHashMap<MarkerWithPosition, Boolean>());

            List<CreateMarkersTask> onScreenToAdd = new ArrayList<>();
            List<CreateMarkersTask> offScreenToAdd = new ArrayList<>();
            for (Cluster<T> c : clusters) {
                boolean onScreen = visibleBounds.contains(c.getPosition());
                CreateMarkersTask markersTask = new CreateMarkersTask(c, newMarkers);
                if (onScreen) {
                    onScreenToAdd.add(markersTask);
                } else {
                    offScreenToAdd.add(markersTask);
                }
            }

            markerModifier.add(true, onScreenToAdd);
            markerModifier.add(false, offScreenToAdd);

            // Wait for all markers to be added.
            markerModifier.waitUntilFree();

            // Don't remove any markers that were just added. This is basically anything that had
            // a hit in the MarkerCache.
            markersToRemove.removeAll(newMarkers);

            // Remove the old markers.
            List<Marker> onScreenToRemove = new ArrayList<>();
            List<Marker> offScreenToRemove = new ArrayList<>();
            for (final MarkerWithPosition marker : markersToRemove) {
                boolean onScreen = visibleBounds.contains(marker.position);
                if (onScreen) {
                    onScreenToRemove.add(marker.marker);
                } else {
                    offScreenToRemove.add(marker.marker);
                }
            }

            markerModifier.remove(true, onScreenToRemove);
            markerModifier.remove(false, offScreenToRemove);

            markerModifier.waitUntilFree();

            return newMarkers;
        }
    }

    /**
     * Handles all markerWithPosition manipulations on the map. Work (such as adding, or removing a markerWithPosition)
     * is performed while trying not to block the rest of the app's UI.
     */
    @SuppressLint("HandlerLeak")
    private class MarkerModifier extends BaseMarkerModifier<List<CreateMarkersTask>, List<Marker>> {

        @Override
        void performNextTask() {
            if (!mOnScreenRemoveMarkersTasks.isEmpty()) {
                removeMarkers(mOnScreenRemoveMarkersTasks.poll());

            } else if (!mOnScreenCreateMarkersTasks.isEmpty()) {
                addMarkers(mOnScreenCreateMarkersTasks.poll());

            } else if (!mCreateMarkersTasks.isEmpty()) {
                addMarkers(mCreateMarkersTasks.poll());

            } else if (!mRemoveMarkersTasks.isEmpty()) {
                removeMarkers(mRemoveMarkersTasks.poll());
            }
        }

        private void addMarkers(List<CreateMarkersTask> createMarkersTaskList) {
            for (CreateMarkersTask task : createMarkersTaskList) {
                task.perform();
            }
        }

        private void removeMarkers(List<Marker> markers) {
            for (Marker m : markers) {
                Cluster<T> cluster = mMarkerToCluster.get(m);
                mClusterToMarker.remove(cluster);
                mMarkerCache.remove(m);
                mMarkerToCluster.remove(m);
                mClusterManager.getMarkerManager().remove(m);
            }
        }
    }

    /**
     * Creates markerWithPosition(s) for a particular cluster.
     */
    private class CreateMarkersTask {
        private final Cluster<T> cluster;
        private final Set<MarkerWithPosition> newMarkers;

        /**
         * @param c            the cluster to render.
         * @param markersAdded a collection of markers to append any created markers.
         */
        public CreateMarkersTask(Cluster<T> c, Set<MarkerWithPosition> markersAdded) {
            this.cluster = c;
            this.newMarkers = markersAdded;
        }

        private void perform() {
            // Don't show small clusters. Render the markers inside, instead.
            if (!shouldRenderAsCluster(cluster)) {
                for (T item : cluster.getItems()) {
                    Marker marker = mMarkerCache.get(item);
                    MarkerWithPosition markerWithPosition;
                    if (marker == null) {
                        MarkerOptions markerOptions = new MarkerOptions();
                        markerOptions.position(item.getPosition());
                        if (!(item.getTitle() == null) && !(item.getSnippet() == null)) {
                            markerOptions.title(item.getTitle());
                            markerOptions.snippet(item.getSnippet());
                        } else if (!(item.getSnippet() == null)) {
                            markerOptions.title(item.getSnippet());
                        } else if (!(item.getTitle() == null)) {
                            markerOptions.title(item.getTitle());
                        }
                        onBeforeClusterItemRendered(item, markerOptions);
                        marker = mClusterManager.getMarkerCollection().addMarker(markerOptions);
                        markerWithPosition = new MarkerWithPosition(marker);
                        mMarkerCache.put(item, marker);
                    } else {
                        markerWithPosition = new MarkerWithPosition(marker);
                    }
                    onClusterItemRendered(item, marker);
                    newMarkers.add(markerWithPosition);
                }
                return;
            }

            Marker marker = mClusterToMarker.get(cluster);
            MarkerWithPosition markerWithPosition;
            if (marker == null) {
                MarkerOptions markerOptions = new MarkerOptions().
                        position(cluster.getPosition());
                onBeforeClusterRendered(cluster, markerOptions);
                marker = mClusterManager.getClusterMarkerCollection().addMarker(markerOptions);
                mMarkerToCluster.put(marker, cluster);
                mClusterToMarker.put(cluster, marker);
                markerWithPosition = new MarkerWithPosition(marker);
            } else {
                markerWithPosition = new MarkerWithPosition(marker);
            }
            onClusterRendered(cluster, marker);
            newMarkers.add(markerWithPosition);
        }
    }
}
