package com.google.maps.android.clustering.view;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.view.model.MarkerWithPosition;

import java.util.Set;

/**
 * Creates markerWithPosition(s) for a particular cluster.
 */
class BaseCreateMarkersTask<T extends ClusterItem, M extends BaseMarkerModifier> {
    private final BaseClusterRenderer<T> clusterRenderer;
    private final Cluster<T> cluster;
    private final Set<MarkerWithPosition> newMarkers;

    /**
     * @param c            the cluster to render.
     * @param markersAdded a collection of markers to append any created markers.
     */
    BaseCreateMarkersTask(BaseClusterRenderer<T> clusterRenderer, Cluster<T> c, Set<MarkerWithPosition> markersAdded) {
        this.clusterRenderer = clusterRenderer;
        this.cluster = c;
        this.newMarkers = markersAdded;
    }

    void setPosition(MarkerOptions markerOptions, LatLng position) {
        markerOptions.position(position);
    }

    void onMarkerCreated(M markerModifier, MarkerWithPosition markerWithPosition, LatLng position) {
        // no-op
    }

    void perform(M markerModifier) {
        // Don't show small clusters. Render the markers inside, instead.
        if (!clusterRenderer.shouldRenderAsCluster(cluster)) {
            for (T item : cluster.getItems()) {
                Marker marker = clusterRenderer.mMarkerCache.get(item);
                MarkerWithPosition markerWithPosition;
                if (marker == null) {
                    MarkerOptions markerOptions = new MarkerOptions();
                    setPosition(markerOptions, item.getPosition());

                    if (!(item.getTitle() == null) && !(item.getSnippet() == null)) {
                        markerOptions.title(item.getTitle());
                        markerOptions.snippet(item.getSnippet());

                    } else if (!(item.getSnippet() == null)) {
                        markerOptions.title(item.getSnippet());

                    } else if (!(item.getTitle() == null)) {
                        markerOptions.title(item.getTitle());
                    }

                    clusterRenderer.onBeforeClusterItemRendered(item, markerOptions);
                    marker = clusterRenderer.mClusterManager.getMarkerCollection().addMarker(markerOptions);
                    markerWithPosition = new MarkerWithPosition(marker);
                    clusterRenderer.mMarkerCache.put(item, marker);
                    onMarkerCreated(markerModifier, markerWithPosition, item.getPosition());
                } else {
                    markerWithPosition = new MarkerWithPosition(marker);
                }
                clusterRenderer.onClusterItemRendered(item, marker);
                newMarkers.add(markerWithPosition);
            }
            return;
        }

        Marker marker = clusterRenderer.mClusterToMarker.get(cluster);
        MarkerWithPosition markerWithPosition;
        if (marker == null) {
            MarkerOptions markerOptions = new MarkerOptions().
                    position(cluster.getPosition());
            setPosition(markerOptions, cluster.getPosition());
            clusterRenderer.onBeforeClusterRendered(cluster, markerOptions);
            marker = clusterRenderer.mClusterManager.getClusterMarkerCollection().addMarker(markerOptions);
            clusterRenderer.mMarkerToCluster.put(marker, cluster);
            clusterRenderer.mClusterToMarker.put(cluster, marker);
            markerWithPosition = new MarkerWithPosition(marker);
            onMarkerCreated(markerModifier, markerWithPosition, cluster.getPosition());
        } else {
            markerWithPosition = new MarkerWithPosition(marker);
        }
        clusterRenderer.onClusterRendered(cluster, marker);
        newMarkers.add(markerWithPosition);
    }
}
