package dev.anchorlight.stonelib.message;

/**
 * Per-plugin settings for the {@code <icon:>} tag and {@link Sprites} fallback behaviour.
 *
 * <p>These live with the <em>calling plugin</em> rather than in StoneLib, deliberately. StoneLib
 * has no global state by design - every module is constructed directly - so there is no static
 * "sprites enabled" flag to flip, and no init call that has to happen before {@link Sprites} works.
 * A plugin that wants icons passes its own options in; a plugin that does not is unaffected.
 *
 * <p>{@code enabled} exists as a kill switch. If a client-compatibility problem ever turns up
 * mid-event, a plugin can set it to {@code false} in its own config and every sprite degrades to
 * the item's translatable name, with no StoneLib release and no call site changing. Every message
 * stays readable; it just stops being pictorial.
 *
 * @param enabled  false makes {@link Sprites#of} and {@code <icon:>} emit the text fallback instead
 *                 of a sprite. Defaults to true via {@link #defaults()}.
 * @param labelled true makes the {@code <icon:>} tag emit the sprite followed by the item's name,
 *                 as {@link Sprites#labelled} does, rather than the bare sprite.
 */
public record IconOptions(boolean enabled, boolean labelled) {

    /** Sprites on, bare sprite with no trailing name - the usual choice. */
    public static IconOptions defaults() {
        return new IconOptions(true, false);
    }

    /** Sprites on, each followed by the item's translated name. */
    public static IconOptions labelledDefaults() {
        return new IconOptions(true, true);
    }

    /** The kill switch: everything degrades to the item's translatable name. */
    public static IconOptions disabled() {
        return new IconOptions(false, false);
    }
}
