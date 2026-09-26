package dev.anchorlight.stonelib.shop;

import dev.anchorlight.stonelib.loot.LootTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A shop read out of config: categories, each holding entries.
 *
 * <p>Expected shape:
 *
 * <pre>{@code
 * categories:
 *   supplies:
 *     display_name: "<gold>Supplies"
 *     icon: CHEST
 *     menu_slot: 11
 *     items:
 *       - id: iron
 *         name: "<white>Iron Ingot"
 *         material: IRON_INGOT
 *         amount: 8
 *         price: 40
 *         lore: ["<gray>Honest metal."]
 *       - id: war_axe
 *         name: "<red>War Axe"
 *         material: DIAMOND_AXE
 *         price: 400
 *         stock: 3                 # three for the whole event, across everyone
 *   late:
 *     display_name: "<dark_red>Chaos"
 *     visible_when: chaos_open     # resolved by the plugin, not by the engine
 *     items:
 *       - id: meteor
 *         name: "<red>Call a Meteor"
 *         icon: FIRE_CHARGE
 *         price: 250
 *         action: WORLD_EVENT      # the plugin's vocabulary
 *         args:
 *           event: meteor
 * }</pre>
 *
 * <p>A malformed entry is logged and skipped rather than thrown: one bad row in a shop file should
 * cost that row, not stop the server from starting mid-event.
 */
public final class ShopCatalog {

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Logger logger;

    public ShopCatalog(Logger logger) {
        this.logger = logger;
    }

    /**
     * Replaces the catalogue from a config section.
     *
     * @param root the section holding the categories, e.g. {@code config.getConfigurationSection("categories")}
     */
    public void load(ConfigurationSection root) {
        categories.clear();
        if (root == null) {
            logger.warning("Shop has no categories section - it will be empty.");
            return;
        }
        // Spread default icons across the second row when a category does not pin its own slot.
        int fallbackSlot = 10;
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            List<ShopEntry> entries = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("items")) {
                ShopEntry entry = parseEntry(id, raw);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            Material icon = LootTable.material(section.getString("icon", "CHEST"));
            categories.put(id, new ShopCategory(
                    id,
                    section.getString("display_name", rawId),
                    icon,
                    section.getInt("menu_slot", fallbackSlot),
                    entries,
                    blankToNull(section.getString("visible_when"))));
            fallbackSlot += 2;
        }
        logger.info("Shop loaded: " + categories.size() + " categories, "
                + categories.values().stream().mapToInt(c -> c.entries().size()).sum() + " entries.");
    }

    private ShopEntry parseEntry(String categoryId, Map<?, ?> raw) {
        String id = string(raw.get("id"), "").trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            logger.warning("Shop entry in category " + categoryId + " has no id - skipped.");
            return null;
        }
        Material icon = LootTable.material(string(raw.get("icon"), string(raw.get("material"), "")));
        if (icon == null) {
            // Not fatal: an action entry legitimately has no material, and PAPER is a visible
            // placeholder that shows staff the row loaded but its icon did not.
            icon = Material.PAPER;
        }
        List<String> lore = new ArrayList<>();
        if (raw.get("lore") instanceof List<?> list) {
            list.forEach(line -> lore.add(String.valueOf(line)));
        }
        Map<String, String> args = new LinkedHashMap<>();
        if (raw.get("args") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> args.put(String.valueOf(key), String.valueOf(value)));
        }
        // Convenience: a bare material is the commonest arg by far, so carry it through as one.
        String material = string(raw.get("material"), null);
        if (material != null) {
            args.putIfAbsent("material", material);
        }
        if (raw.get("enchantments") instanceof Map<?, ?> map) {
            map.forEach((key, value) -> args.put("enchantment." + key, String.valueOf(value)));
        }

        int price = raw.get("price") instanceof Number number ? number.intValue() : -1;
        if (price < 0) {
            logger.warning("Shop entry " + categoryId + "/" + id
                    + " has no price - it will be shown but cannot be bought.");
        }
        return new ShopEntry(
                id,
                string(raw.get("name"), id),
                lore,
                icon,
                raw.get("amount") instanceof Number amount ? amount.intValue() : 1,
                price,
                string(raw.get("action"), "ITEM").trim().toUpperCase(Locale.ROOT),
                args,
                raw.get("stock") instanceof Number stock ? stock.intValue() : ShopEntry.UNLIMITED,
                blankToNull(string(raw.get("visible_when"), null)));
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ----------------------------------------------------------------- reads

    public List<ShopCategory> categories() {
        return List.copyOf(categories.values());
    }

    public ShopCategory category(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** Finds an entry anywhere in the catalogue by its id. */
    public ShopEntry entry(String entryId) {
        if (entryId == null) {
            return null;
        }
        String id = entryId.toLowerCase(Locale.ROOT);
        for (ShopCategory category : categories.values()) {
            ShopEntry entry = category.entry(id);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    /** The category an entry belongs to, or null. */
    public ShopCategory categoryOf(String entryId) {
        if (entryId == null) {
            return null;
        }
        String id = entryId.toLowerCase(Locale.ROOT);
        for (ShopCategory category : categories.values()) {
            if (category.entry(id) != null) {
                return category;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }
}
