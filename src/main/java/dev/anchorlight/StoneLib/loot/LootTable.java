package dev.anchorlight.StoneLib.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * A weighted loot table built from a config list - crates, drops, rewards, anything that hands out
 * a randomised set of items.
 *
 * <p>Config shape:
 * <pre>{@code
 * loot:
 *   - material: IRON_INGOT
 *     min: 2
 *     max: 6
 *     weight: 10
 *   - material: ENCHANTED_BOOK
 *     weight: 1
 *     enchantments:
 *       minecraft:sharpness: 3
 * }</pre>
 *
 * <p>Enchantments are applied as stored enchantments on a book and as real ones on anything else, so
 * the same config shape works for both. Unknown materials and enchantments are logged and skipped
 * rather than thrown - a typo in a config file should not take an event down.
 */
public final class LootTable {

    /** One weighted row of a loot table. */
    public record Entry(Material material, int min, int max, double weight, Map<String, Integer> enchantments) {
    }

    private final List<Entry> entries;
    private final double totalWeight;
    private final Random random;

    private LootTable(List<Entry> entries, Random random) {
        this.entries = List.copyOf(entries);
        this.totalWeight = entries.stream().mapToDouble(Entry::weight).sum();
        this.random = random;
    }

    /** Builds a table from a list of raw config maps, e.g. {@code section.getMapList("loot")}. */
    public static LootTable fromMapList(List<Map<?, ?>> rows, Logger logger) {
        return fromMapList(rows, logger, new Random());
    }

    public static LootTable fromMapList(List<Map<?, ?>> rows, Logger logger, Random random) {
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            Material material = material(String.valueOf(row.get("material")));
            if (material == null) {
                logger.warning("Unknown loot material: " + row.get("material") + " - entry skipped.");
                continue;
            }
            int min = intOf(row.get("min"), 1);
            int max = Math.max(min, intOf(row.get("max"), min));
            double weight = Math.max(0.0001, doubleOf(row.get("weight"), 1.0));
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            if (row.get("enchantments") instanceof Map<?, ?> map) {
                map.forEach((key, value) -> enchantments.put(String.valueOf(key), intOf(value, 1)));
            }
            entries.add(new Entry(material, min, max, weight, enchantments));
        }
        return new LootTable(entries, random);
    }

    /** Builds a table from {@code section.getMapList(path)}, tolerating a null section. */
    public static LootTable fromSection(ConfigurationSection section, String path, Logger logger) {
        if (section == null) {
            return new LootTable(List.of(), new Random());
        }
        return fromMapList(section.getMapList(path), logger);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Rolls the table {@code rolls} times. Rolls are independent, so duplicates are possible. */
    public List<ItemStack> roll(int rolls) {
        List<ItemStack> out = new ArrayList<>();
        if (entries.isEmpty()) {
            return out;
        }
        for (int i = 0; i < Math.max(1, rolls); i++) {
            ItemStack item = build(pick());
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** A single weighted pick, for callers that want the entry rather than a built item. */
    public Entry pick() {
        double target = random.nextDouble() * totalWeight;
        double running = 0;
        for (Entry entry : entries) {
            running += entry.weight();
            if (target <= running) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private ItemStack build(Entry entry) {
        int amount = entry.min() >= entry.max()
                ? entry.min()
                : entry.min() + random.nextInt(entry.max() - entry.min() + 1);
        if (amount <= 0) {
            return null;
        }
        ItemStack item = new ItemStack(entry.material(), Math.min(amount, entry.material().getMaxStackSize()));
        if (entry.enchantments().isEmpty()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        entry.enchantments().forEach((name, level) -> {
            Enchantment enchantment = enchantment(name);
            if (enchantment == null) {
                return;
            }
            if (meta instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        });
        item.setItemMeta(meta);
        return item;
    }

    /** Resolves a material by name, with or without the {@code minecraft:} namespace. */
    public static Material material(String raw) {
        if (raw == null) {
            return null;
        }
        Material direct = Material.matchMaterial(raw);
        return direct != null ? direct : Material.matchMaterial("minecraft:" + raw.toLowerCase(Locale.ROOT));
    }

    /** Resolves an enchantment from the registry by key, e.g. {@code minecraft:sharpness}. */
    public static Enchantment enchantment(String raw) {
        if (raw == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double doubleOf(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
