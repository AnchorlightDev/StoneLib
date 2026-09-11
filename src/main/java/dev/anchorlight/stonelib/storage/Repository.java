package dev.anchorlight.stonelib.storage;

import java.util.Collection;

/**
 * A keyed collection of records persisted to a backing store.
 */
public interface Repository<K, V> {
    void load();
    void save();
    V get(K key);
    void put(K key, V value);
    void remove(K key);
    Collection<V> all();
}
