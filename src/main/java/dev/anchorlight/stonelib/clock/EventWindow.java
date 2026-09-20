package dev.anchorlight.stonelib.clock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.logging.Logger;

/**
 * A resolved event window: two absolute instants plus the zone they are displayed in.
 *
 * <p>Separate from {@link EventClock} because parsing a window out of config is fiddly in a way
 * that is worth testing on its own, and because a plugin may want to resolve a window from
 * somewhere other than a config file.
 *
 * @param start when the event opens
 * @param end   when the event closes; always after {@code start}
 * @param zone  the zone timestamps are displayed in, and the zone a zone-less value is read in
 * @param valid false when the window had to be invented because the input could not be read.
 *              Surface this to staff: every phase boundary derived from an invented window is
 *              wrong, and nothing downstream can tell.
 */
public record EventWindow(Instant start, Instant end, ZoneId zone, boolean valid) {

    /**
     * Resolves a window from two raw config values.
     *
     * <p>The values are deliberately {@link Object} rather than {@link String}. An ISO-8601
     * timestamp is resolved by SnakeYAML into a {@link Date} whenever it appears unquoted, and a
     * config library that rewrites the file on merge will drop the quotes a bundled resource ships
     * with. {@code getString} would then hand back {@code Date.toString()}
     * ("Fri Oct 02 17:00:00 AEST 2026"), which is not ISO-8601 and would fail to parse. Read the
     * values with {@code config.get(path)} and pass them through here instead.
     *
     * @param startValue       raw {@code start} value: a {@link Date}, an offset ISO-8601 string,
     *                         or a zone-less local timestamp read in {@code zoneId}
     * @param endValue         raw {@code end} value, same shapes
     * @param zoneId           zone name for display and for zone-less values; blank means system default
     * @param fallbackDuration how long the invented window runs when {@code end} cannot be read
     * @param logger           where parse failures are reported
     */
    public static EventWindow parse(Object startValue, Object endValue, String zoneId,
                                    Duration fallbackDuration, Logger logger) {
        ZoneId zone = zone(zoneId, logger);
        Duration fallback = fallbackDuration == null || fallbackDuration.isZero()
                || fallbackDuration.isNegative() ? Duration.ofDays(3) : fallbackDuration;

        boolean[] valid = {true};
        Instant start = instant(startValue, zone, Instant.now(), "start", logger, valid);
        Instant end = instant(endValue, zone, start.plus(fallback), "end", logger, valid);

        if (!end.isAfter(start)) {
            logger.severe("Event end is not after start; falling back to start + " + fallback + ".");
            end = start.plus(fallback);
            valid[0] = false;
        }
        return new EventWindow(start, end, zone, valid[0]);
    }

    private static ZoneId zone(String configured, Logger logger) {
        if (configured == null || configured.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException ex) {
            logger.warning("Unknown timezone '" + configured + "', using system default.");
            return ZoneId.systemDefault();
        }
    }

    /**
     * Turns one configured value into an instant. Three accepted shapes, in order:
     *
     * <ol>
     *   <li>A {@link Date}, which is what YAML gives us for an unquoted ISO-8601 timestamp. The
     *       offset is already applied, so the instant is exact. Checked first because it is the
     *       usual path once a config library has rewritten the file.</li>
     *   <li>ISO-8601 with an offset ({@code 2026-10-02T18:00:00+11:00}) - the documented form, and
     *       the only one that is unambiguous.</li>
     *   <li>A zone-less local timestamp, interpreted in {@code zone}.</li>
     * </ol>
     */
    private static Instant instant(Object value, ZoneId zone, Instant fallback, String label,
                                   Logger logger, boolean[] valid) {
        if (isBlank(value)) {
            logger.severe("Event " + label + " is unset - using " + fallback
                    + ". Set it before the event opens.");
            valid[0] = false;
            return fallback;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        String trimmed = String.valueOf(value).trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
            // Not offset-qualified; try a local timestamp in the configured zone.
        }
        try {
            return LocalDateTime.parse(trimmed).atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            logger.severe("Could not parse event " + label + " '" + value
                    + "' (expected ISO-8601, e.g. 2026-10-02T18:00:00+11:00) - using " + fallback);
            valid[0] = false;
            return fallback;
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }

    /** The length of the window. Never zero or negative. */
    public Duration duration() {
        Duration total = Duration.between(start, end);
        return total.isZero() || total.isNegative() ? Duration.ofDays(3) : total;
    }
}
