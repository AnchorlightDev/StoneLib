package dev.anchorlight.stonelib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SectionMessagesTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static YamlConfiguration config() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix", "[P] ");
        config.set("messages.hit", "You hit %player% for 50% damage");
        config.set("messages.named", "Hi <player>");
        return config;
    }

    @Test
    void readsASectionAndAppliesThePrefix() {
        YamlConfiguration config = config();
        MessageService messages = new MessageService(() -> config.getConfigurationSection("messages"));
        assertEquals("[P] Hi Steve", plain(messages.getNamed("named", "player", "Steve")));
        assertEquals("Hi Steve", plain(messages.getNamedUnprefixed("named", "player", "Steve")));
    }

    @Test
    void legacyPercentPlaceholdersOnlyRewriteSuppliedNames() {
        YamlConfiguration config = config();
        MessageService messages = new MessageService(() -> config.getConfigurationSection("messages"))
                .legacyPercentPlaceholders(true);
        assertEquals("[P] You hit Steve for 50% damage", plain(messages.getNamed("hit", "player", "Steve")));
    }

    @Test
    void placeholderValuesCannotInjectMarkup() {
        YamlConfiguration config = config();
        MessageService messages = new MessageService(() -> config.getConfigurationSection("messages"))
                .legacyPercentPlaceholders(true);
        Component out = messages.getNamed("hit", "player", "<click:run_command:'/op me'>x");
        assertEquals("[P] You hit <click:run_command:'/op me'>x for 50% damage", plain(out));
    }

    @Test
    void reloadRereadsTheSupplier() {
        YamlConfiguration[] current = {config()};
        MessageService messages = new MessageService(() -> current[0].getConfigurationSection("messages"));
        YamlConfiguration next = config();
        next.set("messages.named", "Bye <player>");
        current[0] = next;
        messages.reload();
        assertEquals("Bye A", plain(messages.getNamedUnprefixed("named", "player", "A")));
    }

    @Test
    void buttonsCarryTheirCommandAndHover() {
        Component button = Buttons.runCommand(Component.text("[Go]"), "pvp enable", Component.text("hover"));
        ClickEvent click = button.clickEvent();
        assertNotNull(click);
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.action());
        assertNotNull(button.hoverEvent());
        assertNull(Buttons.runCommand(Component.text("x"), "/a", null).hoverEvent());
        assertEquals("[A] | [B]", plain(Buttons.row(Component.text(" | "), Component.text("[A]"), null, Component.text("[B]"))));
    }
}
