package dev.anchorlight.StoneLib.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/**
 * A typed marker for a plugin-owned inventory: it identifies the menu as yours, carries whatever
 * context the menu is about, and holds the click handler.
 *
 * <p>The alternative - matching on the inventory title - breaks the moment two menus share a title
 * or a title is translated, and it gives you nothing to hang the menu's context off. A holder gives
 * you both, and {@link MenuListener} then cancels every click in one place so a menu can never leak
 * items into the world.
 *
 * <p>Note the {@code inventory} field: Bukkit calls {@link #getInventory()} on a holder in several
 * paths, so the inventory is written back into the holder at creation rather than left to throw.
 *
 * <pre>{@code
 * // once, in onEnable
 * getServer().getPluginManager().registerEvents(new MenuListener(), this);
 *
 * // opening a menu
 * MenuHolder<Arena> holder = MenuHolder.open(this, player, arena, 27,
 *         MiniMessages.parse("<dark_gray>Arena shop"),
 *         (viewer, slot, event) -> buy(viewer, event.getInventory().getItem(slot)),
 *         inventory -> inventory.setItem(13, icon));
 * }</pre>
 *
 * @param <T> the context this menu is about - an arena, a shop category, a page number
 */
public class MenuHolder<T> implements InventoryHolder {

    /** Handles a click on a slot in a menu. The click is already cancelled when this runs. */
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(Player player, int slot, InventoryClickEvent event);
    }

    /** Populates a freshly created inventory. */
    @FunctionalInterface
    public interface Populator {
        void populate(Inventory inventory);
    }

    private final T context;
    private final ClickHandler clickHandler;
    private Inventory inventory;

    public MenuHolder(T context, ClickHandler clickHandler) {
        this.context = context;
        this.clickHandler = clickHandler;
    }

    /** The context this menu was opened for. May be null for menus that need none. */
    public T context() {
        return context;
    }

    public ClickHandler clickHandler() {
        return clickHandler;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Creates the inventory, populates it, wires it to a new holder and opens it for the player.
     *
     * @param size must be a multiple of 9, between 9 and 54
     */
    public static <T> MenuHolder<T> open(Plugin plugin, Player player, T context, int size,
                                         Component title, ClickHandler clickHandler, Populator populator) {
        MenuHolder<T> holder = new MenuHolder<>(context, clickHandler);
        Inventory inventory = plugin.getServer().createInventory(holder, size, title);
        holder.setInventory(inventory);
        if (populator != null) {
            populator.populate(inventory);
        }
        player.openInventory(inventory);
        return holder;
    }
}
