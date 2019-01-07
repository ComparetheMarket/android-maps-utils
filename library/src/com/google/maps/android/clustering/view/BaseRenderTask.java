package com.google.maps.android.clustering.view;

import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;

import java.util.Set;

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
