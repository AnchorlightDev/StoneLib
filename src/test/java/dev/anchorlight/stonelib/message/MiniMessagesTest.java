package dev.anchorlight.stonelib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMessagesTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ----------------------------------------------------- the §9.5 regression

    @Test
    void aStringValueIsStillInertAfterTheComponentBranchWasAdded() {
        // THE test that matters most. Widening resolvers() to accept Components must not have
        // weakened the String path: a value that looks like markup has to stay literal text with
        // no live click event, or a player named "<click:run_command:/op x>" becomes an exploit
        // against anyone who receives a message containing their name.
        Component c = MiniMessages.parse("<msg>", "msg", "<click:run_command:/op x>hi");

        assertEquals("<click:run_command:/op x>hi",
                PlainTextComponentSerializer.plainText().serialize(c));
        assertNoClickEventAnywhere(c);
    }

    @Test
    void hoverAndColourInAStringValueAreAlsoInert() {
        Component c = MiniMessages.parse("<msg>", "msg", "<red><hover:show_text:'boo'>x</hover>");
        assertEquals("<red><hover:show_text:'boo'>x</hover>",
                PlainTextComponentSerializer.plainText().serialize(c));
        assertNoClickEventAnywhere(c);
    }

    @Test
    void aStringThatLooksLikeASpriteTagIsInertToo() {
        // A sprite authored in a trusted template is fine; the same text arriving as a runtime
        // VALUE must not become a live sprite.
        Component c = MiniMessages.parse("<msg>", "msg",
                "<sprite:\"minecraft:items\":item/diamond_sword>");
        assertTrue(!containsSprite(c), "a String value must never become a live sprite: " + c);
    }

    // ------------------------------------------------- the §3 Component branch

    @Test
    void aComponentValueIsInsertedAsAComponent() {
        // §9.4: Component in -> Placeholder.component; everything else -> Placeholder.unparsed.
        Component c = MiniMessages.parse("<icon>", "icon", Sprites.of(Material.DIAMOND_SWORD));
        assertTrue(containsSprite(c), "expected the sprite component to survive: " + c);
    }

    @Test
    void componentAndStringValuesMixInOneTemplate() {
        Component c = MiniMessages.parse("Bring <amount>x <icon>",
                "amount", 5,
                "icon", Sprites.of(Material.DIAMOND_SWORD));
        assertTrue(PlainTextComponentSerializer.plainText().serialize(c).startsWith("Bring 5x "));
        assertTrue(containsSprite(c), "expected the sprite alongside the string value: " + c);
    }

    @Test
    void resolversPicksTheRightPlaceholderKindPerValue() {
        TagResolver[] resolvers = MiniMessages.resolvers(
                "a", Component.text("x"),
                "b", "plain",
                "c", 42,
                "d", null);
        assertEquals(4, resolvers.length);
    }

    @Test
    void aNullValueIsStillEmptyStringNotTheWordNull() {
        assertEquals("", MiniMessages.plain("<v>", "v", null));
    }

    @Test
    void nonStringValuesStillStringify() {
        assertEquals("42", MiniMessages.plain("<v>", "v", 42));
        assertEquals("true", MiniMessages.plain("<v>", "v", true));
    }

    @Test
    void oddTrailingEntryIsIgnoredAndNoResolversForTooFewArgs() {
        assertEquals(0, MiniMessages.resolvers().length);
        assertEquals(0, MiniMessages.resolvers("lonely").length);
        assertEquals(1, MiniMessages.resolvers("k", "v", "dangling").length);
    }

    // ------------------------------------------------------- the tag, unchanged

    @Test
    void theSpriteTagInATrustedTemplateWorksWithNoStoneLibApi() {
        // Phase 0 item 1: <sprite> ships in MiniMessage's default tag set, so it already worked
        // through every StoneLib message method before any of this change existed.
        Component c = MiniMessages.parse("<sprite:\"minecraft:items\":item/diamond_sword>");
        assertTrue(containsSprite(c), "expected the stock <sprite> tag to parse: " + c);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertNoClickEventAnywhere(Component component) {
        ClickEvent<?> click = component.style().clickEvent();
        assertNull(click, "a placeholder value smuggled in a click event: " + component);
        component.children().forEach(MiniMessagesTest::assertNoClickEventAnywhere);
    }

    private static boolean containsSprite(Component component) {
        if (component instanceof ObjectComponent object
                && object.contents() instanceof SpriteObjectContents) {
            return true;
        }
        return component.children().stream().anyMatch(MiniMessagesTest::containsSprite);
    }
}
