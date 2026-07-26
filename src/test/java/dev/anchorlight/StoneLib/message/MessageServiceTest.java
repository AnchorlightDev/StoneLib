package dev.anchorlight.StoneLib.message;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void sendDeliversMessage() {
        // Note: PlayerMock fails due to registry initialization in this Paper 1.21.3 environment.
        // Using ConsoleCommandSenderMock as a workaround (verified to work in Task 3).
        var sender = server.getConsoleSender();
        service.send(sender, "greeting", "Alex");

        // Verify message was actually delivered to the sender by reading what it received
        // ConsoleCommandSenderMock (like PlayerMock) supports nextMessage() to retrieve sent messages
        var consoleSender = (be.seeseemelk.mockbukkit.command.ConsoleCommandSenderMock) sender;
        String received = consoleSender.nextMessage();
        assertTrue(received.contains("Hello, Alex!"));
    }
}
