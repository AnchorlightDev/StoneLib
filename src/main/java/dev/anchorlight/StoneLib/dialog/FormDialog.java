package dev.anchorlight.StoneLib.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

/**
 * A builder over Paper's Dialog API for the common case: a form with some inputs, a submit button
 * and a cancel button.
 *
 * <pre>
 * FormDialog.builder(plugin, Component.text("Edit pet"))
 *         .body(Component.text("Give your pet a name."))
 *         .text("name", Component.text("Name"), field -&gt; field.initial(current).maxLength(32))
 *         .slider("scale", Component.text("Size"), 0.5f, 2.0f, field -&gt; field.initial(1.0f).step(0.1f))
 *         .toggle("visible", Component.text("Visible to others"), true)
 *         .onSubmit(Component.text("Save"), response -&gt; {
 *             String name = response.text("name", current);
 *             float scale = response.number("scale", 0.5f, 2.0f, 1.0f);
 *         })
 *         .onCancel(Component.text("Cancel"), player -&gt; {})
 *         .show(player);
 * </pre>
 *
 * <p>What the wrapper is actually for, beyond brevity:</p>
 *
 * <ul>
 *   <li>Callbacks are always handed a {@link Player}, never a bare audience, and a callback that
 *       throws is logged rather than escaping into Paper.</li>
 *   <li>Values come back through {@link FormResponse}, which clamps and defaults rather than
 *       trusting what the client sent.</li>
 *   <li>Duplicate input keys fail at build time, where they are obvious, instead of silently
 *       shadowing each other in the response.</li>
 * </ul>
 *
 * <p>Paper marks the Dialog API experimental, so callbacks here catch {@link Throwable}: a class
 * that moves between versions surfaces as {@code NoClassDefFoundError}, which must not escape a
 * click handler.</p>
 */
public final class FormDialog {

    private FormDialog() {
    }

    /** Starts building a form. */
    public static Builder builder(Plugin plugin, Component title) {
        return new Builder(plugin, title);
    }

    /** Whether this server actually has the Dialog API. Check once on enable. */
    public static boolean available() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Builds and shows one form. Not reusable: build a new one per showing. */
    public static final class Builder {

        private final Plugin plugin;
        private final Component title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private final Set<String> keys = new LinkedHashSet<>();

        private Component submitLabel = Component.text("Confirm");
        private Consumer<FormResponse> onSubmit = response -> {};
        private Component cancelLabel;
        private Consumer<Player> onCancel;
        private boolean canCloseWithEscape = true;

        private Builder(Plugin plugin, Component title) {
            this.plugin = plugin;
            this.title = title;
        }

        /** Adds a paragraph of static text above the inputs. */
        public Builder body(Component text) {
            body.add(DialogBody.plainMessage(text));
            return this;
        }

        /** Adds a single-line text field. */
        public Builder text(String key, Component label, UnaryOperator<io.papermc.paper.registry.data.dialog.input.TextDialogInput.Builder> customiser) {
            claim(key);
            var field = DialogInput.text(key, label);
            inputs.add(customiser.apply(field).build());
            return this;
        }

        /** Adds a text field with no further customisation. */
        public Builder text(String key, Component label, String initial) {
            return text(key, label, field -> field.initial(initial));
        }

        /**
         * Adds a numeric slider.
         *
         * <p>The range is the range the client is offered, not a guarantee about what comes back —
         * read the value with {@link FormResponse#number}, which clamps to the same bounds.</p>
         */
        public Builder slider(String key, Component label, float min, float max, UnaryOperator<io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput.Builder> customiser) {
            claim(key);
            if (min >= max) {
                throw new IllegalArgumentException("Slider '" + key + "' needs min < max, got " + min + " and " + max);
            }
            var field = DialogInput.numberRange(key, label, min, max);
            inputs.add(customiser.apply(field).build());
            return this;
        }

        /** Adds a checkbox. */
        public Builder toggle(String key, Component label, boolean initial) {
            claim(key);
            inputs.add(DialogInput.bool(key, label).initial(initial).build());
            return this;
        }

        /**
         * Adds a dropdown.
         *
         * @param options id-to-label pairs; mark one initial with
         *                {@link SingleOptionDialogInput.OptionEntry#create(String, Component, boolean)}
         */
        public Builder dropdown(String key, Component label, List<SingleOptionDialogInput.OptionEntry> options) {
            claim(key);
            if (options.isEmpty()) {
                throw new IllegalArgumentException("Dropdown '" + key + "' needs at least one option");
            }
            inputs.add(DialogInput.singleOption(key, label, options).build());
            return this;
        }

        /** Convenience for one dropdown option. */
        public static SingleOptionDialogInput.OptionEntry option(String id, Component display, boolean initial) {
            return SingleOptionDialogInput.OptionEntry.create(id, display, initial);
        }

        /** Sets the submit button's label and what happens when it is pressed. */
        public Builder onSubmit(Component label, Consumer<FormResponse> handler) {
            this.submitLabel = label;
            this.onSubmit = handler;
            return this;
        }

        /** Adds a cancel button. Without one the form has only a submit button. */
        public Builder onCancel(Component label, Consumer<Player> handler) {
            this.cancelLabel = label;
            this.onCancel = handler;
            return this;
        }

        /** Whether escape closes the form without submitting. Default true. */
        public Builder canCloseWithEscape(boolean canClose) {
            this.canCloseWithEscape = canClose;
            return this;
        }

        /**
         * Builds the dialog and shows it.
         *
         * @return false if the Dialog API is missing or the dialog could not be shown, so the
         *         caller can fall back to telling the player rather than failing silently
         */
        public boolean show(Player player) {
            try {
                List<ActionButton> buttons = new ArrayList<>();
                buttons.add(button(submitLabel, (response, viewer) -> onSubmit.accept(new FormResponse(viewer, response))));
                if (cancelLabel != null) {
                    Consumer<Player> cancel = onCancel == null ? viewer -> {} : onCancel;
                    buttons.add(button(cancelLabel, (response, viewer) -> cancel.accept(viewer)));
                }

                Dialog dialog = Dialog.create(factory -> factory.empty()
                        .base(DialogBase.builder(title)
                                .body(List.copyOf(body))
                                .inputs(List.copyOf(inputs))
                                .canCloseWithEscape(canCloseWithEscape)
                                .build())
                        .type(DialogType.multiAction(buttons).build()));
                player.showDialog(dialog);
                return true;
            } catch (Throwable e) {
                plugin.getLogger().log(Level.WARNING, "Failed to show dialog for " + player.getName(), e);
                return false;
            }
        }

        private ActionButton button(Component label, FormCallback handler) {
            DialogActionCallback callback = (response, audience) -> {
                if (!(audience instanceof Player viewer)) {
                    return;
                }
                try {
                    handler.accept(response, viewer);
                } catch (Throwable e) {
                    plugin.getLogger().log(Level.WARNING, "Dialog callback failed for " + viewer.getName(), e);
                }
            };
            DialogAction action = DialogAction.customClick(callback, ClickCallback.Options.builder().build());
            return ActionButton.builder(label).action(action).build();
        }

        private void claim(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Input keys must not be blank");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate input key: " + key);
            }
        }

        @FunctionalInterface
        private interface FormCallback {
            void accept(io.papermc.paper.dialog.DialogResponseView response, Player player);
        }
    }
}
