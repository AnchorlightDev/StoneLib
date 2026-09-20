package dev.anchorlight.stonelib.shop;

import org.bukkit.Material;

import java.util.List;

/**
 * One page of a shop, reached from the category menu.
 *
 * @param id          unique within the catalogue, lower-cased on parse
 * @param name        display name, a MiniMessage string
 * @param icon        the icon shown in the category menu
 * @param menuSlot    where that icon sits in the menu
 * @param entries     the rows on this page, in config order
 * @param visibleWhen an opaque condition tag resolved by {@link ShopContext#visible}, or null when
 *                    the category is always shown. A category hidden this way is not merely greyed
 *                    out: it cannot be opened and its entries cannot be bought, so a late-game
 *                    category is not visible to be planned around for the whole event.
 */
public record ShopCategory(String id, String name, Material icon, int menuSlot,
                           List<ShopEntry> entries, String visibleWhen) {

    public ShopCategory {
        entries = entries == null ? List.of() : List.copyOf(entries);
        icon = icon == null ? Material.CHEST : icon;
    }

    public ShopEntry entry(String entryId) {
        if (entryId == null) {
            return null;
        }
        for (ShopEntry entry : entries) {
            if (entry.id().equals(entryId)) {
                return entry;
            }
        }
        return null;
    }
}
