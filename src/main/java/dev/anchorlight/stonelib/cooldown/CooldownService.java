package dev.anchorlight.StoneLib.cooldown;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cooldown tracker keyed by {@code UUID -> key -> expiry}.
 *
 * <p>The per-player key means one service can hold every cooldown a plugin has - an ability, a
 * command, a toggle - without a map per feature. Expired entries are dropped lazily when they are
 * next read, so there is nothing to schedule and nothing to clean up.
 *
 * <p>Cooldowns are memory-only and deliberately do not survive a restart; persist them yourself if
 * your feature needs that.
 *
 * <pre>{@code
 * CooldownService cooldowns = new CooldownService("myplugin.cooldown.bypass");
 *
 * if (!cooldowns.tryUse(player, "dash", Duration.ofSeconds(10))) {
 *     messages.sendNamed(player, "on_cooldown",
 *             "time", Durations.human(cooldowns.remaining(player.getUniqueId(), "dash")));
 *     return;
 * }
 * }</pre>
 */
public final class CooldownService {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final String bypassPermission;

    /** A service with no bypass permission: {@link #canBypass} is always false. */
    public CooldownService() {
        this(null);
    }

    /**
     * @param bypassPermission permission node that exempts a player from every cooldown, or null
     */
    public CooldownService(String bypassPermission) {
        this.bypassPermission = bypassPermission;
    }

    public boolean isOnCooldown(UUID uuid, String key) {
        return remainingMillis(uuid, key) > 0;
    }

    public long remainingMillis(UUID uuid, String key) {
        Map<String, Long> keys = cooldowns.get(uuid);
        if (keys == null) {
            return 0;
        }
        Long expiry = keys.get(key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            keys.remove(key);
            return 0;
        }
        return remaining;
    }

    public Duration remaining(UUID uuid, String key) {
        return Duration.ofMillis(remainingMillis(uuid, key));
    }

    public void apply(UUID uuid, String key, Duration cooldown) {
        applyMillis(uuid, key, cooldown == null ? 0 : cooldown.toMillis());
    }

    public void applyMillis(UUID uuid, String key, long cooldownMillis) {
        if (cooldownMillis <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + cooldownMillis);
    }

    public void clear(UUID uuid, String key) {
        Map<String, Long> keys = cooldowns.get(uuid);
        if (keys != null) {
            keys.remove(key);
        }
    }

    public void clearAll(UUID uuid) {
        cooldowns.remove(uuid);
    }

    /** Drops every tracked cooldown. Useful on reload. */
    public void clearEverything() {
        cooldowns.clear();
    }

    /** True when this service has a bypass permission and the player holds it. */
    public boolean canBypass(Player player) {
        return bypassPermission != null && player != null && player.hasPermission(bypassPermission);
    }

    /**
     * Check and apply in one step. Returns true if the action is allowed - applying the cooldown as
     * a side effect - and false if it is still cooling down.
     */
    public boolean tryUse(Player player, String key, Duration cooldown) {
        if (player == null) {
            return false;
        }
        if (canBypass(player)) {
            return true;
        }
        if (isOnCooldown(player.getUniqueId(), key)) {
            return false;
        }
        apply(player.getUniqueId(), key, cooldown);
        return true;
    }

    /** Every live cooldown for one player, as key to remaining milliseconds. */
    public Map<String, Long> snapshot(UUID uuid) {
        Map<String, Long> keys = cooldowns.get(uuid);
        if (keys == null) {
            return Map.of();
        }
        Map<String, Long> out = new ConcurrentHashMap<>();
        for (String key : keys.keySet()) {
            long remaining = remainingMillis(uuid, key);
            if (remaining > 0) {
                out.put(key, remaining);
            }
        }
        return out;
    }
}
