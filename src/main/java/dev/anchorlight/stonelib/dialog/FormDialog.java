package dev.anchorlight.stonelib.dialog;

import dev.anchorlight.stonelib.bedrock.BedrockField;
import dev.anchorlight.stonelib.bedrock.BedrockForms;
import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
 * A builder over Paper's Dialog API for the two shapes plugins actually use - a form with inputs,
 * and a prompt made of buttons - that also reaches Bedrock players.
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
 *
 * FormDialog.prompt(plugin, Component.text("Delete mailbox?"))
 *         .body(Component.text("This cannot be undone."))
 *         .button(Component.text("Delete"), player -&gt; mailbox.delete())
 *         .button(Component.text("Keep"), player -&gt; {})
 *         .show(player);
 * </pre>
 *
 * <h2>Bedrock players</h2>
 * A player connected through Geyser cannot see a Java dialog. When Floodgate is installed,
 * {@code show} sends them the equivalent native form instead, built from the same inputs and
 * buttons: a custom form for {@link #builder}, a two-button modal or a button list for
 * {@link #prompt}. Callbacks receive the same {@link Player} and {@link FormResponse} either way,
 * on the player's own thread, so calling code does not change. Without Floodgate nothing differs.
 * A Bedrock form has one submit button, so a form's cancel handler runs when the player closes it.
 * Pass {@link BedrockForms#none()} to {@code bedrock(...)} to opt a form out.
 *
 * <p>What the wrapper is for, beyond brevity:</p>
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

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private FormDialog() {
    }

    /** Starts building a form with inputs. */
    public static Builder builder(Plugin plugin, Component title) {
        return new Builder(plugin, title);
    }

    /** Starts building a prompt: body text and a row of buttons, no inputs. */
    public static Prompt prompt(Plugin plugin, Component title) {
        return new Prompt(plugin, title);
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

    /** Legacy {@code §} text, which is all a Bedrock form renders. */
    static String bedrockText(Component component) {
        return component == null ? "" : LEGACY.serialize(component);
    }

    /** The Bedrock equivalent of a form's body and inputs, read back from the built Paper inputs. */
    static List<BedrockField> bedrockFields(List<Component> bodyText, List<DialogInput> inputs) {
        List<BedrockField> fields = new ArrayList<>();
        for (Component text : bodyText) {
            fields.add(new BedrockField.Label(bedrockText(text)));
        }
        for (DialogInput input : inputs) {
            if (input instanceof TextDialogInput text) {
                fields.add(new BedrockField.Input(text.key(), bedrockText(text.label()), "", text.initial()));
            } else if (input instanceof BooleanDialogInput bool) {
                fields.add(new BedrockField.Toggle(bool.key(), bedrockText(bool.label()), bool.initial()));
            } else if (input instanceof NumberRangeDialogInput range) {
                float min = range.start();
                float max = range.end();
                float step = range.step() != null && range.step() > 0 ? range.step() : (max - min) / 100f;
                float initial = range.initial() != null ? range.initial() : min;
                fields.add(new BedrockField.Slider(range.key(), bedrockText(range.label()), min, max, step,
                        Math.max(min, Math.min(max, initial))));
            } else if (input instanceof SingleOptionDialogInput single) {
                List<String> ids = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                int initial = 0;
                for (SingleOptionDialogInput.OptionEntry entry : single.entries()) {
                    if (entry.initial()) {
                        initial = ids.size();
                    }
                    ids.add(entry.id());
                    labels.add(entry.display() == null ? entry.id() : bedrockText(entry.display()));
                }
                fields.add(new BedrockField.Dropdown(single.key(), bedrockText(single.label()), ids, labels, initial));
            }
        }
        return fields;
    }

    private static BedrockForms resolveBedrock(Plugin plugin, BedrockForms explicit) {
        return explicit != null ? explicit : BedrockForms.detect(plugin, new PlatformScheduler(plugin));
    }

    private static void guarded(Plugin plugin, Player player, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "Dialog callback failed for " + player.getName(), e);
        }
    }

    private static ActionButton javaButton(Plugin plugin, Component label, JavaCallback handler) {
        DialogActionCallback callback = (response, audience) -> {
            if (!(audience instanceof Player viewer)) {
                return;
            }
            guarded(plugin, viewer, () -> handler.accept(response, viewer));
        };
        DialogAction action = DialogAction.customClick(callback, ClickCallback.Options.builder().build());
        return ActionButton.builder(label).action(action).build();
    }

    @FunctionalInterface
    private interface JavaCallback {
        void accept(io.papermc.paper.dialog.DialogResponseView response, Player player);
    }

    /** Builds and shows one form. Not reusable: build a new one per showing. */
    public static final class Builder {

        private final Plugin plugin;
        private final Component title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<Component> bodyText = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private final Set<String> keys = new LinkedHashSet<>();

        private Component submitLabel = Component.text("Confirm");
        private Consumer<FormResponse> onSubmit = response -> {};
        private Component cancelLabel;
        private Consumer<Player> onCancel;
        private boolean canCloseWithEscape = true;
        private BedrockForms bedrock;

        private Builder(Plugin plugin, Component title) {
            this.plugin = plugin;
            this.title = title;
        }

        /** Adds a paragraph of static text above the inputs. */
        public Builder body(Component text) {
            body.add(DialogBody.plainMessage(text));
            bodyText.add(text);
            return this;
        }

        /** Adds a single-line text field. */
        public Builder text(String key, Component label, UnaryOperator<TextDialogInput.Builder> customiser) {
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
        public Builder slider(String key, Component label, float min, float max,
                              UnaryOperator<NumberRangeDialogInput.Builder> customiser) {
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
         * Which Bedrock forms to use. Defaults to detecting Floodgate; pass
         * {@link BedrockForms#none()} to always send the Java dialog.
         */
        public Builder bedrock(BedrockForms forms) {
            this.bedrock = forms;
            return this;
        }

        /**
         * Builds the dialog and shows it - or, to a Bedrock player, the equivalent Floodgate form.
         *
         * @return false if the dialog could not be shown, so the caller can fall back to telling the
         *         player rather than failing silently
         */
        public boolean show(Player player) {
            try {
                BedrockForms forms = resolveBedrock(plugin, bedrock);
                if (forms.isBedrock(player.getUniqueId())) {
                    return showBedrock(player, forms);
                }
            } catch (Throwable e) {
                plugin.getLogger().log(Level.WARNING, "Bedrock form failed for " + player.getName()
                        + "; sending the Java dialog instead", e);
            }
            return showJava(player);
        }

        private boolean showJava(Player player) {
            try {
                List<ActionButton> buttons = new ArrayList<>();
                buttons.add(javaButton(plugin, submitLabel,
                        (response, viewer) -> onSubmit.accept(new FormResponse(viewer, response))));
                if (cancelLabel != null) {
                    Consumer<Player> cancel = onCancel == null ? viewer -> {} : onCancel;
                    buttons.add(javaButton(plugin, cancelLabel, (response, viewer) -> cancel.accept(viewer)));
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

        private boolean showBedrock(Player player, BedrockForms forms) {
            Runnable closed = cancelLabel == null || onCancel == null
                    ? null
                    : () -> guarded(plugin, player, () -> onCancel.accept(player));
            return forms.sendCustom(player, bedrockText(title), bedrockFields(),
                    answers -> guarded(plugin, player,
                            () -> onSubmit.accept(FormResponse.fromBedrock(player, answers))),
                    closed);
        }

        List<BedrockField> bedrockFields() {
            return FormDialog.bedrockFields(bodyText, inputs);
        }

        private void claim(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Input keys must not be blank");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate input key: " + key);
            }
        }
    }

    /**
     * A prompt: body text and buttons, each running its own handler. Covers confirmations
     * (two buttons), notices (one) and choice menus (several).
     */
    public static final class Prompt {

        private record Choice(Component label, Consumer<Player> handler) {
        }

        private final Plugin plugin;
        private final Component title;
        private final List<Component> body = new ArrayList<>();
        private final List<Choice> buttons = new ArrayList<>();
        private Choice exit;
        private int columns = 1;
        private boolean canCloseWithEscape = true;
        private BedrockForms bedrock;

        private Prompt(Plugin plugin, Component title) {
            this.plugin = plugin;
            this.title = title;
        }

        public Prompt body(Component text) {
            body.add(text);
            return this;
        }

        public Prompt button(Component label, Consumer<Player> handler) {
            buttons.add(new Choice(label, handler == null ? player -> {} : handler));
            return this;
        }

        /**
         * A separate exit button, shown below the others on Java and last on Bedrock - "Back" or
         * "Cancel" on a menu of choices.
         */
        public Prompt exit(Component label, Consumer<Player> handler) {
            this.exit = new Choice(label, handler == null ? player -> {} : handler);
            return this;
        }

        /** How many buttons per row on Java. Bedrock always lays out one per row. Default 1. */
        public Prompt columns(int columns) {
            this.columns = Math.max(1, columns);
            return this;
        }

        public Prompt canCloseWithEscape(boolean canClose) {
            this.canCloseWithEscape = canClose;
            return this;
        }

        public Prompt bedrock(BedrockForms forms) {
            this.bedrock = forms;
            return this;
        }

        /** @return false if the prompt could not be shown */
        public boolean show(Player player) {
            if (buttons.isEmpty() && exit == null) {
                throw new IllegalStateException("A prompt needs at least one button");
            }
            try {
                BedrockForms forms = resolveBedrock(plugin, bedrock);
                if (forms.isBedrock(player.getUniqueId())) {
                    return showBedrock(player, forms);
                }
            } catch (Throwable e) {
                plugin.getLogger().log(Level.WARNING, "Bedrock form failed for " + player.getName()
                        + "; sending the Java dialog instead", e);
            }
            return showJava(player);
        }

        private boolean showJava(Player player) {
            try {
                List<ActionButton> actions = new ArrayList<>();
                for (Choice choice : buttons) {
                    actions.add(javaButton(plugin, choice.label(), (response, viewer) -> choice.handler().accept(viewer)));
                }
                List<DialogBody> dialogBody = body.stream().map(DialogBody::plainMessage).map(DialogBody.class::cast).toList();
                var type = DialogType.multiAction(actions).columns(columns);
                if (exit != null) {
                    type.exitAction(javaButton(plugin, exit.label(), (response, viewer) -> exit.handler().accept(viewer)));
                }
                Dialog dialog = Dialog.create(factory -> factory.empty()
                        .base(DialogBase.builder(title)
                                .body(dialogBody)
                                .canCloseWithEscape(canCloseWithEscape)
                                .build())
                        .type(type.build()));
                player.showDialog(dialog);
                return true;
            } catch (Throwable e) {
                plugin.getLogger().log(Level.WARNING, "Failed to show dialog for " + player.getName(), e);
                return false;
            }
        }

        private boolean showBedrock(Player player, BedrockForms forms) {
            List<Choice> all = new ArrayList<>(buttons);
            if (exit != null) {
                all.add(exit);
            }
            String content = String.join("\n", body.stream().map(FormDialog::bedrockText).toList());
            // Closing the form is Bedrock's Escape: run the exit button, as Java does.
            Runnable closed = exit == null ? null : () -> guarded(plugin, player, () -> exit.handler().accept(player));
            if (all.size() == 2) {
                return forms.sendModal(player, bedrockText(title), content,
                        bedrockText(all.get(0).label()), bedrockText(all.get(1).label()),
                        first -> guarded(plugin, player, () -> all.get(first ? 0 : 1).handler().accept(player)),
                        closed);
            }
            List<String> labels = all.stream().map(choice -> bedrockText(choice.label())).toList();
            return forms.sendButtons(player, bedrockText(title), content, labels, index -> {
                if (index >= 0 && index < all.size()) {
                    guarded(plugin, player, () -> all.get(index).handler().accept(player));
                }
            }, closed);
        }
    }
}
