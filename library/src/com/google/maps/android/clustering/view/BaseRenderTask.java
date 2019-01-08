package com.google.maps.android.clustering.view;

import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;

import java.util.Set;

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
public abstract class BaseRenderTask<T extends ClusterItem> implements BaseClusterRenderer.RenderTask {
    private Runnable mCallback;
    private Projection mProjection;

    private final BaseClusterRenderer<T> clusterRenderer;
    final Set<? extends Cluster<T>> clusters;

    BaseRenderTask(BaseClusterRenderer<T> clusterRenderer, Set<? extends Cluster<T>> clusters) {
        this.clusterRenderer = clusterRenderer;
        this.clusters = clusters;
    }

    @Override
    public final void setCallback(Runnable callback) {
        mCallback = callback;
    }

    @Override
    public void setProjection(Projection projection) {
        this.mProjection = projection;
    }

    @Override
    public final void run() {
        if (clusters.equals(clusterRenderer.mClusters)) {
            mCallback.run();
            return;
        }

        // Prevent crashes: https://issuetracker.google.com/issues/35827242
        LatLngBounds visibleBounds;
        try {
            visibleBounds = mProjection.getVisibleRegion().latLngBounds;
        } catch (Exception e) {
            e.printStackTrace();
            visibleBounds = LatLngBounds.builder()
                    .include(new LatLng(0, 0))
                    .build();
        }

        clusterRenderer.mMarkers = executeWork(visibleBounds);
        clusterRenderer.mClusters = clusters;

        mCallback.run();
    }

    protected abstract Set<BaseClusterRenderer.MarkerWithPosition> executeWork(LatLngBounds visibleBounds);
}
