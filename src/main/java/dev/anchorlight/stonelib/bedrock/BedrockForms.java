package dev.anchorlight.stonelib.bedrock;

import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * Native Bedrock forms for players joining through Geyser, when Floodgate is installed.
 *
 * <p>Clickable chat does nothing on a Bedrock client, so a feature that offers "[Accept] [Deny]" in
 * chat needs a second route for those players. This interface is that route, and it is safe to
 * depend on whether or not Floodgate is present: {@link #detect} returns a Floodgate-backed
 * implementation when the plugin is enabled, and {@link #none()} otherwise, whose
 * {@link #isBedrock} is always false so callers fall straight through to chat.
 *
 * <p>Nothing in this interface names a Floodgate or Cumulus type. The implementation that does is
 * loaded reflectively, and only once Floodgate is known to be enabled, so a server without it never
 * resolves those classes. Declare {@code softdepend: [floodgate]} so Floodgate enables first.
 *
 * <p>Form responses arrive on a network thread. Callbacks here are always delivered on the
 * player's own thread through {@link PlatformScheduler}, so they may touch the player and run
 * commands directly, on Paper and on Folia. A form the player closes without choosing invokes no
 * callback.
 *
 * <pre>{@code
 * BedrockForms forms = BedrockForms.detect(this, scheduler);
 * if (forms.isBedrock(player.getUniqueId())) {
 *     forms.sendModal(player, "Duel request", name + " challenged you", "Accept", "Deny",
 *             accepted -> player.performCommand(accepted ? "duel accept" : "duel deny"));
 * } else {
 *     player.sendMessage(chatButtons);
 * }
 * }</pre>
 */
public interface BedrockForms {

    /** True when forms can actually be sent. */
    boolean available();

    /** True when {@code player} is connected through Geyser. Always false without Floodgate. */
    boolean isBedrock(UUID player);

    /**
     * A two-button form.
     *
     * @param onChoice receives true for the first button, false for the second
     * @return true when the form was sent
     */
    boolean sendModal(Player player, String title, String content, String first, String second,
                      Consumer<Boolean> onChoice);

    /**
     * A list of buttons.
     *
     * @param onChoice receives the index of the button pressed
     * @return true when the form was sent
     */
    boolean sendButtons(Player player, String title, String content, List<String> buttons, IntConsumer onChoice);

    /**
     * A form of labels, text inputs, toggles, sliders and dropdowns with a single submit - the
     * Bedrock counterpart of a Java dialog. {@code FormDialog} builds one of these automatically.
     *
     * @param onSubmit receives each field's answer by key; see {@link BedrockField} for the types
     * @param onClosed run when the player closes the form without submitting, or null
     * @return true when the form was sent
     */
    boolean sendCustom(Player player, String title, List<BedrockField> fields,
                       Consumer<Map<String, Object>> onSubmit, Runnable onClosed);

    /** An implementation for servers without Floodgate: nobody is a Bedrock player. */
    static BedrockForms none() {
        return NoBedrockForms.INSTANCE;
    }

    /**
     * Floodgate-backed forms when the Floodgate plugin is enabled, {@link #none()} otherwise. Call
     * from {@code onEnable}, after Floodgate has had the chance to enable.
     */
    static BedrockForms detect(Plugin plugin, PlatformScheduler scheduler) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return none();
        }
        try {
            Class<?> type = Class.forName("dev.anchorlight.stonelib.bedrock.FloodgateBedrockForms");
            return (BedrockForms) type.getConstructor(PlatformScheduler.class).newInstance(scheduler);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Floodgate is installed but its API could not be used; Bedrock players will get chat instead", ex);
            return none();
        }
    }

    /** The no-Floodgate implementation. */
    final class NoBedrockForms implements BedrockForms {

        static final NoBedrockForms INSTANCE = new NoBedrockForms();

        private NoBedrockForms() {
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isBedrock(UUID player) {
            return false;
        }

        @Override
        public boolean sendModal(Player player, String title, String content, String first, String second,
                                 Consumer<Boolean> onChoice) {
            return false;
        }

        @Override
        public boolean sendButtons(Player player, String title, String content, List<String> buttons,
                                   IntConsumer onChoice) {
            return false;
        }

        @Override
        public boolean sendCustom(Player player, String title, List<BedrockField> fields,
                                  Consumer<Map<String, Object>> onSubmit, Runnable onClosed) {
            return false;
        }
    }
}
