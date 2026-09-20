package dev.anchorlight.stonelib.display;

/**
 * Text progress bars for sidebars, chat lines and lore.
 *
 * <p>Returns a MiniMessage string rather than a {@code Component}, so the result can be dropped
 * into a message template and parsed along with everything around it.
 */
public final class Bars {

    private static final char DEFAULT_GLYPH = '|';

    private Bars() {
    }

    /** A bar in the default green-on-grey, e.g. {@code ||||||----}. */
    public static String progress(double current, double target, int width) {
        return progress(current, target, width, "green", "dark_gray", DEFAULT_GLYPH);
    }

    /** A bar in caller-chosen colours. Colours are any MiniMessage colour name or {@code #rrggbb}. */
    public static String progress(double current, double target, int width,
                                  String filledColour, String emptyColour) {
        return progress(current, target, width, filledColour, emptyColour, DEFAULT_GLYPH);
    }

    /**
     * A bar in caller-chosen colours and glyph.
     *
     * <p>A non-zero fraction always shows at least one filled glyph, and a fraction below 1.0
     * always leaves at least one empty glyph. Rounding alone would show an empty bar for real
     * progress and a full bar for a goal that is not finished, and on a community goal that reads
     * as the tracker being broken.
     */
    public static String progress(double current, double target, int width,
                                  String filledColour, String emptyColour, char glyph) {
        int size = Math.max(1, width);
        double safeTarget = target <= 0 ? 1 : target;
        double fraction = Math.clamp(current / safeTarget, 0.0, 1.0);

        int filled = (int) Math.round(fraction * size);
        if (fraction > 0.0 && filled == 0) {
            filled = 1;
        }
        if (fraction < 1.0 && filled == size) {
            filled = size - 1;
        }
        return "<" + filledColour + ">" + String.valueOf(glyph).repeat(filled) + "</" + filledColour + ">"
                + "<" + emptyColour + ">" + String.valueOf(glyph).repeat(size - filled) + "</" + emptyColour + ">";
    }

    /** The fraction a bar would show, clamped to [0, 1]. Handy for bossbar progress. */
    public static float fraction(double current, double target) {
        double safeTarget = target <= 0 ? 1 : target;
        return (float) Math.clamp(current / safeTarget, 0.0, 1.0);
    }

    /** A whole-number percentage, clamped to [0, 100]. */
    public static int percent(double current, double target) {
        return Math.round(fraction(current, target) * 100f);
    }
}
