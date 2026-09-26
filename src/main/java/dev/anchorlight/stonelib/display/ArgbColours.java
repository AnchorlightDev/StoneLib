package dev.anchorlight.stonelib.display;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and formats ARGB colours typed by admins: {@code #RRGGBB}, {@code #AARRGGBB} (the
 * {@code #} is optional) or a Minecraft dye name such as {@code light_blue}. Colours without an
 * alpha component get {@link #DEFAULT_ALPHA}, which suits translucent displays.
 *
 * <p>Plain ints rather than {@code org.bukkit.Color}, so values can be parsed and stored without a
 * running server; convert with {@code Color.fromARGB(argb)} when rendering.
 */
public final class ArgbColours {

    /** Alpha applied when a colour is given without one: half transparent. */
    public static final int DEFAULT_ALPHA = 0x80;

    private static final Pattern HEX = Pattern.compile("[0-9a-f]{6}|[0-9a-f]{8}");
    private static final Map<String, Integer> DYE_COLOURS;

    static {
        Map<String, Integer> dyes = new LinkedHashMap<>();
        dyes.put("white", 0xF9FFFE);
        dyes.put("orange", 0xF9801D);
        dyes.put("magenta", 0xC74EBD);
        dyes.put("light_blue", 0x3AB3DA);
        dyes.put("yellow", 0xFED83D);
        dyes.put("lime", 0x80C71F);
        dyes.put("pink", 0xF38BAA);
        dyes.put("gray", 0x474F52);
        dyes.put("light_gray", 0x9D9D97);
        dyes.put("cyan", 0x169C9C);
        dyes.put("purple", 0x8932B8);
        dyes.put("blue", 0x3C44AA);
        dyes.put("brown", 0x835432);
        dyes.put("green", 0x5E7C16);
        dyes.put("red", 0xB02E26);
        dyes.put("black", 0x1D1D21);
        DYE_COLOURS = Collections.unmodifiableMap(dyes);
    }

    private ArgbColours() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** Dye names accepted by {@link #parse}, in dye order, for tab completion. */
    public static Set<String> names() {
        return DYE_COLOURS.keySet();
    }

    public static Optional<Integer> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        String name = value.replace('-', '_').replace(' ', '_').replace("grey", "gray");
        Integer dye = DYE_COLOURS.get(name);
        if (dye != null) {
            return Optional.of(withDefaultAlpha(dye));
        }

        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (!HEX.matcher(hex).matches()) {
            return Optional.empty();
        }
        long parsed = Long.parseLong(hex, 16);
        return Optional.of(hex.length() == 6 ? withDefaultAlpha((int) parsed) : (int) parsed);
    }

    /** Formats as {@code #AARRGGBB}, which {@link #parse} reads back unchanged. */
    public static String format(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    public static int withDefaultAlpha(int rgb) {
        return (DEFAULT_ALPHA << 24) | (rgb & 0xFFFFFF);
    }
}
