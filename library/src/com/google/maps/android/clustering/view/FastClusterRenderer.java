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

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.model.MarkerWithPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private class FastRenderTask extends BaseRenderTask<T> {

        private FastRenderTask(Set<? extends Cluster<T>> clusters) {
            super(FastClusterRenderer.this, clusters);
        }

        @Override
        protected Set<MarkerWithPosition> executeWork(LatLngBounds visibleBounds) {
            final MarkerModifier markerModifier = new MarkerModifier();
            final Set<MarkerWithPosition> markersToRemove = mMarkers;

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
                boolean onScreen = visibleBounds.contains(marker.getPosition());
                if (onScreen) {
                    onScreenToRemove.add(marker.getMarker());
                } else {
                    offScreenToRemove.add(marker.getMarker());
                }
            }

            markerModifier.remove(true, onScreenToRemove);
            markerModifier.remove(false, offScreenToRemove);

            markerModifier.waitUntilFree();

            return newMarkers;
        }
    }

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
                task.perform(this);
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

    private class CreateMarkersTask extends BaseCreateMarkersTask<T, MarkerModifier> {
        CreateMarkersTask(Cluster<T> c, Set<MarkerWithPosition> markersAdded) {
            super(FastClusterRenderer.this, c, markersAdded);
        }
    }
}
