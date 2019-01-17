package com.google.maps.android.clustering.view.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.view.BaseClusterRenderer;

/**
 * A Marker and its position. Marker.getPosition() must be called from the UI thread, so this
 * object allows lookup from other threads.
 */
public class MarkerWithPosition {
    private final Marker marker;
    private LatLng position;

    public MarkerWithPosition(Marker marker) {
        this.marker = marker;
        position = marker.getPosition();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MarkerWithPosition) {
            return marker.equals(((MarkerWithPosition) other).marker);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return marker.hashCode();
    }

    public Marker getMarker() {
        return marker;
    }

    public LatLng getPosition() {
        return position;
    }

    public void setPosition(LatLng position) {
        this.position = position;
    }
}
