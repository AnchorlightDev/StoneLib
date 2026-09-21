package dev.anchorlight.stonelib.shop;

import dev.anchorlight.stonelib.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Everything a shop needs that the engine cannot know: what the currency is, what an action does,
 * what the text says, and which conditional categories are open right now.
 *
 * <p>Only the first four methods have to be implemented. The rest render the menu and have plain
 * defaults, so a shop can be stood up quickly and then dressed later.
 *
 * <p>The engine never interprets {@link ShopEntry#action()}. That keyword and its arguments belong
 * to the plugin, and {@link #deliver} is where they are read.
 */
public interface ShopContext {

    /** Why a purchase ended the way it did, for the plugin to report to the buyer. */
    enum Outcome {
        /** Bought and delivered. */
        PURCHASED,
        /** The entry has no valid price. A config slip, not a freebie. */
        UNPRICED,
        /** A plugin precondition said no. {@code detail} is the reason {@link #refuse} returned. */
        REFUSED,
        /** Nothing left of a limited entry. */
        SOLD_OUT,
        /** Not enough currency. */
        CANNOT_AFFORD,
        /** Paid for, but delivery failed. The engine has already refunded and restocked. */
        DELIVERY_FAILED
    }

    // ------------------------------------------------------------- currency

    /**
     * What this entry costs right now.
     *
     * <p>Defaults to the number in config, which is what a fixed-price shop wants. Override it
     * when a price moves at runtime - a sale, a scaling cost, a valve that discounts an item while
     * some condition holds - and the engine will quote <em>and charge</em> this, rather than the
     * configured price.
     *
     * <p>This is the only correct place for that. A plugin that computes a live price on its own
     * side can make the menu and its own messages agree with each other and still disagree with
     * the engine, which reads {@link ShopEntry#price()} for the actual charge. What the buyer is
     * shown then has nothing to do with what they are billed, and the failure is silent: an item
     * displayed at a price the buyer can afford is refused because the real charge was higher.
     *
     * <p>Return a negative number to mark the entry unbuyable, exactly as a negative config price
     * does.
     */
    default int price(ShopEntry entry) {
        return entry.price();
    }

    /** The buyer's current balance, for display. */
    int balance(Player player);

    /** Takes {@code price} from the buyer. Returns false when they cannot afford it. */
    boolean trySpend(Player player, int price);

    /** Gives {@code price} back. Called only when a charged purchase could not be delivered. */
    void refund(Player player, int price);

    // ------------------------------------------------------------- behaviour

    /**
     * Performs the entry's action.
     *
     * @return false if the action could not be carried out, in which case the engine refunds the
     *         buyer and puts any limited stock back
     */
    boolean deliver(Player buyer, ShopEntry entry);

    /**
     * Resolves a {@code visible_when} tag from config.
     *
     * <p>Called for both categories and entries. A null or blank tag never reaches this method -
     * the engine treats that as always visible.
     */
    default boolean visible(Player viewer, String visibleWhen) {
        return true;
    }

    /**
     * A last check before any currency is taken.
     *
     * <p>This is where per-player caps, cooldowns and one-per-event limits belong. Refusing here
     * means no refund is ever needed and no cooldown is burned on a purchase that did not happen.
     *
     * @return null to allow the purchase, or a reason, which is passed back through
     *         {@link #onOutcome} as the {@code detail} of a {@link Outcome#REFUSED}
     */
    default String refuse(Player buyer, ShopEntry entry) {
        return null;
    }

    /**
     * Reports how a purchase ended, so the plugin can send its own messages and sounds.
     *
     * @param detail the refusal reason for {@link Outcome#REFUSED}, otherwise null
     */
    void onOutcome(Player buyer, ShopEntry entry, Outcome outcome, String detail);

    // --------------------------------------------------------------- display

    /** Title of the category menu. */
    Component menuTitle();

    /** Title of one category page. */
    Component categoryTitle(ShopCategory category);

    /** Lore under a category icon in the menu. */
    default List<Component> categoryLore(ShopCategory category, Player viewer) {
        return List.of(plain("Click to browse", NamedTextColor.GRAY));
    }

    /**
     * Lore the engine appends beneath an entry's own configured lore.
     *
     * @param remaining units left, or {@link Integer#MAX_VALUE} for an unlimited entry
     */
    default List<Component> entryLore(ShopEntry entry, Player viewer, int remaining) {
        int cost = price(entry);
        if (cost < 0) {
            return List.of(plain("Not for sale", NamedTextColor.DARK_GRAY));
        }
        Component price = plain("Price: " + cost, NamedTextColor.GOLD);
        if (remaining == Integer.MAX_VALUE) {
            return List.of(price);
        }
        return List.of(price, remaining <= 0
                ? plain("Sold out", NamedTextColor.RED)
                : plain(remaining + " left", NamedTextColor.YELLOW));
    }

    /** The icon showing the viewer their balance. Return null to leave the slot empty. */
    default ItemStack balanceIcon(Player viewer) {
        return ItemBuilder.of(Material.SUNFLOWER)
                .name(plain("Balance: " + balance(viewer), NamedTextColor.GOLD))
                .hideDetails()
                .build();
    }

    /** The icon returning to the category menu. */
    default ItemStack backIcon() {
        return ItemBuilder.of(Material.ARROW)
                .name(plain("Back", NamedTextColor.WHITE))
                .hideDetails()
                .build();
    }

    /** The icon advancing to the next page of a long category. */
    default ItemStack nextPageIcon(int nextPageNumber) {
        return ItemBuilder.of(Material.SPECTRAL_ARROW)
                .name(plain("Page " + nextPageNumber, NamedTextColor.WHITE))
                .hideDetails()
                .build();
    }

    /** The icon returning to the previous page of a long category. */
    default ItemStack previousPageIcon(int previousPageNumber) {
        return ItemBuilder.of(Material.SPECTRAL_ARROW)
                .name(plain("Page " + previousPageNumber, NamedTextColor.WHITE))
                .hideDetails()
                .build();
    }

    /** Non-italic text, since item lore is italic by default and almost never wants to be. */
    private static Component plain(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }
}
