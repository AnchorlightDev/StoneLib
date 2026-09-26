package dev.anchorlight.stonelib.aggregation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Folds high-frequency events into a rolling window per key, and reports a summary only once the
 * window is worth reporting.
 *
 * <p>The problem this solves is that the events worth detecting arrive far too fast to act on
 * individually. A player lavacasting produces thousands of block events a minute; a spam bot
 * produces hundreds of chat events; a broken redstone contraption produces tens of thousands of
 * block updates. Handling each one — a database write, an HTTP call, an alert — costs more than
 * the thing being detected. So events are counted in memory against a key, and only a summary
 * leaves the hot path.
 *
 * <p>No Bukkit types at all, so it is safe on a Velocity proxy and in plain unit tests. The event
 * handlers stay in your plugin and call {@link #record}; the policy for what counts as worth
 * reporting stays in your plugin too, as the predicate you hand {@link #drainReportable}.
 *
 * <p>Not thread-safe by design. Bukkit event handlers and a Bukkit scheduler task both run on the
 * main thread, so guarding this would cost every caller a lock for a contention case that does not
 * arise. Call it from one thread; if you need it from several, synchronise externally.
 *
 * <pre>{@code
 * // One window per player, ten minutes long, reported at most every 30s.
 * WindowedCounter<UUID> damage = new WindowedCounter<>(
 *         Duration.ofMinutes(10), Duration.ofSeconds(30), 2000);
 *
 * // In a hot event handler: two map lookups and an increment, nothing else.
 * damage.record(player.getUniqueId(), "broken", ChunkKey.ofBlock(x, z), 1);
 *
 * // On a timer: ship whatever has crossed the line.
 * for (var window : damage.drainReportable(w -> w.count("broken") >= 400 || w.distinctBuckets() >= 8)) {
 *     api.send(summaryOf(window));
 * }
 * damage.sweepIdle();
 * }</pre>
 *
 * @param <K> the key events are grouped by, typically a player id
 */
public final class WindowedCounter<K> {

    private final long windowMillis;
    private final long reportIntervalMillis;
    private final int maxKeys;
    private final LongSupplier clock;

    private final Map<K, Window<K>> windows = new HashMap<>();
    private long droppedKeys;

    /**
     * @param window         how long a key's counters accumulate before starting over
     * @param reportInterval minimum gap between reports for one key
     * @param maxKeys        hard cap on tracked keys; further keys are dropped and counted
     */
    public WindowedCounter(Duration window, Duration reportInterval, int maxKeys) {
        this(window, reportInterval, maxKeys, System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public WindowedCounter(Duration window, Duration reportInterval, int maxKeys, LongSupplier clock) {
        this.windowMillis = Objects.requireNonNull(window, "window").toMillis();
        this.reportIntervalMillis = Objects.requireNonNull(reportInterval, "reportInterval").toMillis();
        this.maxKeys = Math.max(1, maxKeys);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Adds to a named counter. Returns the key's window, or null once {@code maxKeys} is reached. */
    public Window<K> record(K key, String counter, long amount) {
        Window<K> window = windowFor(key);
        if (window == null) {
            return null;
        }
        window.add(counter, amount);
        return window;
    }

    /**
     * Adds to a named counter and attributes it to a bucket.
     *
     * <p>The bucket is any {@code long} identifier — a packed chunk key, a world id, a shard — and
     * both its own tally and the window's distinct-bucket count are kept. That is what turns "400
     * blocks broken" into "400 blocks broken across 30 chunks", which is usually the part that
     * distinguishes a problem from ordinary activity.
     */
    public Window<K> record(K key, String counter, long bucket, long amount) {
        Window<K> window = windowFor(key);
        if (window == null) {
            return null;
        }
        window.add(counter, amount);
        window.addToBucket(counter, bucket, amount);
        return window;
    }

    /** The key's current window without recording anything, or empty if it has none. */
    public Window<K> peek(K key) {
        Window<K> window = windows.get(key);
        if (window == null) {
            return null;
        }
        if (clock.getAsLong() - window.startedAt >= windowMillis) {
            window.reset(clock.getAsLong());
        }
        return window;
    }

    /**
     * Windows that satisfy {@code reportable} and are not inside their report interval.
     *
     * <p>Returning them marks them reported, so a key that stays over the line produces one report
     * per interval rather than one per drain. The windows are live objects and keep accumulating;
     * their counters are cumulative for the window, so successive reports for one key are supersets
     * of each other and a consumer can fold them in with a max per field rather than a sum.
     */
    public List<Window<K>> drainReportable(Predicate<? super Window<K>> reportable) {
        Objects.requireNonNull(reportable, "reportable");
        long now = clock.getAsLong();
        List<Window<K>> due = new ArrayList<>();

        for (Window<K> window : windows.values()) {
            if (now - window.startedAt >= windowMillis) {
                window.reset(now);
            }
            if (now - window.lastReportedAt < reportIntervalMillis) {
                continue;
            }
            if (!reportable.test(window)) {
                continue;
            }
            window.lastReportedAt = now;
            due.add(window);
        }
        return due;
    }

    /**
     * Forgets keys that have seen nothing for a whole window.
     *
     * <p>Call it from the same timer that drains, or the map grows with every key ever seen —
     * which on a busy server is every player who has ever logged in.
     */
    public int sweepIdle() {
        long now = clock.getAsLong();
        int removed = 0;
        for (Iterator<Map.Entry<K, Window<K>>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().lastActivityAt >= windowMillis) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Forgets one key, for a player who has just disconnected. */
    public Window<K> forget(K key) {
        return windows.remove(key);
    }

    public int trackedKeys() {
        return windows.size();
    }

    /** Keys refused because {@code maxKeys} was already reached. */
    public long droppedKeys() {
        return droppedKeys;
    }

    private Window<K> windowFor(K key) {
        long now = clock.getAsLong();
        Window<K> window = windows.get(key);

        if (window == null) {
            if (windows.size() >= maxKeys) {
                droppedKeys++;
                return null;
            }
            window = new Window<>(key, now);
            windows.put(key, window);
        } else if (now - window.startedAt >= windowMillis) {
            window.reset(now);
        }

        window.lastActivityAt = now;
        return window;
    }

    /**
     * One key's counters over the current window.
     *
     * <p>Mutated only by the owning {@link WindowedCounter}; a consumer reads it.
     */
    public static final class Window<K> {

        private final K key;
        private long startedAt;
        private long lastActivityAt;
        private long lastReportedAt;

        private final Map<String, Long> counts = new HashMap<>();
        private final Map<String, Map<Long, Long>> buckets = new HashMap<>();
        private final Set<Long> distinctBuckets = new HashSet<>();

        private Window(K key, long now) {
            this.key = key;
            this.startedAt = now;
            this.lastActivityAt = now;
            // Deliberately zero rather than `now`: a window that trips on its
            // very first event should report immediately, not one interval later.
            this.lastReportedAt = 0L;
        }

        public K key() {
            return key;
        }

        /** When the current window opened. */
        public long startedAt() {
            return startedAt;
        }

        public long lastActivityAt() {
            return lastActivityAt;
        }

        public long count(String counter) {
            return counts.getOrDefault(counter, 0L);
        }

        /** Every named counter in this window. */
        public Map<String, Long> counts() {
            return Collections.unmodifiableMap(counts);
        }

        /** How many distinct buckets this window has touched, across all counters. */
        public int distinctBuckets() {
            return distinctBuckets.size();
        }

        public Set<Long> bucketIds() {
            return Collections.unmodifiableSet(distinctBuckets);
        }

        /** Per-bucket tallies for one counter, empty if it was never recorded with a bucket. */
        public Map<Long, Long> bucketCounts(String counter) {
            Map<Long, Long> perBucket = buckets.get(counter);
            return perBucket == null ? Map.of() : Collections.unmodifiableMap(perBucket);
        }

        private void add(String counter, long amount) {
            counts.merge(counter, amount, Long::sum);
        }

        private void addToBucket(String counter, long bucket, long amount) {
            buckets.computeIfAbsent(counter, k -> new HashMap<>()).merge(bucket, amount, Long::sum);
            distinctBuckets.add(bucket);
        }

        private void reset(long now) {
            startedAt = now;
            counts.clear();
            buckets.clear();
            distinctBuckets.clear();
            lastReportedAt = 0L;
        }
    }
}
