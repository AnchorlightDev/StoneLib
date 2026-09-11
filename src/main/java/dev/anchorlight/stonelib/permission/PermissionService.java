package dev.anchorlight.stonelib.permission;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LuckPerms-backed permission lookups, with a per-player cache and support for nodes that expire
 * on their own.
 *
 * <p>Two things here are worth having in a shared library rather than in each plugin:</p>
 *
 * <ul>
 *   <li>{@link #resolve(Player, Set)} answers "which of these hundred nodes does this player
 *       have?" once on join and caches the answer, so a render loop never asks the permission
 *       system anything.</li>
 *   <li>{@link #grantTemporary} adds a node that LuckPerms itself expires, which is how a
 *       week-long event reward stays a week long without a plugin remembering to take it back —
 *       including across a restart.</li>
 * </ul>
 *
 * <p>The cache is authoritative only for what it was asked to resolve. Call
 * {@link #invalidate(UUID)} when a grant changes, and always on quit.</p>
 */
public final class PermissionService {

    private final Logger logger;
    private final Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public PermissionService(Logger logger) {
        this.logger = logger;
    }

    /** Whether LuckPerms is present. Everything else degrades to Bukkit checks when it is not. */
    public boolean available() {
        try {
            LuckPermsBackend.probe();
            return true;
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Works out which of {@code candidates} the player holds and caches the result.
     *
     * <p>Uses the Bukkit permission check, which reads LuckPerms' already-loaded data for an
     * online player, so this is cheap and does not hit the permission storage. Call it on join.</p>
     */
    public Set<String> resolve(Player player, Set<String> candidates) {
        Set<String> held = new LinkedHashSet<>();
        for (String node : candidates) {
            if (player.hasPermission(node)) {
                held.add(node);
            }
        }
        cache.put(player.getUniqueId(), held);
        return held;
    }

    /**
     * Whether a player holds a node, answered from the cache when it has been resolved.
     *
     * <p>Falls back to a live Bukkit check when there is no cached answer, so a caller can never
     * get a wrong "no" just because {@link #resolve} has not run yet.</p>
     */
    public boolean has(Player player, String node) {
        Set<String> held = cache.get(player.getUniqueId());
        if (held != null && held.contains(node)) {
            return true;
        }
        return player.hasPermission(node);
    }

    /** The cached set for a player, or an empty set if nothing has been resolved. */
    public Set<String> cached(UUID uuid) {
        return cache.getOrDefault(uuid, Set.of());
    }

    /** Drops a player's cached answer. Call on quit, and whenever their nodes change. */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    /** Drops every cached answer, for a reload command. */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Grants a node permanently.
     *
     * @return a future completing with true once LuckPerms has saved the change
     */
    public CompletableFuture<Boolean> grant(UUID uuid, String node) {
        return modify(uuid, node, null, true);
    }

    /**
     * Grants a node that expires by itself after {@code duration} — an event prize, or a trial.
     *
     * <p>LuckPerms stores the expiry, so it survives restarts and applies on every server sharing
     * the permission storage. Nothing here has to remember to revoke it.</p>
     */
    public CompletableFuture<Boolean> grantTemporary(UUID uuid, String node, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Temporary grants need a positive duration: " + duration);
        }
        return modify(uuid, node, duration, true);
    }

    /** Removes a node, whether it was permanent or temporary. */
    public CompletableFuture<Boolean> revoke(UUID uuid, String node) {
        return modify(uuid, node, null, false);
    }

    private CompletableFuture<Boolean> modify(UUID uuid, String node, Duration expiry, boolean add) {
        if (!available()) {
            logger.warning("Cannot change permissions: LuckPerms is not loaded");
            return CompletableFuture.completedFuture(false);
        }
        try {
            return LuckPermsBackend.modify(uuid, node, expiry, add)
                    .thenApply(ignored -> {
                        invalidate(uuid);
                        return true;
                    })
                    .exceptionally(e -> {
                        logger.log(Level.WARNING, "Failed to " + (add ? "grant" : "revoke")
                                + " " + node + " for " + uuid, e);
                        return false;
                    });
        } catch (NoClassDefFoundError e) {
            logger.log(Level.WARNING, "LuckPerms went away mid-call", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Every reference to LuckPerms lives in here.
     *
     * <p>That is not tidiness: a type named in an enclosing class's own method signatures has to
     * resolve when that class is linked, so a {@code Node} parameter on a private method of
     * {@link PermissionService} is enough to throw {@link NoClassDefFoundError} on a server
     * without LuckPerms — before {@link #available()} can be consulted. Keeping the types in a
     * nested class defers loading until something actually calls in.</p>
     */
    private static final class LuckPermsBackend {

        private LuckPermsBackend() {
        }

        /**
         * Throws if LuckPerms is absent or not yet loaded. Loading this nested class is what
         * triggers the {@link NoClassDefFoundError}, which is exactly what we want to catch.
         */
        static void probe() {
            net.luckperms.api.LuckPermsProvider.get();
        }

        static CompletableFuture<Void> modify(UUID uuid, String node, Duration expiry, boolean add) {
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            return luckPerms.getUserManager().modifyUser(uuid, user -> {
                if (add) {
                    net.luckperms.api.node.types.PermissionNode.Builder builder =
                            net.luckperms.api.node.types.PermissionNode.builder(node);
                    if (expiry != null) {
                        builder.expiry(expiry);
                    }
                    user.data().add(builder.build());
                } else {
                    // Remove by key, so a permanent node and an expiring one both go.
                    user.data().clear(existing -> existing.getKey().equalsIgnoreCase(node));
                }
            });
        }
    }
}
