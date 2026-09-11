package dev.anchorlight.stonelib.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Makes every {@link MenuHolder} inventory read-only and routes clicks to its handler.
 *
 * <p>Register this once per plugin, in onEnable. It covers every menu the plugin opens through a
 * MenuHolder, so no individual menu needs its own listener and none of them can be the one that
 * forgot to cancel the event and let a display item be carried out.
 *
 * <pre>{@code
 * getServer().getPluginManager().registerEvents(new MenuListener(), this);
 * }</pre>
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder<?> holder)) {
            return;
        }
        // Cancel first, unconditionally: shift-clicks and hotbar swaps from the player's own
        // inventory can still move items into the menu, so this must not depend on where the
        // click landed.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        MenuHolder.ClickHandler handler = holder.clickHandler();
        if (handler != null) {
            handler.onClick(player, event.getSlot(), event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder<?>) {
            event.setCancelled(true);
        }
    }
}
