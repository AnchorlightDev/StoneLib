package dev.anchorlight.stonelib.scaling;

import java.util.Locale;

/**
 * Resolves a configured community target against the current population.
 *
 * <p>Pure arithmetic with no Bukkit types, so it is unit-testable and usable from a proxy. Supply
 * the population yourself - {@link PopulationWindow} is the usual source.
 */
public final class ScaledTarget {

    private ScaledTarget() {
    }

    /**
     * The configured shape of a scalable target, as read from config.
     *
     * @param scaling   how the target responds to population
     * @param base      the configured baseline, used directly when scaling is {@link Scaling#NONE}
     *                  and as the fallback when the mode-specific field is unset
     * @param perPlayer multiplier for {@link Scaling#PER_PLAYER}
     * @param fraction  fraction for {@link Scaling#FRACTION_OF_ONLINE}
     * @param min       floor applied after scaling; at least 1
     * @param max       ceiling applied after scaling; {@code base} is used when this is not positive
     */
    public record Spec(Scaling scaling, int base, int perPlayer, double fraction, int min, int max) {

        /** A fixed target that ignores population. */
        public static Spec fixed(int base) {
            return new Spec(Scaling.NONE, base, 0, 0.0, 1, base);
        }

        public Spec {
            scaling = scaling == null ? Scaling.NONE : scaling;
            base = Math.max(1, base);
            min = Math.max(1, min);
        }
    }

    /**
     * The target to actually compare progress against right now.
     *
     * <p>The result is never below {@code min} and never below 1. Callers holding accumulated
     * progress should additionally floor the result at that progress - see
     * {@link #atLeastProgress(int, int)} - so that a shrinking population can lower the bar but can
     * never invalidate work already done or make a goal un-completable.
     *
     * @param population the reference population; values below 1 are treated as 1
     */
    public static int compute(Spec spec, int population) {
        if (spec == null) {
            return 1;
        }
        if (spec.scaling() == Scaling.NONE) {
            return Math.max(1, spec.base());
        }
        int people = Math.max(1, population);
        int computed = switch (spec.scaling()) {
            case PER_PLAYER -> spec.perPlayer() > 0 ? spec.perPlayer() * people : spec.base();
            case FRACTION_OF_ONLINE -> spec.fraction() > 0
                    ? (int) Math.ceil(spec.fraction() * people) : spec.base();
            case NONE -> spec.base();
        };
        int ceiling = spec.max() > 0 ? spec.max() : spec.base();
        return Math.max(1, Math.min(ceiling, Math.max(spec.min(), computed)));
    }

    /**
     * Floors a computed target at the progress already banked.
     *
     * <p>Apply this to any target backed by accumulated progress. Without it, a population drop
     * between two contributions can move the target below what players have already earned, which
     * either completes the goal by accident or - worse, if the caller compares for equality -
     * strands it forever.
     */
    public static int atLeastProgress(int target, int progressSoFar) {
        return Math.max(target, progressSoFar);
    }

    /** Parses a {@link Scaling} leniently, falling back rather than throwing on a config typo. */
    public static Scaling parse(String raw, Scaling fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Scaling.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
