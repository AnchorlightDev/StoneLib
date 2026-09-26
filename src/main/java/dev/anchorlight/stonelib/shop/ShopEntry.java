package dev.anchorlight.stonelib.shop;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One purchasable row of a shop.
 *
 * <p>{@code action} and {@code args} are deliberately opaque to the engine. A shop's vocabulary of
 * effects - give an item, summon a boss, move a border - is the one part of a shop that is never
 * reusable, so the engine carries these through to the plugin untouched and never interprets them.
 * See {@link ShopContext#deliver}.
 *
 * @param id         unique within the catalogue, lower-cased on parse
 * @param name       display name, a MiniMessage string
 * @param lore       extra lore lines, MiniMessage strings, shown above the engine's own
 * @param icon       the menu icon
 * @param amount     stack size for the icon and, conventionally, for what is delivered
 * @param price      cost in the plugin's currency; negative means unpriced and unbuyable
 * @param action     the plugin's action keyword
 * @param args       arbitrary extra arguments for the action
 * @param stock      how many may be bought across the whole event, or -1 for unlimited
 * @param visibleWhen an opaque condition tag resolved by {@link ShopContext#visible}, or null when
 *                    the entry is always shown
 */
public record ShopEntry(String id, String name, List<String> lore, Material icon, int amount,
                        int price, String action, Map<String, String> args,
                        int stock, String visibleWhen) {

    /** Sentinel for an entry with no purchase limit. */
    public static final int UNLIMITED = -1;

    public ShopEntry {
        lore = lore == null ? List.of() : List.copyOf(lore);
        args = args == null ? Map.of() : Map.copyOf(args);
        amount = Math.max(1, amount);
        icon = icon == null ? Material.PAPER : icon;
    }

    /** Whether this entry has a finite stock. */
    public boolean limited() {
        return stock >= 0;
    }

    /** Whether the entry can be bought at all. An unpriced entry is a config slip, not a freebie. */
    public boolean priced() {
        return price >= 0;
    }

    /** An argument, or {@code fallback} when it is absent. */
    public String arg(String key, String fallback) {
        return args.getOrDefault(key, fallback);
    }

    /** An argument parsed as an int, or {@code fallback} when absent or not a number. */
    public int intArg(String key, int fallback) {
        String raw = args.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** An argument parsed as a double, or {@code fallback} when absent or not a number. */
    public double doubleArg(String key, double fallback) {
        String raw = args.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
