package dev.anchorlight.StoneLib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Converts legacy '&'-coded message strings into MiniMessage-compatible input.
 */
public final class LegacyColorConverter {

    private LegacyColorConverter() {
    }

    /** Parses a legacy '&'-coded string and re-serializes it as plain text with MiniMessage tags stripped of legacy codes. */
    public static Component parseLegacy(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    /** Converts a legacy '&'-coded string directly to its plain-text rendering, discarding color/formatting. */
    public static String toPlainText(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(parseLegacy(legacy));
    }
}
