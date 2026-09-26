package dev.anchorlight.stonelib.region;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * A tagged item that sets {@link SelectionManager} corners: left-click a block for the first
 * corner, right-click for the second. The wand never breaks or places blocks, including creative
 * instant-break. Register it as a listener.
 *
 * <pre>{@code
 * SelectionWand wand = new SelectionWand(plugin, "region_wand", selections,
 *         (player, corner, point) -> player.sendRichMessage("<yellow>Corner " + corner + " set"));
 * getServer().getPluginManager().registerEvents(wand, plugin);
 *
 * player.getInventory().addItem(wand.create(Material.BLAZE_ROD, Component.text("Region Wand")));
 * }</pre>
 */
public final class SelectionWand implements Listener {

    /** Called after a corner is set. {@code corner} is 1 or 2. */
    @FunctionalInterface
    public interface Feedback {
        void cornerSet(Player player, int corner, SelectionManager.Point point);
    }

    private final NamespacedKey key;
    private final SelectionManager selections;
    private final Feedback feedback;

    public SelectionWand(Plugin plugin, String keyName, SelectionManager selections, Feedback feedback) {
        this.key = new NamespacedKey(plugin, keyName);
        this.selections = Objects.requireNonNull(selections, "selections");
        this.feedback = feedback == null ? (player, corner, point) -> { } : feedback;
    }

    public ItemStack create(Material material, Component displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isWand(player.getInventory().getItemInMainHand())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        event.setCancelled(true); // never let the wand break or place blocks

        String world = block.getWorld().getName();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.setFirst(player.getUniqueId(), world, block.getX(), block.getY(), block.getZ());
            feedback.cornerSet(player, 1, selections.first(player.getUniqueId()).orElseThrow());
        } else {
            selections.setSecond(player.getUniqueId(), world, block.getX(), block.getY(), block.getZ());
            feedback.cornerSet(player, 2, selections.second(player.getUniqueId()).orElseThrow());
        }
    }

    /** Cancelling the interact event alone doesn't stop creative-mode instant breaking. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }
}
