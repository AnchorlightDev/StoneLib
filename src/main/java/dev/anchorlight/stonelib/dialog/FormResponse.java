package dev.anchorlight.stonelib.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * The values a player submitted from a {@link FormDialog}, read by the same keys the inputs were
 * declared with.
 *
 * <p>Every getter takes a fallback, because the client controls what comes back: a key can be
 * missing, and a number can arrive outside the range the slider was given. Nothing here trusts the
 * response.</p>
 *
 * <p>The same class serves Java and Bedrock players. A Bedrock player's answers arrive from a
 * Floodgate form rather than a Paper dialog, so {@link #raw()} is null for them; everything else
 * reads the same.</p>
 */
public final class FormResponse {

    /** Where the answers come from. */
    private interface Values {
        String text(String key);

        Boolean bool(String key);

        Float number(String key);
    }

    private final Player player;
    private final DialogResponseView view;
    private final Values values;

    FormResponse(Player player, DialogResponseView view) {
        this.player = player;
        this.view = view;
        this.values = new Values() {
            @Override
            public String text(String key) {
                return view.getText(key);
            }

            @Override
            public Boolean bool(String key) {
                return view.getBoolean(key);
            }

            @Override
            public Float number(String key) {
                return view.getFloat(key);
            }
        };
    }

    /** Answers from a Bedrock form: String, Boolean or Float by key. */
    private FormResponse(Player player, Map<String, Object> answers) {
        this.player = player;
        this.view = null;
        this.values = new Values() {
            @Override
            public String text(String key) {
                return answers.get(key) instanceof String value ? value : null;
            }

            @Override
            public Boolean bool(String key) {
                return answers.get(key) instanceof Boolean value ? value : null;
            }

            @Override
            public Float number(String key) {
                return answers.get(key) instanceof Number value ? value.floatValue() : null;
            }
        };
    }

    static FormResponse fromBedrock(Player player, Map<String, Object> answers) {
        return new FormResponse(player, answers == null ? Map.of() : answers);
    }

    /** The player who submitted the form. */
    public Player player() {
        return player;
    }

    /** True when the answers came from a Bedrock form rather than a Java dialog. */
    public boolean fromBedrock() {
        return view == null;
    }

    /**
     * A text field's value, or {@code fallback} if it is missing or blank.
     *
     * <p>This is raw player input. Run it through
     * {@link dev.anchorlight.stonelib.message.UntrustedText} before it reaches a MiniMessage
     * template or a display name.</p>
     */
    public String text(String key, String fallback) {
        String value = values.text(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** A toggle's value, or {@code fallback} if it is missing. */
    public boolean flag(String key, boolean fallback) {
        Boolean value = values.bool(key);
        return value == null ? fallback : value;
    }

    /**
     * A slider's value, clamped into {@code [min, max]} and falling back when it is missing or not
     * a number. Clamping here rather than at the call site is the point: a client is free to send
     * anything.
     */
    public float number(String key, float min, float max, float fallback) {
        Float value = values.number(key);
        if (value == null || value.isNaN()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** A dropdown's selected id, or {@code fallback} if nothing came back. */
    public String option(String key, String fallback) {
        return text(key, fallback);
    }

    /** The underlying Paper view, for anything this wrapper does not cover. Null for Bedrock. */
    public DialogResponseView raw() {
        return view;
    }
}
