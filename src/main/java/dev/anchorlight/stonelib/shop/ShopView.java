package dev.anchorlight.stonelib.shop;

import dev.anchorlight.stonelib.ItemBuilder;
import dev.anchorlight.stonelib.menu.MenuHolder;
import dev.anchorlight.stonelib.message.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a {@link ShopCatalog} as inventory menus and turns clicks into purchases.
 *
 * <p>This is the half of a shop that is the same in every plugin: laying categories out on a menu,
 * paginating a category that does not fit on one page, routing a click back to the row it landed
 * on, and - the part most worth having in one place - running a purchase in an order that cannot
 * leave a player short.
 *
 * <p>That order is:
 *
 * <ol>
 *   <li>the entry must be priced,</li>
 *   <li>the entry and its category must be visible to this buyer,</li>
 *   <li>{@link ShopContext#refuse} must allow it,</li>
 *   <li>limited stock must be available,</li>
 *   <li><em>then</em> currency is taken,</li>
 *   <li>stock is decremented,</li>
 *   <li>and if delivery fails, both the currency and the stock are put back.</li>
 * </ol>
 *
 * <p>Every guardrail runs before anything is taken, so a refused purchase never needs a refund;
 * and the one case that can still fail after payment undoes both sides of the transaction.
 *
 * <p>Requires {@link dev.anchorlight.stonelib.menu.MenuListener} to be registered once in your
 * plugin's {@code onEnable}. Without it the menus will not respond and, worse, will not be
 * read-only.
 */
public final class ShopView {

    /** Which page of the shop an open inventory is showing. */
    public record Page(String categoryId, int page) {

        /** The category menu rather than a category page. */
        static Page menu() {
            return new Page(null, 0);
        }

        boolean isMenu() {
            return categoryId == null;
        }
    }

    private static final int MENU_SIZE = 27;
    private static final int MAX_ROWS = 6;

    private final Plugin plugin;
    private final ShopCatalog catalog;
    private final ShopContext context;
    private final ShopStock stock;

    public ShopView(Plugin plugin, ShopCatalog catalog, ShopContext context, ShopStock stock) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.context = context;
        this.stock = stock == null ? new ShopStock() : stock;
    }

    public ShopStock stock() {
        return stock;
    }

    // ------------------------------------------------------------------ open

    /** Opens the category menu. */
    public void openMenu(Player player) {
        MenuHolder.open(plugin, player, Page.menu(), MENU_SIZE, context.menuTitle(),
                this::onClick,
                inventory -> {
                    for (ShopCategory category : catalog.categories()) {
                        if (!visible(player, category.visibleWhen())) {
                            continue;
                        }
                        inventory.setItem(Math.floorMod(category.menuSlot(), MENU_SIZE),
                                icon(category.icon(), category.name(),
                                        context.categoryLore(category, player)));
                    }
                    ItemStack balance = context.balanceIcon(player);
                    if (balance != null) {
                        inventory.setItem(MENU_SIZE - 1, balance);
                    }
                });
    }

    /** Opens the first page of a category, or the menu when it is unknown or hidden. */
    public void openCategory(Player player, String categoryId) {
        openCategory(player, categoryId, 0);
    }

    /** Opens one page of a category. Pages outside the range are clamped. */
    public void openCategory(Player player, String categoryId, int page) {
        ShopCategory category = catalog.category(categoryId);
        if (category == null || !visible(player, category.visibleWhen())) {
            openMenu(player);
            return;
        }
        List<ShopEntry> visible = visibleEntries(player, category);
        int rows = rowsFor(visible.size());
        int pageSize = (rows - 1) * 9;
        int pages = Math.max(1, (int) Math.ceil(visible.size() / (double) pageSize));
        int current = Math.clamp(page, 0, pages - 1);

        MenuHolder.open(plugin, player, new Page(category.id(), current), rows * 9,
                context.categoryTitle(category),
                this::onClick,
                inventory -> populateCategory(inventory, player, visible, rows, current, pages));
    }

    private void populateCategory(Inventory inventory, Player player, List<ShopEntry> entries,
                                  int rows, int page, int pages) {
        int pageSize = (rows - 1) * 9;
        int from = page * pageSize;
        for (int slot = 0; slot < pageSize && from + slot < entries.size(); slot++) {
            inventory.setItem(slot, entryIcon(entries.get(from + slot), player));
        }
        int navRow = (rows - 1) * 9;
        inventory.setItem(navRow, context.backIcon());
        if (page > 0) {
            inventory.setItem(navRow + 3, context.previousPageIcon(page));
        }
        if (page < pages - 1) {
            inventory.setItem(navRow + 5, context.nextPageIcon(page + 2));
        }
        ItemStack balance = context.balanceIcon(player);
        if (balance != null) {
            inventory.setItem(navRow + 8, balance);
        }
    }

    /**
     * How many rows a category needs: one per nine entries plus a navigation row, capped at the
     * inventory maximum. Anything past that paginates.
     */
    private static int rowsFor(int entryCount) {
        int contentRows = Math.max(1, (int) Math.ceil(entryCount / 9.0));
        return Math.min(MAX_ROWS, contentRows + 1);
    }

    private List<ShopEntry> visibleEntries(Player player, ShopCategory category) {
        List<ShopEntry> out = new ArrayList<>();
        for (ShopEntry entry : category.entries()) {
            if (visible(player, entry.visibleWhen())) {
                out.add(entry);
            }
        }
        return out;
    }

    private boolean visible(Player player, String visibleWhen) {
        return visibleWhen == null || visibleWhen.isBlank() || context.visible(player, visibleWhen);
    }

    // ---------------------------------------------------------------- icons

    private ItemStack entryIcon(ShopEntry entry, Player viewer) {
        List<Component> lore = new ArrayList<>();
        for (String line : entry.lore()) {
            lore.add(noItalic(MiniMessages.parse(line)));
        }
        lore.addAll(context.entryLore(entry, viewer, stock.remaining(entry)));

        return ItemBuilder.of(entry.icon(), Math.min(entry.amount(), entry.icon().getMaxStackSize()))
                .name(noItalic(MiniMessages.parse(entry.name())))
                .lore(lore)
                .hideDetails()
                .build();
    }

    private ItemStack icon(org.bukkit.Material material, String name, List<Component> lore) {
        return ItemBuilder.of(material)
                .name(noItalic(MiniMessages.parse(name)))
                .lore(lore == null ? List.of() : lore)
                .hideDetails()
                .build();
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    // --------------------------------------------------------------- clicks

    private void onClick(Player player, int slot, org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder<?> holder)
                || !(holder.context() instanceof Page page)) {
            return;
        }
        if (page.isMenu()) {
            clickMenu(player, slot);
            return;
        }
        clickCategory(player, page, slot);
    }

    private void clickMenu(Player player, int slot) {
        for (ShopCategory category : catalog.categories()) {
            if (!visible(player, category.visibleWhen())) {
                continue;
            }
            if (Math.floorMod(category.menuSlot(), MENU_SIZE) == slot) {
                openCategory(player, category.id());
                return;
            }
        }
    }

    private void clickCategory(Player player, Page page, int slot) {
        ShopCategory category = catalog.category(page.categoryId());
        if (category == null) {
            openMenu(player);
            return;
        }
        List<ShopEntry> entries = visibleEntries(player, category);
        int rows = rowsFor(entries.size());
        int pageSize = (rows - 1) * 9;
        int navRow = (rows - 1) * 9;

        if (slot >= navRow) {
            if (slot == navRow) {
                openMenu(player);
            } else if (slot == navRow + 3 && page.page() > 0) {
                openCategory(player, category.id(), page.page() - 1);
            } else if (slot == navRow + 5) {
                openCategory(player, category.id(), page.page() + 1);
            }
            return;
        }
        int index = page.page() * pageSize + slot;
        if (index < entries.size()) {
            purchase(player, entries.get(index));
            // Re-render in place: a limited entry may now read "sold out", and the balance icon
            // is stale either way.
            openCategory(player, category.id(), page.page());
        }
    }

    // ------------------------------------------------------------- purchase

    /**
     * Runs a purchase, guardrails first.
     *
     * <p>Public so a plugin can sell the same entry from a command or an NPC without going through
     * the GUI, and get exactly the same ordering.
     *
     * @return true when the buyer got what they paid for
     */
    public boolean purchase(Player buyer, ShopEntry entry) {
        if (entry == null) {
            return false;
        }
        // The context's price, not the entry's: an overridden price(ShopEntry) is what the buyer
        // was quoted in the menu, so it has to be what is checked and what is taken. Reading
        // entry.price() here instead is a shop that bills a different number than it advertises.
        int cost = context.price(entry);
        if (cost < 0) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.UNPRICED, null);
            return false;
        }
        if (!visible(buyer, entry.visibleWhen())) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.REFUSED, entry.visibleWhen());
            return false;
        }
        ShopCategory category = catalog.categoryOf(entry.id());
        if (category != null && !visible(buyer, category.visibleWhen())) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.REFUSED, category.visibleWhen());
            return false;
        }
        String refusal = context.refuse(buyer, entry);
        if (refusal != null) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.REFUSED, refusal);
            return false;
        }
        if (stock.soldOut(entry)) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.SOLD_OUT, null);
            return false;
        }
        if (!context.trySpend(buyer, cost)) {
            context.onOutcome(buyer, entry, ShopContext.Outcome.CANNOT_AFFORD, null);
            return false;
        }
        // Taken after payment so that a sold-out race refunds rather than charging for nothing.
        if (!stock.tryTake(entry)) {
            context.refund(buyer, cost);
            context.onOutcome(buyer, entry, ShopContext.Outcome.SOLD_OUT, null);
            return false;
        }
        if (!context.deliver(buyer, entry)) {
            context.refund(buyer, cost);
            stock.restoreOne(entry);
            context.onOutcome(buyer, entry, ShopContext.Outcome.DELIVERY_FAILED, null);
            return false;
        }
        context.onOutcome(buyer, entry, ShopContext.Outcome.PURCHASED, null);
        return true;
    }
}
