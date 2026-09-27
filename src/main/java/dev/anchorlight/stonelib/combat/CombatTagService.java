package dev.anchorlight.stonelib.combat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Tracks who is "in combat": a player who dealt or took hostile damage within the last N seconds.
 *
 * <p>This is the data half of a combat tag. What a tag <em>means</em> - no teleporting, no toggling
 * PvP off, no ender pearls - and how it is shown are the plugin's business. The service only answers
 * who is tagged and for how long, and says when someone enters or leaves combat, so every plugin
 * that needs a tag does not re-derive the expiry edge cases.
 *
 * <p>No Bukkit types, so it is unit-testable with an injected clock and safe to call from any
 * thread, including Folia region threads. Listeners run on the calling thread: {@link #tag} for
 * entering, {@link #untag} or {@link #expireDue} for leaving. Hop to the player's own thread before
 * touching the player from a listener.
 *
 * <pre>{@code
 * CombatTagService tags = new CombatTagService();
 * tags.onEnter(uuid -> sounds.play(uuid, "enter"));
 * tags.onLeave((uuid, reason) -> hideTimer(uuid));
 *
 * // on hostile damage, both ways
 * tags.tag(attacker, Duration.ofSeconds(15));
 * tags.tag(defender, Duration.ofSeconds(15));
 *
 * // once a second
 * tags.expireDue();
 * }</pre>
 */
public final class CombatTagService {

    /** Why a player stopped being tagged. */
    public enum LeaveReason {
        /** The timer ran out. */
        EXPIRED,
        /** Removed on purpose: death, logout, a staff command. */
        CLEARED
    }

    /** Receives the player who left combat and why. */
    @FunctionalInterface
    public interface LeaveListener {
        void left(UUID player, LeaveReason reason);
    }

    private final Map<UUID, Long> expiries = new ConcurrentHashMap<>();
    private final List<Consumer<UUID>> enterListeners = new CopyOnWriteArrayList<>();
    private final List<LeaveListener> leaveListeners = new CopyOnWriteArrayList<>();
    private final LongSupplier clock;

    public CombatTagService() {
        this(System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public CombatTagService(LongSupplier clock) {
        this.clock = clock;
    }

    public void onEnter(Consumer<UUID> listener) {
        enterListeners.add(listener);
    }

    public void onLeave(LeaveListener listener) {
        leaveListeners.add(listener);
    }

    /**
     * Tags {@code player} for {@code duration} from now, extending a live tag rather than shortening
     * it.
     *
     * @return true when the player was not already in combat, i.e. this call started a tag
     */
    public boolean tag(UUID player, Duration duration) {
        if (player == null || duration == null || duration.isZero() || duration.isNegative()) {
            return false;
        }
        long now = clock.getAsLong();
        long until = now + duration.toMillis();
        boolean[] entered = {false};
        expiries.compute(player, (id, current) -> {
            if (current == null || current <= now) {
                entered[0] = true;
                return until;
            }
            return Math.max(current, until);
        });
        if (entered[0]) {
            for (Consumer<UUID> listener : enterListeners) {
                listener.accept(player);
            }
        }
        return entered[0];
    }

    public boolean isTagged(UUID player) {
        return remainingMillis(player) > 0;
    }

    public long remainingMillis(UUID player) {
        if (player == null) {
            return 0;
        }
        Long until = expiries.get(player);
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - clock.getAsLong());
    }

    public Duration remaining(UUID player) {
        return Duration.ofMillis(remainingMillis(player));
    }

    /**
     * Ends a tag early. Leave listeners fire with {@link LeaveReason#CLEARED} only if the player was
     * actually tagged.
     *
     * @return true when a live tag was removed
     */
    public boolean untag(UUID player) {
        if (player == null) {
            return false;
        }
        Long until = expiries.remove(player);
        boolean wasLive = until != null && until > clock.getAsLong();
        if (wasLive) {
            fireLeave(player, LeaveReason.CLEARED);
        }
        return wasLive;
    }

    /**
     * Removes every tag whose time is up and fires leave listeners for each. Call this on a timer;
     * once a second is plenty for a timer players read in whole seconds.
     *
     * @return the players whose tag expired in this sweep
     */
    public List<UUID> expireDue() {
        long now = clock.getAsLong();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : expiries.entrySet()) {
            if (entry.getValue() <= now && expiries.remove(entry.getKey(), entry.getValue())) {
                expired.add(entry.getKey());
            }
        }
        for (UUID player : expired) {
            fireLeave(player, LeaveReason.EXPIRED);
        }
        return expired;
    }

    /** Everyone currently tagged. A snapshot; expired entries not yet swept are excluded. */
    public Set<UUID> tagged() {
        long now = clock.getAsLong();
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        expiries.forEach((player, until) -> {
            if (until > now) {
                out.add(player);
            }
        });
        return out;
    }

    /** Drops every tag without firing listeners. For plugin disable. */
    public void clearAll() {
        expiries.clear();
    }

    private void fireLeave(UUID player, LeaveReason reason) {
        for (LeaveListener listener : leaveListeners) {
            listener.left(player, reason);
        }
    }
}
