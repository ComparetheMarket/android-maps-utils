package com.google.maps.android.clustering.view.model;

import com.google.android.gms.maps.model.Marker;

import java.util.HashMap;
import java.util.Map;

/**
 * A cache of markers representing individual ClusterItems.
 */
public class MarkerCache<T> {
    private Map<T, Marker> mCache = new HashMap<>();
    private Map<Marker, T> mCacheReverse = new HashMap<>();

    public Marker get(T item) {
        return mCache.get(item);
    }

    public T get(Marker m) {
        return mCacheReverse.get(m);
    }

    public void put(T item, Marker m) {
        mCache.put(item, m);
        mCacheReverse.put(m, item);
    }

    public void remove(Marker m) {
        T item = mCacheReverse.get(m);
        mCacheReverse.remove(m);
        mCache.remove(item);
    }
}
