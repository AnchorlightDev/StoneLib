package dev.anchorlight.stonelib.scaling;

/**
 * How a community target scales with the number of people actually playing.
 *
 * <p>A fixed target written for twenty players is unreachable for three, and a shared goal nobody
 * can finish is a gate nobody can open. That is not hypothetical - it is the usual way a community
 * event stalls on its first dev test.
 *
 * @see ScaledTarget for the arithmetic and its clamps
 */
public enum Scaling {

    /** Fixed target. Correct for one-shot goals - lighting a portal, filling twelve frames. */
    NONE,

    /** {@code targetPerPlayer * population}, clamped. For counting goals. */
    PER_PLAYER,

    /** {@code ceil(fraction * population)}, clamped. For state goals - how many are sleeping. */
    FRACTION_OF_ONLINE
}
