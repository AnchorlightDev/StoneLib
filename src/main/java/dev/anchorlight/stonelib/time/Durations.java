package dev.anchorlight.stonelib.time;

import java.time.Duration;

/**
 * Duration formatting for player-facing text.
 *
 * <p>Three shapes, because they are not interchangeable and picking the wrong one is how you get
 * ambiguous output:
 *
 * <ul>
 *   <li>{@link #clock} - {@code "12:34"}. For a bossbar or a live countdown, where the digits are
 *       updating and the reader is watching them tick.</li>
 *   <li>{@link #human} - {@code "18 minutes"}, {@code "18m 30s"}. For prose - a quest description,
 *       a cooldown message. Never renders as {@code "18:30"}, which reads as a clock time.</li>
 *   <li>{@link #compact} - {@code "2h 5m"}. For a sidebar or a status line, where space is tight
 *       and only the two most significant units matter.</li>
 *   <li>{@link #full} - {@code "2h 5m 30s"}. For anywhere all three units are wanted at once,
 *       including the seconds that {@link #compact} and {@link #human} drop once an hour is in
 *       play. A countdown a player is timing a run against needs the seconds; a sidebar reading
 *       "2h 5m" for sixty seconds looks frozen.</li>
 * </ul>
 */
public final class Durations {

    private Durations() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** Clock-style countdown: {@code "12:34"}, or {@code "1:02:33"} once it passes an hour. */
    public static String clock(Duration duration) {
        long total = safeSeconds(duration);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    public static String clock(long totalSeconds) {
        return clock(Duration.ofSeconds(Math.max(0, totalSeconds)));
    }

    /**
     * Human-readable duration for prose: {@code "18 minutes"}, {@code "30 seconds"},
     * {@code "18m 30s"}, {@code "2h 5m"}. Pluralises whole units correctly.
     */
    public static String human(Duration duration) {
        long total = safeSeconds(duration);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        if (hours > 0) {
            return minutes == 0 && seconds == 0
                    ? hours + (hours == 1 ? " hour" : " hours")
                    : hours + "h " + minutes + "m";
        }
        if (minutes == 0) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        if (seconds == 0) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return minutes + "m " + seconds + "s";
    }

    public static String human(long totalSeconds) {
        return human(Duration.ofSeconds(Math.max(0, totalSeconds)));
    }

    /**
     * Compact two-unit form for sidebars and status lines: {@code "2h 5m"}, {@code "5m 30s"},
     * {@code "30s"}. Always short enough for a scoreboard line.
     */
    public static String compact(Duration duration) {
        long total = safeSeconds(duration);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static String compact(long totalSeconds) {
        return compact(Duration.ofSeconds(Math.max(0, totalSeconds)));
    }

    /**
     * All three units, largest first, skipping the leading ones that are zero:
     * {@code "2h 5m 30s"}, {@code "5m 30s"}, {@code "30s"}.
     *
     * <p>The difference from {@link #compact} is the seconds. Compact deliberately shows only the
     * two most significant units, so once a duration passes an hour the seconds disappear and the
     * value changes once a minute - correct where space is tight, wrong anywhere somebody is
     * watching it tick, where a display that sits still for sixty seconds reads as broken.
     *
     * <p>Interior zero units are kept: {@code "2h 0m 30s"} rather than {@code "2h 30s"}, so the
     * shape of the string does not change from one second to the next as a unit empties. A
     * countdown whose fields move around is hard to read at a glance.
     */
    public static String full(Duration duration) {
        long total = safeSeconds(duration);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public static String full(long totalSeconds) {
        return full(Duration.ofSeconds(Math.max(0, totalSeconds)));
    }

    /** Negative and null durations render as zero rather than as nonsense. */
    private static long safeSeconds(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0;
        }
        return duration.getSeconds();
    }

    /** Whole Minecraft ticks in a duration, for scheduler calls. */
    public static long toTicks(Duration duration) {
        return safeSeconds(duration) * 20L;
    }
}
