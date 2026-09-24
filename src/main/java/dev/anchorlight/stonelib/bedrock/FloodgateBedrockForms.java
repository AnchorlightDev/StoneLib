package dev.anchorlight.stonelib.bedrock;

import dev.anchorlight.stonelib.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The Floodgate implementation of {@link BedrockForms}. Only ever loaded reflectively by
 * {@link BedrockForms#detect}, after Floodgate is known to be enabled - do not reference it
 * directly, or a server without Floodgate fails to load the referencing class.
 */
final class FloodgateBedrockForms implements BedrockForms {

    private final FloodgateApi api;
    private final PlatformScheduler scheduler;

    /** Public so the reflective lookup in {@link BedrockForms#detect} can find it. */
    public FloodgateBedrockForms(PlatformScheduler scheduler) {
        this.api = FloodgateApi.getInstance();
        this.scheduler = scheduler;
        if (api == null) {
            throw new IllegalStateException("FloodgateApi.getInstance() returned null");
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean isBedrock(UUID player) {
        return player != null && api.isFloodgatePlayer(player);
    }

    @Override
    public boolean sendModal(Player player, String title, String content, String first, String second,
                             Consumer<Boolean> onChoice) {
        UUID id = player.getUniqueId();
        ModalForm form = ModalForm.builder()
                .title(title)
                .content(content)
                .button1(first)
                .button2(second)
                .validResultHandler(response -> deliver(id, () -> onChoice.accept(response.clickedFirst())))
                .build();
        return api.sendForm(id, form);
    }

    @Override
    public boolean sendButtons(Player player, String title, String content, List<String> buttons,
                               IntConsumer onChoice) {
        UUID id = player.getUniqueId();
        SimpleForm.Builder builder = SimpleForm.builder().title(title).content(content);
        for (String button : buttons) {
            builder.button(button);
        }
        builder.validResultHandler(response -> deliver(id, () -> onChoice.accept(response.clickedButtonId())));
        return api.sendForm(id, builder.build());
    }

    @Override
    public boolean sendCustom(Player player, String title, List<BedrockField> fields,
                              Consumer<Map<String, Object>> onSubmit, Runnable onClosed) {
        UUID id = player.getUniqueId();
        CustomForm.Builder builder = CustomForm.builder().title(title);
        for (BedrockField field : fields) {
            switch (field) {
                case BedrockField.Label label -> builder.label(label.text());
                case BedrockField.Input input -> builder.input(input.label(),
                        input.placeholder() == null ? "" : input.placeholder(),
                        input.initial() == null ? "" : input.initial());
                case BedrockField.Toggle toggle -> builder.toggle(toggle.label(), toggle.initial());
                case BedrockField.Slider slider -> builder.slider(slider.label(), slider.min(), slider.max(),
                        slider.step(), slider.initial());
                case BedrockField.Dropdown dropdown -> builder.dropdown(dropdown.label(), dropdown.labels(),
                        dropdown.initial());
            }
        }
        builder.validResultHandler(response -> {
            // Answers are read by component index, which counts labels too.
            Map<String, Object> answers = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                BedrockField field = fields.get(i);
                try {
                    switch (field) {
                        case BedrockField.Label ignored -> { }
                        case BedrockField.Input input -> answers.put(input.key(), response.asInput(i));
                        case BedrockField.Toggle toggle -> answers.put(toggle.key(), response.asToggle(i));
                        case BedrockField.Slider slider -> answers.put(slider.key(), response.asSlider(i));
                        case BedrockField.Dropdown dropdown -> {
                            int chosen = response.asDropdown(i);
                            if (chosen >= 0 && chosen < dropdown.ids().size()) {
                                answers.put(dropdown.key(), dropdown.ids().get(chosen));
                            }
                        }
                    }
                } catch (RuntimeException malformed) {
                    // A missing or mistyped answer is left out; FormResponse falls back for it.
                }
            }
            deliver(id, () -> onSubmit.accept(answers));
        });
        if (onClosed != null) {
            builder.closedOrInvalidResultHandler(() -> deliver(id, onClosed));
        }
        return api.sendForm(id, builder.build());
    }

    /** Hops from Floodgate's network thread to the player's own thread. */
    private void deliver(UUID id, Runnable callback) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            scheduler.entity(online, callback);
        }
    }
}
