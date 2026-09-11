package dev.anchorlight.stonelib.message;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private MessageService service;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        plugin.getDataFolder().mkdirs();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        try (PrintWriter writer = new PrintWriter(messagesFile)) {
            writer.println("prefix: '<gray>[Test] '");
            writer.println("greeting: 'Hello, {0}!'");
        }
        service = new MessageService(plugin, "messages.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPositionalPlaceholderAndPrefix() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("greeting", "Steve"));
        assertEquals("[Test] Hello, Steve!", plain);
    }

    @Test
    void missingKeyReturnsPlaceholderText() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("nope"));
        assertEquals("[Test] [Missing message: nope]", plain);
    }

    @Test
    void placeholderValueCannotInjectLiveMiniMessageTags() {
        // A placeholder value containing what looks like a MiniMessage click/hover tag (e.g. a
        // player's own display name, or any value not authored by the plugin's own config) must
        // never be parsed as live markup -- it must render as inert literal text, and it must not
        // be able to execute a command or open a URL via a smuggled click event.
        String maliciousName = "<click:run_command:/op me><bold>Steve";
        var component = service.get("greeting", maliciousName);

        assertNull(component.clickEvent(), "a placeholder value must never produce a live click event");
        component.children().forEach(child ->
            assertNull(child.clickEvent(), "no child component may carry a live click event either"));

        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.contains("<click:run_command:/op me>"),
            "the tag text must survive literally rather than being silently dropped");
    }

    @Test
    void sendDeliversMessage() {
        // Note: PlayerMock fails due to registry initialization in this Paper 1.21.3 environment.
        // Using ConsoleCommandSenderMock as a workaround (verified to work in Task 3).
        var sender = server.getConsoleSender();
        service.send(sender, "greeting", "Alex");

        // Verify message was actually delivered to the sender by reading what it received
        // ConsoleCommandSenderMock (like PlayerMock) supports nextMessage() to retrieve sent messages
        var consoleSender = (org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock) sender;
        String received = consoleSender.nextMessage();
        assertTrue(received.contains("Hello, Alex!"));
    }
}
