package dev.anchorlight.stonelib.clock;

import dev.anchorlight.stonelib.time.Durations;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * A timed event reduced to two absolute timestamps.
 *
 * <p>Everything downstream - difficulty ramps, shop unlocks, pacing, countdowns - derives from
 * normalised progress {@code p} in [0, 1], or from an absolute duration before {@code end}. That
 * split is the whole point: <em>fractions stretch with the event, durations before the end do
 * not</em>. A grace period written as "the first third" is one day of a three-day run and one week
 * of a three-week one, while a closing countdown written as "the last hour" stays one hour however
 * long the event runs.
 *
 * <p>Nothing here is persisted. The clock is recomputed from its window on every load, so a restart
 * cannot shift the event, and the same configuration drives a weekend and a year.
 *
 * <p>The clock deliberately knows nothing about <em>named</em> phases. Phase names and their
 * thresholds are where event-specific assumptions live, so they belong to the plugin: call
 * {@link #atProgress(double)} and {@link #withinOfEnd(Duration)} from your own phase enum.
 *
 * <p>Not thread-safe for mutation. Build it and {@link #apply} windows on the main thread; reads
 * are safe from anywhere.
 */
public final class EventClock {

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm z");

    /** The length that {@link #intervalScale()} measures a run against. */
    private final Duration reference;
    private final Logger logger;

    private Instant start;
    private Instant end;
    private ZoneId displayZone;
    private boolean scheduleValid;

    /** Staff testing override. Session only - never written to disk, cleared by {@link #apply}. */
    private Double simulatedProgress;

    /** A clock measured against a three-day reference run. */
    public EventClock(Logger logger) {
        this(Duration.ofDays(3), logger);
    }

    /**
     * @param reference the run length {@link #intervalScale()} treats as 1.0. Content pacing tuned
     *                  for this length is stretched proportionally for longer runs.
     */
    public EventClock(Duration reference, Logger logger) {
        this.reference = reference == null || reference.isZero() || reference.isNegative()
                ? Duration.ofDays(3) : reference;
        this.logger = logger;
        this.start = Instant.now();
        this.end = this.start.plus(this.reference);
        this.displayZone = ZoneId.systemDefault();
        this.scheduleValid = false;
    }

    /**
     * Adopts a window. Call this on load and on every reload.
     *
     * <p>Clears any active simulation: a forced progress value exists to be forgotten, and leaving
     * one in place across a reload would silently misreport the event.
     */
    public void apply(EventWindow window) {
        this.start = window.start();
        this.end = window.end();
        this.displayZone = window.zone();
        this.scheduleValid = window.valid();
        this.simulatedProgress = null;

        if (!scheduleValid) {
            // Worth shouting about: an invented window means every boundary derived from it fires
            // at the wrong time, and nothing downstream can tell.
            logger.severe("================================================================");
            logger.severe("EVENT SCHEDULE NOT CONFIGURED. The window has been made up");
            logger.severe("(" + start + " -> " + end + ") and every boundary derived from");
            logger.severe("it is wrong. Fix the configured start and end, then reload.");
            logger.severe("================================================================");
        }
    }

    // ------------------------------------------------------------- timestamps

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    public ZoneId displayZone() {
        return displayZone;
    }

    /** Whether the window came from configuration rather than from a fallback. */
    public boolean scheduleValid() {
        return scheduleValid;
    }

    /** The configured length of the event. Never zero. */
    public Duration duration() {
        Duration total = Duration.between(start, end);
        return total.isZero() || total.isNegative() ? reference : total;
    }

    public String formatted(Instant instant) {
        return instant == null ? "unset" : DISPLAY.format(ZonedDateTime.ofInstant(instant, displayZone));
    }

    // --------------------------------------------------------------- progress

    /**
     * Normalised progress through the event, clamped to [0, 1].
     *
     * <p>Honours the simulation override, so every phase check in a plugin can be driven from one
     * place during testing rather than each system needing its own test hook.
     */
    public double progress() {
        if (simulatedProgress != null) {
            return simulatedProgress;
        }
        double elapsed = Duration.between(start, Instant.now()).toMillis();
        double total = duration().toMillis();
        return Math.clamp(elapsed / total, 0.0, 1.0);
    }

    /** True when {@code p} has reached the given fraction. */
    public boolean atProgress(double fraction) {
        return progress() >= fraction;
    }

    /** True when the event is live and within {@code window} of the end. */
    public boolean withinOfEnd(Duration window) {
        return window != null && live() && remaining().compareTo(window) <= 0;
    }

    public boolean simulating() {
        return simulatedProgress != null;
    }

    public Double simulatedProgress() {
        return simulatedProgress;
    }

    /**
     * Forces {@code p} for this session only. Deliberately loud: a server left in this state would
     * behave nothing like its schedule says it should.
     *
     * @param fraction forced progress, clamped to [0, 1], or null to return to the real clock
     */
    public void simulate(Double fraction) {
        if (fraction == null) {
            simulatedProgress = null;
            logger.warning("SCHEDULE SIMULATION CLEARED - progress is back on the real clock.");
            return;
        }
        simulatedProgress = Math.clamp(fraction, 0.0, 1.0);
        logger.warning("SCHEDULE SIMULATION ACTIVE - progress forced to " + simulatedProgress
                + ". This is session-only and is NOT persisted. Restart or reload to clear it.");
    }

    // ------------------------------------------------------------------ state

    /** True once the event window has opened. Respects the simulation override. */
    public boolean started() {
        return simulatedProgress != null || !Instant.now().isBefore(start);
    }

    /**
     * True once the event window has closed.
     *
     * <p>Consults the real clock even under simulation, so a simulation can rehearse the ending but
     * can never actually end the event.
     */
    public boolean finished() {
        if (simulatedProgress != null) {
            return simulatedProgress >= 1.0 && Instant.now().isAfter(end);
        }
        return Instant.now().isAfter(end);
    }

    /** Before the start: the world is held in its pre-event state. */
    public boolean preEvent() {
        return !started();
    }

    /** True when event systems may run at all. */
    public boolean live() {
        return started() && !finished();
    }

    /**
     * Time left until {@code end}.
     *
     * <p>Under a simulation this is derived from the forced {@code p} rather than the wall clock,
     * so duration-based gates near the end can actually be tested.
     */
    public Duration remaining() {
        if (simulatedProgress != null) {
            long millis = Math.round(duration().toMillis() * (1.0 - simulatedProgress));
            return Duration.ofMillis(Math.max(0, millis));
        }
        Duration remaining = Duration.between(Instant.now(), end);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public String remainingShort() {
        return Durations.compact(remaining());
    }

    public Duration elapsed() {
        if (simulatedProgress != null) {
            return Duration.ofMillis(Math.round(duration().toMillis() * simulatedProgress));
        }
        Duration elapsed = Duration.between(start, Instant.now());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    /**
     * 1-based day, computed from elapsed time rather than stored.
     *
     * <p>For display only. Nothing should branch on a day number: the same config may drive a
     * weekend and a month, and "day 4" means nothing without {@link #totalDays()} beside it.
     */
    public int day() {
        return (int) (elapsed().toDays() + 1);
    }

    /** Total days the event runs for, rounded up, for "day 2 of 7" style display. */
    public int totalDays() {
        return (int) Math.max(1,
                Math.ceil(duration().toMillis() / (double) Duration.ofDays(1).toMillis()));
    }

    /**
     * Multiplier to apply to configured content intervals.
     *
     * <p>Frequencies tuned for the reference run feel relentless over a much longer one, so
     * intervals stretch with the run. Never below 1.0, so a short event is never made quieter than
     * it was configured to be.
     */
    public double intervalScale() {
        double scale = duration().toMillis() / (double) reference.toMillis();
        return Math.max(1.0, scale);
    }

    /** How long until progress reaches a fraction, on the real clock. Never negative. */
    public Duration untilProgress(double fraction) {
        long targetMillis = start.toEpochMilli()
                + Math.round(duration().toMillis() * Math.clamp(fraction, 0.0, 1.0));
        Duration until = Duration.between(Instant.now(), Instant.ofEpochMilli(targetMillis));
        return until.isNegative() ? Duration.ZERO : until;
    }

    /** {@code p} formatted for logs and staff output. */
    public String progressLabel() {
        return String.format(Locale.ROOT, "%.3f", progress());
    }
}
