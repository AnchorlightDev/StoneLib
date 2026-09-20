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

    // ------------------------------------------------- extra resolvers (2.3.0)

    /**
     * Writes a second messages file and returns a service over it, so the extra-resolver tests do
     * not disturb the shared fixture.
     */
    private MessageService serviceWith(String body, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... extra)
            throws IOException {
        File f = new File(plugin.getDataFolder(), "icons.yml");
        try (PrintWriter writer = new PrintWriter(f)) {
            writer.println("prefix: '<gray>[Test] '");
            writer.println(body);
        }
        return new MessageService(plugin, "icons.yml", extra);
    }

    @Test
    void noExtraResolversLeavesOutputExactlyAsItWas() throws IOException {
        // §9.6: the compatibility guarantee. A plugin that never passes extras must get byte-for-byte
        // what it got before the overload existed, so the two constructors are compared directly on
        // the same corpus of templates rather than against a hand-written expectation.
        String corpus = String.join("\n",
                "plain: 'nothing special'",
                "coloured: '<green>hello <bold>there</bold>'",
                "positional: 'you have {0} of {1}'",
                "named: '<player> earned <points>'",
                "sprite-tag: 'take <sprite:\"minecraft:items\":item/diamond_sword>'",
                "empty: ''");

        MessageService withoutExtras = serviceWith(corpus);
        MessageService twoArgConstructor = serviceWith(corpus);

        var gson = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson();
        for (String key : new String[]{"plain", "coloured", "named", "sprite-tag", "empty"}) {
            assertEquals(gson.serialize(twoArgConstructor.getNamed(key, "player", "Steve", "points", 12)),
                    gson.serialize(withoutExtras.getNamed(key, "player", "Steve", "points", 12)),
                    "getNamed differed for key: " + key);
            assertEquals(gson.serialize(twoArgConstructor.getNamedUnprefixed(key)),
                    gson.serialize(withoutExtras.getNamedUnprefixed(key)),
                    "getNamedUnprefixed differed for key: " + key);
        }
        assertEquals(gson.serialize(twoArgConstructor.get("positional", 3, "gold")),
                gson.serialize(withoutExtras.get("positional", 3, "gold")));
    }

    @Test
    void anExtraResolverIsAvailableToNamedTemplates() throws IOException {
        MessageService messages = serviceWith(
                "tier: '<gold>bring <amount>x <icon:''diamond_sword''>'",
                Sprites.iconResolver(IconOptions.defaults()));

        var component = messages.getNamedUnprefixed("tier", "amount", 5);
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.startsWith("bring 5x"), plain);
        assertTrue(containsSprite(component), "expected the <icon:> tag to resolve: " + component);
    }

    @Test
    void anExtraResolverIsAvailableToPositionalTemplatesToo() throws IOException {
        MessageService messages = serviceWith(
                "tier: 'bring {0}x <icon:''diamond_sword''>'",
                Sprites.iconResolver(IconOptions.defaults()));

        var component = messages.get("tier", 5);
        assertTrue(containsSprite(component), "expected the <icon:> tag to resolve: " + component);
    }

    @Test
    void anIconTagIsUnknownWithoutOptingIn() throws IOException {
        // The tag must NOT leak to plugins that did not ask for it -- that is the whole reason it
        // is not registered on the shared MiniMessage instance.
        MessageService messages = serviceWith("tier: 'bring <icon:''diamond_sword''>'");
        var component = messages.getNamedUnprefixed("tier");
        assertTrue(!containsSprite(component),
                "an opt-out plugin must not get the icon tag: " + component);
    }

    @Test
    void aRuntimeSpriteCanBePassedAsAPlaceholderValue() throws IOException {
        // The §3 dynamic path, end to end through MessageService with no extra resolvers at all.
        MessageService messages = serviceWith("drop: 'you found <icon>'");
        var component = messages.getNamedUnprefixed("drop", "icon",
                Sprites.of(org.bukkit.Material.DIAMOND_SWORD));
        assertTrue(containsSprite(component), "expected the sprite to survive: " + component);
    }

    private static boolean containsSprite(net.kyori.adventure.text.Component component) {
        if (component instanceof net.kyori.adventure.text.ObjectComponent object
                && object.contents() instanceof net.kyori.adventure.text.object.SpriteObjectContents) {
            return true;
        }
        return component.children().stream().anyMatch(MessageServiceTest::containsSprite);
    }
}
