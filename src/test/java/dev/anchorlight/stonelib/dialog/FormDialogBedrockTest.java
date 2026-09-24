package dev.anchorlight.stonelib.dialog;

import dev.anchorlight.stonelib.bedrock.BedrockField;
import dev.anchorlight.stonelib.bedrock.BedrockForms;
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bedrock players get a native form built from the same dialog definition, and its answers reach
 * the same handlers.
 */
class FormDialogBedrockTest {

    /** Records what was sent and lets the test answer it. */
    static final class FakeForms implements BedrockForms {
        final List<String> sent = new ArrayList<>();
        List<BedrockField> fields;
        Consumer<Map<String, Object>> submit;
        Runnable closed;
        Consumer<Boolean> modal;
        IntConsumer buttons;
        List<String> buttonLabels;

        @Override public boolean available() { return true; }
        @Override public boolean isBedrock(UUID player) { return true; }

        @Override
        public boolean sendModal(Player p, String title, String content, String first, String second, Consumer<Boolean> onChoice) {
            sent.add("modal:" + title + ":" + first + "/" + second);
            modal = onChoice;
            return true;
        }

        @Override
        public boolean sendButtons(Player p, String title, String content, List<String> labels, IntConsumer onChoice) {
            sent.add("buttons:" + title);
            buttonLabels = labels;
            buttons = onChoice;
            return true;
        }

        @Override
        public boolean sendCustom(Player p, String title, List<BedrockField> f, Consumer<Map<String, Object>> onSubmit, Runnable onClosed) {
            sent.add("custom:" + title);
            fields = f;
            submit = onSubmit;
            closed = onClosed;
            return true;
        }
    }

    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock player;
    private FakeForms forms;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        player = server.addPlayer();
        forms = new FakeForms();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aTwoButtonPromptBecomesAModal() {
        List<String> clicked = new ArrayList<>();
        assertTrue(FormDialog.prompt(plugin, Component.text("Delete?"))
                .body(Component.text("Sure?"))
                .button(Component.text("Yes"), p -> clicked.add("yes"))
                .button(Component.text("No"), p -> clicked.add("no"))
                .bedrock(forms)
                .show(player));
        assertEquals(List.of("modal:Delete?:Yes/No"), forms.sent);
        forms.modal.accept(false);
        assertEquals(List.of("no"), clicked);
    }

    @Test
    void aChoiceMenuBecomesAButtonListWithTheExitLast() {
        List<String> clicked = new ArrayList<>();
        FormDialog.prompt(plugin, Component.text("Pick"))
                .button(Component.text("A"), p -> clicked.add("a"))
                .button(Component.text("B"), p -> clicked.add("b"))
                .button(Component.text("C"), p -> clicked.add("c"))
                .exit(Component.text("Back"), p -> clicked.add("back"))
                .bedrock(forms)
                .show(player);
        assertEquals(List.of("A", "B", "C", "Back"), forms.buttonLabels);
        forms.buttons.accept(3);
        forms.buttons.accept(1);
        forms.buttons.accept(99);
        assertEquals(List.of("back", "b"), clicked);
    }

    @Test
    void paperInputsTranslateToBedrockFields() {
        TextDialogInput text = mock(TextDialogInput.class);
        when(text.key()).thenReturn("name");
        when(text.label()).thenReturn(Component.text("Name"));
        when(text.initial()).thenReturn("Rex");
        BooleanDialogInput toggle = mock(BooleanDialogInput.class);
        when(toggle.key()).thenReturn("visible");
        when(toggle.label()).thenReturn(Component.text("Visible"));
        when(toggle.initial()).thenReturn(true);
        NumberRangeDialogInput range = mock(NumberRangeDialogInput.class);
        when(range.key()).thenReturn("scale");
        when(range.label()).thenReturn(Component.text("Size"));
        when(range.start()).thenReturn(0.5f);
        when(range.end()).thenReturn(2f);
        when(range.initial()).thenReturn(null);
        when(range.step()).thenReturn(0.1f);
        SingleOptionDialogInput single = mock(SingleOptionDialogInput.class);
        SingleOptionDialogInput.OptionEntry red = mock(SingleOptionDialogInput.OptionEntry.class);
        when(red.id()).thenReturn("red");
        when(red.display()).thenReturn(Component.text("Red"));
        SingleOptionDialogInput.OptionEntry blue = mock(SingleOptionDialogInput.OptionEntry.class);
        when(blue.id()).thenReturn("blue");
        when(blue.initial()).thenReturn(true);
        when(single.key()).thenReturn("colour");
        when(single.label()).thenReturn(Component.text("Colour"));
        when(single.entries()).thenReturn(List.of(red, blue));

        List<BedrockField> fields = FormDialog.bedrockFields(List.of(Component.text("Name it")),
                List.of(text, toggle, range, single));

        assertEquals(new BedrockField.Label("Name it"), fields.get(0));
        assertEquals(new BedrockField.Input("name", "Name", "", "Rex"), fields.get(1));
        assertEquals(new BedrockField.Toggle("visible", "Visible", true), fields.get(2));
        assertEquals(new BedrockField.Slider("scale", "Size", 0.5f, 2f, 0.1f, 0.5f), fields.get(3));
        BedrockField.Dropdown dropdown = (BedrockField.Dropdown) fields.get(4);
        assertEquals(List.of("red", "blue"), dropdown.ids());
        assertEquals(List.of("Red", "blue"), dropdown.labels(), "an option with no display falls back to its id");
        assertEquals(1, dropdown.initial());
    }

    @Test
    void bedrockAnswersReadThroughTheSameResponseApi() {
        FormResponse response = FormResponse.fromBedrock(player,
                Map.of("name", "Fido", "visible", false, "scale", 9f, "colour", "blue"));
        assertTrue(response.fromBedrock());
        assertEquals("Fido", response.text("name", "?"));
        assertFalse(response.flag("visible", true));
        assertEquals(2f, response.number("scale", 0.5f, 2f, 1f), "out-of-range values are clamped");
        assertEquals("blue", response.option("colour", "red"));
        assertEquals("fallback", response.text("missing", "fallback"));
        assertEquals(null, response.raw());
    }

    @Test
    void noneKeepsJavaPlayersOnTheJavaPath() {
        assertFalse(BedrockForms.none().isBedrock(player.getUniqueId()));
        assertFalse(BedrockForms.detect(plugin, null).available(), "no Floodgate in tests");
    }
}
