package dev.anchorlight.stonelib.map;

import java.util.Locale;
import java.util.Optional;

/** How a source image is fitted into a fixed-size canvas. */
public enum ImageFit {
    /** Preserve aspect ratio, fill the entire canvas, crop the overflow (around a focal point). */
    COVER,
    /** Preserve aspect ratio, show the whole image, letterbox the rest with a background colour. */
    CONTAIN,
    /** Fill the exact canvas, ignoring the source aspect ratio. */
    STRETCH;

    /** Case-insensitive lookup for config values; empty for anything unknown. */
    public static Optional<ImageFit> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
