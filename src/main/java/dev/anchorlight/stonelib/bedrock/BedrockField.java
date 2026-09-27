package dev.anchorlight.stonelib.bedrock;

import java.util.List;

/**
 * One component of a Bedrock custom form. Text is plain, or legacy {@code §}-coded, because that is
 * all a Bedrock form renders.
 *
 * <p>Answers come back keyed by {@link #key()}: a {@code String} for {@link Input}, a
 * {@code Boolean} for {@link Toggle}, a {@code Float} for {@link Slider}, and the selected option's
 * id ({@code String}) for {@link Dropdown}. {@link Label} has no answer.
 */
public sealed interface BedrockField {

    /** The key the answer is returned under; null for a label. */
    String key();

    /** Static text. */
    record Label(String text) implements BedrockField {
        @Override
        public String key() {
            return null;
        }
    }

    record Input(String key, String label, String placeholder, String initial) implements BedrockField {
    }

    record Toggle(String key, String label, boolean initial) implements BedrockField {
    }

    record Slider(String key, String label, float min, float max, float step, float initial) implements BedrockField {
    }

    /**
     * @param ids     option ids, returned as the answer
     * @param labels  what the player sees, parallel to {@code ids}
     * @param initial index of the preselected option
     */
    record Dropdown(String key, String label, List<String> ids, List<String> labels, int initial)
            implements BedrockField {

        public Dropdown {
            if (ids.size() != labels.size() || ids.isEmpty()) {
                throw new IllegalArgumentException("Dropdown '" + key + "' needs matching, non-empty ids and labels");
            }
            ids = List.copyOf(ids);
            labels = List.copyOf(labels);
            initial = Math.max(0, Math.min(ids.size() - 1, initial));
        }
    }
}
