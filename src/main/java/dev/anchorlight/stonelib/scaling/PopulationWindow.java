package dev.anchorlight.stonelib.scaling;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntSupplier;

/**
 * The reference population a scaled target is measured against: the <em>peak</em> concurrent count
 * over a trailing window, not the instantaneous count.
 *
 * <p>Deliberately a peak. If targets tracked the live count, logging off would shrink the goal, and
 * players would correctly work out that the fastest way to finish a community goal is for half the
 * server to quit. Taking the trailing peak means leaving makes a goal no easier, while a genuine
 * long-term drop in population still relaxes it once the window rolls over.
 *
 * <p>Thread-safe. {@link #sample()} is normally called from a repeating task and {@link #peak()}
 * from wherever a target is resolved.
 */
public final class PopulationWindow {

    /** {@code [timestampMillis, count]}, oldest first. */
    private final Deque<long[]> samples = new ArrayDeque<>();
    private final IntSupplier currentCount;
    private final long windowMillis;

    /**
     * @param currentCount how to read the population right now, e.g.
     *                     {@code () -> Bukkit.getOnlinePlayers().size()}
     * @param window       how far back the peak is taken over; values below one minute are raised
     *                     to one minute, since a shorter window barely differs from the live count
     */
    public PopulationWindow(IntSupplier currentCount, Duration window) {
        this.currentCount = currentCount;
        long millis = window == null ? 0 : window.toMillis();
        this.windowMillis = Math.max(Duration.ofMinutes(1).toMillis(), millis);
    }

    /** Records the current count. Call this on a timer - once every few seconds is plenty. */
    public void sample() {
        long now = System.currentTimeMillis();
        synchronized (samples) {
            samples.addLast(new long[]{now, currentCount.getAsInt()});
            trim(now);
        }
    }

    /**
     * The peak count over the trailing window, never below 1 and never below the live count.
     *
     * <p>Including the live count matters on a fresh boot, before any sample has been taken: a
     * target resolved in that gap would otherwise scale against an empty server.
     */
    public int peak() {
        long now = System.currentTimeMillis();
        int peak = Math.max(1, currentCount.getAsInt());
        synchronized (samples) {
            trim(now);
            for (long[] sample : samples) {
                peak = Math.max(peak, (int) sample[1]);
            }
        }
        return peak;
    }

    /** Drops samples that have aged out. Callers hold the monitor. */
    private void trim(long now) {
        while (!samples.isEmpty() && now - samples.peekFirst()[0] > windowMillis) {
            samples.pollFirst();
        }
    }

    /** Forgets every sample. The next {@link #peak()} falls back to the live count. */
    public void clear() {
        synchronized (samples) {
            samples.clear();
        }
    }

    /** How many samples are currently held. Diagnostics only. */
    public int size() {
        synchronized (samples) {
            return samples.size();
        }
    }
}
