package dev.anchorlight.StoneLib.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.entity.Player;

/**
 * The values a player submitted from a {@link FormDialog}, read by the same keys the inputs were
 * declared with.
 *
 * <p>Every getter takes a fallback, because the client controls what comes back: a key can be
 * missing, and a number can arrive outside the range the slider was given. Nothing here trusts the
 * response.</p>
 */
public final class FormResponse {

    private final Player player;
    private final DialogResponseView view;

    FormResponse(Player player, DialogResponseView view) {
        this.player = player;
        this.view = view;
    }

    /** The player who submitted the form. */
    public Player player() {
        return player;
    }

    /**
     * A text field's value, or {@code fallback} if it is missing or blank.
     *
     * <p>This is raw player input. Run it through
     * {@link dev.anchorlight.StoneLib.message.UntrustedText} before it reaches a MiniMessage
     * template or a display name.</p>
     */
    public String text(String key, String fallback) {
        String value = view.getText(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** A toggle's value, or {@code fallback} if it is missing. */
    public boolean flag(String key, boolean fallback) {
        Boolean value = view.getBoolean(key);
        return value == null ? fallback : value;
    }

    /**
     * A slider's value, clamped into {@code [min, max]} and falling back when it is missing or not
     * a number. Clamping here rather than at the call site is the point: a client is free to send
     * anything.
     */
    public float number(String key, float min, float max, float fallback) {
        Float value = view.getFloat(key);
        if (value == null || value.isNaN()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** A dropdown's selected id, or {@code fallback} if nothing came back. */
    public String option(String key, String fallback) {
        return text(key, fallback);
    }

    /** The underlying Paper view, for anything this wrapper does not cover. */
    public DialogResponseView raw() {
        return view;
    }
}
