package dev.anchorlight.stonelib.ownership;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who is responsible for a thing identified by a key - an end crystal's entity UUID, a primed
 * device - with the same expiry rules as {@link BlockOwnership}.
 *
 * <p>Key by a stable identifier such as {@code Entity#getUniqueId()}, not the entity object: a
 * {@code WeakHashMap<Entity, ...>} drops entries whenever the server swaps the wrapper, and holds
 * them for as long as anything else happens to reference it.
 *
 * <p>Thread-safe and free of Bukkit types.
 *
 * @param <K> the key type
 */
public final class ExpiringOwners<K> {

    private record Entry(UUID owner, long expiresAt) {
    }

    private final Map<K, Entry> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long ttlMillis;

    public ExpiringOwners(Duration ttl) {
        this(ttl, System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public ExpiringOwners(Duration ttl, LongSupplier clock) {
        this.clock = clock;
        setTtl(ttl);
    }

    public void setTtl(Duration ttl) {
        this.ttlMillis = ttl == null ? 0 : Math.max(0, ttl.toMillis());
    }

    public void put(K key, UUID owner) {
        if (key == null || owner == null || ttlMillis <= 0) {
            return;
        }
        entries.put(key, new Entry(owner, clock.getAsLong() + ttlMillis));
    }

    /** The live owner for {@code key}, or null when there is none or it has expired. */
    public UUID owner(K key) {
        if (key == null) {
            return null;
        }
        Entry entry = entries.get(key);
        return entry != null && entry.expiresAt() > clock.getAsLong() ? entry.owner() : null;
    }

    public void remove(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    /** @return how many expired entries were removed */
    public int purgeExpired() {
        long now = clock.getAsLong();
        int before = entries.size();
        entries.values().removeIf(entry -> entry.expiresAt() <= now);
        return before - entries.size();
    }

    public int size() {
        long now = clock.getAsLong();
        return (int) entries.values().stream().filter(entry -> entry.expiresAt() > now).count();
    }

    public void clear() {
        entries.clear();
    }
}
