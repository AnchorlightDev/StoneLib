package dev.anchorlight.stonelib.storage;

import java.util.Map;

/**
 * Converts a domain object to and from a flat map for YAML/SQLite persistence.
 */
public interface RecordCodec<V> {
    Map<String, Object> toMap(V value);
    V fromMap(Map<String, Object> map);
}
