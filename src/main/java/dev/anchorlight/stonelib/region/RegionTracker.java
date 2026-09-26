package dev.anchorlight.stonelib.region;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Enter/exit edge detection for players moving through a {@link RegionIndex}.
 *
 * <p>Feed it each block-boundary move; it remembers which region every player is currently inside
 * and reports a {@link Transition} only when that changes. Standing still, or moving within the
 * same region, never re-triggers. Free of Bukkit types, so it can be unit tested directly; wire it
 * to {@code PlayerMoveEvent} yourself and call {@link #clear(UUID)} on quit.
 *
 * <pre>{@code
 * RegionTracker<Portal> tracker = new RegionTracker<>(index, Portal::id, Portal::enabled);
 *
 * tracker.update(player.getUniqueId(), world, x, y, z)
 *         .entered().ifPresent(portal -> activate(player, portal));
 * }</pre>
 *
 * @param <T> the region type
 */
public final class RegionTracker<T> {

    /** The result of one move. Both sides are empty when nothing changed. */
    public record Transition<T>(Optional<T> exited, Optional<T> entered) {
        private static final Transition<?> NONE = new Transition<>(Optional.empty(), Optional.empty());

        @SuppressWarnings("unchecked")
        static <T> Transition<T> none() {
            return (Transition<T>) NONE;
        }

        public boolean changed() {
            return exited.isPresent() || entered.isPresent();
        }
    }

    private final RegionIndex<T> index;
    private final Function<? super T, String> keyOf;
    private final Predicate<? super T> active;
    private final Map<UUID, T> current = new ConcurrentHashMap<>();

    /**
     * @param index  where regions are looked up
     * @param keyOf  stable identity of a region; a region replaced by one with the same key (for
     *               example after an edit) does not count as leaving and re-entering
     * @param active regions failing this test are treated as absent (for example disabled ones)
     */
    public RegionTracker(RegionIndex<T> index, Function<? super T, String> keyOf, Predicate<? super T> active) {
        this.index = Objects.requireNonNull(index, "index");
        this.keyOf = Objects.requireNonNull(keyOf, "keyOf");
        this.active = Objects.requireNonNull(active, "active");
    }

    public RegionTracker(RegionIndex<T> index, Function<? super T, String> keyOf) {
        this(index, keyOf, region -> true);
    }

    public Transition<T> update(UUID player, String world, int x, int y, int z) {
        T now = null;
        for (T candidate : index.candidatesFor(world, x >> 4, z >> 4)) {
            if (active.test(candidate) && index.boundsOf(candidate).contains(x, y, z)) {
                now = candidate;
                break;
            }
        }

        T previous = current.get(player);
        if (now == null) {
            if (previous == null) {
                return Transition.none();
            }
            current.remove(player);
            return new Transition<>(Optional.of(previous), Optional.empty());
        }

        current.put(player, now);
        if (previous != null && keyOf.apply(previous).equals(keyOf.apply(now))) {
            return Transition.none();
        }
        return new Transition<>(Optional.ofNullable(previous), Optional.of(now));
    }

    /** The region the player was last seen inside. */
    public Optional<T> currentRegion(UUID player) {
        return Optional.ofNullable(current.get(player));
    }

    public void clear(UUID player) {
        current.remove(player);
    }

    public void clearAll() {
        current.clear();
    }
}
