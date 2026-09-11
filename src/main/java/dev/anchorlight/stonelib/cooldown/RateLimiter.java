package dev.anchorlight.stonelib.cooldown;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A per-key minimum interval between actions: {@link #tryAcquire} succeeds at most once per
 * interval for each key.
 *
 * <p>Unlike {@link CooldownService} this has no Bukkit types at all, so it is safe on a Velocity
 * proxy, and it is aimed at guarding inbound requests (plugin messages, API calls) rather than
 * player-facing cooldowns with messages and bypass permissions.
 *
 * <pre>{@code
 * RateLimiter connects = new RateLimiter(Duration.ofMillis(500));
 * if (!connects.tryAcquire(player.getUniqueId())) {
 *     reply(new ConnectFailed(id, "You are connecting too quickly, please wait."));
 *     return;
 * }
 * }</pre>
 */
public final class RateLimiter {

    private final long intervalMillis;
    private final LongSupplier clock;
    private final Map<Object, Long> lastAcquiredAt = new ConcurrentHashMap<>();

    public RateLimiter(Duration interval) {
        this(interval, System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public RateLimiter(Duration interval, LongSupplier clock) {
        this.intervalMillis = interval.toMillis();
        this.clock = clock;
    }

    /** True, recording the attempt, when the key hasn't acquired within the interval. */
    public boolean tryAcquire(Object key) {
        long now = clock.getAsLong();
        boolean[] acquired = {false};
        lastAcquiredAt.compute(key, (k, last) -> {
            if (last != null && now - last < intervalMillis) {
                return last;
            }
            acquired[0] = true;
            return now;
        });
        return acquired[0];
    }

    public void clear(Object key) {
        lastAcquiredAt.remove(key);
    }

    public void clearAll() {
        lastAcquiredAt.clear();
    }
}
