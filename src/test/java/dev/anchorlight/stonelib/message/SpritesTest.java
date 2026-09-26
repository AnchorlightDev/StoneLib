package dev.anchorlight.stonelib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpritesTest {

    @SuppressWarnings("unused")
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------- caching (§9.1)

    @Test
    void repeatedLookupsReturnTheSameCachedInstance() {
        // Components are immutable, so this costs nothing -- and a boss bar rebuilding its text
        // every tick must not allocate a fresh component tree per frame.
        Component first = Sprites.of(Material.DIAMOND_SWORD);
        Component second = Sprites.of(Material.DIAMOND_SWORD);
        assertSame(first, second);
    }

    @Test
    void stackDelegatesToItsType() {
        assertSame(Sprites.of(Material.DIAMOND_SWORD),
                Sprites.of(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD, 3)));
    }

    // ----------------------------------------------------------- resolution (§9.3)

    @Test
    void anItemResolvesToTheItemsAtlas() {
        Component c = Sprites.of(Material.DIAMOND_SWORD);
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, c);
        SpriteObjectContents sprite = assertInstanceOf(SpriteObjectContents.class, object.contents());
        assertEquals("minecraft:items", sprite.atlas().asString());
        assertEquals("minecraft:item/diamond_sword", sprite.sprite().asString());
    }

    @Test
    void aBlockResolvesToTheBlocksAtlas() {
        Component c = Sprites.of(Material.STONE);
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, c);
        SpriteObjectContents sprite = assertInstanceOf(SpriteObjectContents.class, object.contents());
        assertEquals("minecraft:blocks", sprite.atlas().asString());
        assertEquals("minecraft:block/stone", sprite.sprite().asString());
    }

    @Test
    void everyMaterialResolvesWithoutThrowing() {
        // §9.3: an unmapped material must fall through to text rather than blow up. Sweeping the
        // whole enum is the only way to be sure none of the odd corners (legacy, air, non-item
        // blocks) takes a path that throws.
        for (Material material : Material.values()) {
            assertTrue(Sprites.of(material) != null, material.name());
            assertTrue(Sprites.labelled(material) != null, material.name());
        }
    }

    @Test
    void airFallsBackToTextRatherThanASprite() {
        assertInstanceOf(TranslatableComponent.class, Sprites.of(Material.AIR));
    }

    @Test
    void spritesCarryTheItemNameAsClientSideFallback() {
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, Sprites.of(Material.DIAMOND_SWORD));
        assertInstanceOf(TranslatableComponent.class, object.fallback());
    }

    @Test
    void nullsAreEmptyRatherThanAnException() {
        assertEquals(Component.empty(), Sprites.of((Material) null));
        assertEquals(Component.empty(), Sprites.of((org.bukkit.inventory.ItemStack) null));
        assertEquals(Component.empty(), Sprites.labelled(null));
    }

    // ------------------------------------------------------------- labelled (§9.10)

    @Test
    void labelledAppendsTheTranslatableName() {
        Component c = Sprites.labelled(Material.DIAMOND_SWORD);
        // The name must be a translatable, not a server-side English guess, so each viewer sees it
        // in their own language exactly as their inventory shows it.
        assertTrue(c.children().stream().anyMatch(child -> child instanceof TranslatableComponent),
                "labelled() should append a translatable name, got: " + c);
    }

    @Test
    void labelledDoesNotRepeatTheNameWhenTheSpriteAlreadyFellBack() {
        // AIR resolves to text; "Air Air" would help nobody.
        assertEquals(Sprites.of(Material.AIR), Sprites.labelled(Material.AIR));
    }

    // ------------------------------------------------------------ escape hatch

    @Test
    void arbitraryAtlasAndSpritePassThroughUnmapped() {
        Component c = Sprites.of(net.kyori.adventure.key.Key.key("stonelib:custom"),
                net.kyori.adventure.key.Key.key("thing/widget"));
        ObjectComponent object = assertInstanceOf(ObjectComponent.class, c);
        SpriteObjectContents sprite = assertInstanceOf(SpriteObjectContents.class, object.contents());
        assertEquals("stonelib:custom", sprite.atlas().asString());
        assertEquals("minecraft:thing/widget", sprite.sprite().asString());
    }

    // --------------------------------------------------------- icon tag (§9.7)

    @Test
    void iconResolverEmitsASpriteWhenEnabled() {
        Component c = MiniMessage.miniMessage().deserialize(
                "<icon:'diamond_sword'>", Sprites.iconResolver(IconOptions.defaults()));
        assertTrue(containsSprite(c), "expected a sprite in: " + c);
    }

    @Test
    void iconResolverAcceptsNamespacedAndMixedCaseNames() {
        for (String name : new String[]{"DIAMOND_SWORD", "minecraft:diamond_sword", "Diamond_Sword"}) {
            Component c = MiniMessage.miniMessage().deserialize(
                    "<icon:'" + name + "'>", Sprites.iconResolver(IconOptions.defaults()));
            assertTrue(containsSprite(c), name + " should resolve, got: " + c);
        }
    }

    @Test
    void iconResolverWithSpritesDisabledEmitsTheTextFallback() {
        // §9.7 -- the per-plugin kill switch. Every message stays readable, just not pictorial.
        Component c = MiniMessage.miniMessage().deserialize(
                "<icon:'diamond_sword'>", Sprites.iconResolver(IconOptions.disabled()));
        assertFalse(containsSprite(c), "expected no sprite when disabled, got: " + c);
        assertTrue(containsTranslatable(c), "expected the translated name instead, got: " + c);
    }

    @Test
    void unknownMaterialDegradesToTextWithoutThrowing() {
        // §4: "An unknown material must not throw -- emit the text fallback."
        Component c = MiniMessage.miniMessage().deserialize(
                "<icon:'not_a_real_item'>", Sprites.iconResolver(IconOptions.defaults()));
        assertEquals("Not A Real Item", PlainTextComponentSerializer.plainText().serialize(c));
    }

    @Test
    void labelledIconOptionEmitsSpriteAndName() {
        Component c = MiniMessage.miniMessage().deserialize(
                "<icon:'diamond_sword'>", Sprites.iconResolver(IconOptions.labelledDefaults()));
        assertTrue(containsSprite(c), "expected a sprite in: " + c);
        assertTrue(containsTranslatable(c), "expected a translated name in: " + c);
    }

    // ---------------------------------------------------------------- audit (§6)

    @Test
    void auditPageRendersAPageOfMaterials() {
        Component page = Sprites.auditPage(1, 10);
        String plain = PlainTextComponentSerializer.plainText().serialize(page);
        assertTrue(plain.startsWith("Sprite audit - page 1/"), plain);
        assertTrue(plain.contains(Material.values()[0].name()), plain);
    }

    @Test
    void auditPageOutOfRangeReturnsANoticeRatherThanThrowing() {
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(Sprites.auditPage(Integer.MAX_VALUE, 10));
        assertTrue(plain.startsWith("No such page."), plain);
    }

    @Test
    void auditPageToleratesSillyPagination() {
        assertTrue(Sprites.auditPage(0, 0) != null);
        assertTrue(Sprites.auditPage(-5, -5) != null);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean containsSprite(Component component) {
        if (component instanceof ObjectComponent object
                && object.contents() instanceof SpriteObjectContents) {
            return true;
        }
        return component.children().stream().anyMatch(SpritesTest::containsSprite);
    }

    private static boolean containsTranslatable(Component component) {
        if (component instanceof TranslatableComponent) {
            return true;
        }
        return component.children().stream().anyMatch(SpritesTest::containsTranslatable);
    }
}
