package dev.anchorlight.stonelib;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Helper class providing fluent item creation and modification.
 * Simplifies building ItemStacks through method chaining.
 *
 * <p>Custom items are identified by a <em>tag</em> in persistent data, never by their display name.
 * A name is player-visible text: it can be changed in an anvil, it changes when someone edits
 * {@code messages.yml}, and it differs per language. Matching on one is how a shop item becomes
 * forgeable with a rename. Use {@link #tag} when building and {@link #tagOf} or {@link #hasTag}
 * when reading, and the item stays identifiable whatever it ends up called.
 */
public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    private ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /** As {@link #of(Material)}, with a stack size. Amounts below 1 are raised to 1. */
    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder lore(Component... lore) {
        meta.lore(Arrays.asList(lore));
        return this;
    }

    /** As {@link #lore(Component...)}, for lore that has already been assembled into a list. */
    public ItemBuilder lore(List<Component> lore) {
        meta.lore(List.copyOf(lore));
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    // ------------------------------------------------------------------ tags

    /**
     * Writes a string tag into the item's persistent data.
     *
     * @param key   the tag key, namespaced to your plugin
     * @param value the value; null removes the tag
     */
    public ItemBuilder tag(NamespacedKey key, String value) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (value == null) {
            container.remove(key);
        } else {
            container.set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    /** As {@link #tag(NamespacedKey, String)}, building the key from your plugin and a name. */
    public ItemBuilder tag(Plugin plugin, String key, String value) {
        return tag(new NamespacedKey(plugin, key), value);
    }

    /** Writes an integer tag - a tier number, a charge count, a price paid. */
    public ItemBuilder tag(NamespacedKey key, int value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value);
        return this;
    }

    // -------------------------------------------------------------- cosmetics

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, Math.max(1, level), true);
        return this;
    }

    /**
     * Gives the item an enchantment shimmer without an actual enchantment.
     *
     * <p>Uses the glint override, so the item carries no real enchantment and no hidden level for
     * a grindstone to strip.
     */
    public ItemBuilder glow() {
        meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /** Hides the attribute and enchantment lines, for a menu icon that should read as plain. */
    public ItemBuilder hideDetails() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------------- readers

    /**
     * Reads a string tag from an item.
     *
     * <p>Null-safe in every direction: the item, its meta and the tag may all be absent, which is
     * the normal case for the arbitrary stacks that arrive from a player inventory or a click
     * event.
     *
     * @return the tag value, or null when the item does not carry it
     */
    public static String tagOf(ItemStack item, NamespacedKey key) {
        if (item == null || key == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null
                : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** As {@link #tagOf(ItemStack, NamespacedKey)}, building the key from your plugin and a name. */
    public static String tagOf(ItemStack item, Plugin plugin, String key) {
        return tagOf(item, new NamespacedKey(plugin, key));
    }

    /** Reads an integer tag, returning {@code fallback} when it is absent. */
    public static int intTagOf(ItemStack item, NamespacedKey key, int fallback) {
        if (item == null || key == null || !item.hasItemMeta()) {
            return fallback;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return fallback;
        }
        Integer value = meta.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    /** Whether an item carries a string tag at all, whatever its value. */
    public static boolean hasTag(ItemStack item, NamespacedKey key) {
        return tagOf(item, key) != null;
    }

    /**
     * Whether an item carries a string tag with a specific value.
     *
     * <p>The comparison is case-sensitive, matching how the value was written.
     */
    public static boolean hasTag(ItemStack item, NamespacedKey key, String expected) {
        String actual = tagOf(item, key);
        return actual != null && actual.equals(expected);
    }
}
