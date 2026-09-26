package dev.anchorlight.stonelib.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The purchase ordering.
 *
 * <p>This is the half of a shop that costs real money when it is wrong, and the reason the logic
 * sits in the library rather than in each plugin. The contract being pinned down:
 *
 * <ol>
 *   <li>every guardrail runs <em>before</em> any currency is taken, so a refused purchase never
 *       needs a refund;</li>
 *   <li>the one case that can still fail after payment - delivery - undoes both the payment and
 *       the stock.</li>
 * </ol>
 */
class ShopViewTest {

    private ServerMock server;
    private RecordingContext context;
    private ShopCatalog catalog;
    private ShopStock stock;
    private ShopView view;
    private Player buyer;

    /** A context that records what happened instead of touching a real economy. */
    private static final class RecordingContext implements ShopContext {

        int balance = 1000;
        boolean deliverSucceeds = true;
        String refusal;
        boolean visible = true;
        /** A runtime price that overrides config, or null to use the entry's own. */
        Integer overriddenPrice;

        final List<String> spent = new ArrayList<>();
        final List<String> refunded = new ArrayList<>();
        final List<String> delivered = new ArrayList<>();
        final List<Outcome> outcomes = new ArrayList<>();
        String lastDetail;

        @Override
        public int price(ShopEntry entry) {
            return overriddenPrice == null ? entry.price() : overriddenPrice;
        }

        @Override
        public int balance(Player player) {
            return balance;
        }

        @Override
        public boolean trySpend(Player player, int price) {
            if (balance < price) {
                return false;
            }
            balance -= price;
            spent.add(String.valueOf(price));
            return true;
        }

        @Override
        public void refund(Player player, int price) {
            balance += price;
            refunded.add(String.valueOf(price));
        }

        @Override
        public boolean deliver(Player buyer, ShopEntry entry) {
            if (!deliverSucceeds) {
                return false;
            }
            delivered.add(entry.id());
            return true;
        }

        @Override
        public boolean visible(Player viewer, String visibleWhen) {
            return visible;
        }

        @Override
        public String refuse(Player buyer, ShopEntry entry) {
            return refusal;
        }

        @Override
        public void onOutcome(Player buyer, ShopEntry entry, Outcome outcome, String detail) {
            outcomes.add(outcome);
            lastDetail = detail;
        }

        @Override
        public Component menuTitle() {
            return Component.text("Shop");
        }

        @Override
        public Component categoryTitle(ShopCategory category) {
            return Component.text(category.id());
        }

        Outcome lastOutcome() {
            return outcomes.isEmpty() ? null : outcomes.get(outcomes.size() - 1);
        }
    }

    private static ShopEntry entry(String id, int price, int stockCount, String visibleWhen) {
        return new ShopEntry(id, id, List.of(), Material.STONE, 1, price, "ITEM",
                Map.of(), stockCount, visibleWhen);
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin("ShopTest");
        buyer = server.addPlayer();

        context = new RecordingContext();
        catalog = new ShopCatalog(java.util.logging.Logger.getLogger("ShopViewTest"));
        stock = new ShopStock();
        view = new ShopView(plugin, catalog, context, stock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------- the happy path

    @Test
    void buyingTakesTheCurrencyAndDelivers() {
        assertTrue(view.purchase(buyer, entry("iron", 40, ShopEntry.UNLIMITED, null)));

        assertEquals(960, context.balance);
        assertEquals(List.of("iron"), context.delivered);
        assertEquals(ShopContext.Outcome.PURCHASED, context.lastOutcome());
        assertTrue(context.refunded.isEmpty());
    }

    // --------------------------------------- guardrails run BEFORE any payment

    @Test
    void anUnpricedEntryTakesNothing() {
        assertFalse(view.purchase(buyer, entry("broken", -1, ShopEntry.UNLIMITED, null)));

        assertEquals(1000, context.balance);
        assertEquals(ShopContext.Outcome.UNPRICED, context.lastOutcome());
        assertTrue(context.spent.isEmpty(), "an unpriced entry must never reach the currency");
    }

    @Test
    void aRefusedPurchaseTakesNothingAndNeedsNoRefund() {
        context.refusal = "on cooldown";

        assertFalse(view.purchase(buyer, entry("gadget", 60, ShopEntry.UNLIMITED, null)));

        assertEquals(1000, context.balance);
        assertEquals(ShopContext.Outcome.REFUSED, context.lastOutcome());
        assertEquals("on cooldown", context.lastDetail);
        assertTrue(context.spent.isEmpty());
        assertTrue(context.refunded.isEmpty(), "nothing was taken, so nothing should be given back");
    }

    @Test
    void aHiddenEntryCannotBeBoughtEvenIfSomethingCallsPurchaseDirectly() {
        context.visible = false;

        assertFalse(view.purchase(buyer, entry("late_gear", 400, ShopEntry.UNLIMITED, "late_game")));

        assertEquals(1000, context.balance);
        assertEquals(ShopContext.Outcome.REFUSED, context.lastOutcome());
        assertTrue(context.delivered.isEmpty());
    }

    @Test
    void aSoldOutEntryTakesNothing() {
        ShopEntry axe = entry("war_axe", 400, 1, null);
        assertTrue(view.purchase(buyer, axe));
        context.outcomes.clear();

        assertFalse(view.purchase(buyer, axe));

        assertEquals(600, context.balance, "the second attempt must not charge");
        assertEquals(ShopContext.Outcome.SOLD_OUT, context.lastOutcome());
    }

    @Test
    void notEnoughCurrencyTakesNothingAndDoesNotConsumeStock() {
        context.balance = 10;
        ShopEntry axe = entry("war_axe", 400, 3, null);

        assertFalse(view.purchase(buyer, axe));

        assertEquals(10, context.balance);
        assertEquals(ShopContext.Outcome.CANNOT_AFFORD, context.lastOutcome());
        assertEquals(3, stock.remaining(axe), "a failed purchase must not burn stock");
    }

    // ------------------------------------------- the one failure after payment

    /**
     * Delivery is the only step that can fail once the buyer has paid. Both sides of the
     * transaction have to come back, or a failed delivery quietly costs the player their points
     * <em>and</em> removes the item from the shop for everyone.
     */
    @Test
    void aFailedDeliveryRefundsTheCurrencyAndPutsTheStockBack() {
        context.deliverSucceeds = false;
        ShopEntry axe = entry("war_axe", 400, 3, null);

        assertFalse(view.purchase(buyer, axe));

        assertEquals(1000, context.balance, "the buyer must be made whole");
        assertEquals(List.of("400"), context.refunded);
        assertEquals(3, stock.remaining(axe), "stock must go back on the shelf");
        assertEquals(ShopContext.Outcome.DELIVERY_FAILED, context.lastOutcome());
    }

    @Test
    void aFailedDeliveryOnAnUnlimitedEntryStillRefunds() {
        context.deliverSucceeds = false;

        assertFalse(view.purchase(buyer, entry("iron", 40, ShopEntry.UNLIMITED, null)));

        assertEquals(1000, context.balance);
        assertEquals(ShopContext.Outcome.DELIVERY_FAILED, context.lastOutcome());
    }

    // --------------------------------------------------------- limited stock

    @Test
    void limitedStockRunsOutAcrossEveryoneNotPerPlayer() {
        ShopEntry axe = entry("war_axe", 100, 2, null);
        Player second = server.addPlayer();

        assertTrue(view.purchase(buyer, axe));
        assertTrue(view.purchase(second, axe));
        assertFalse(view.purchase(buyer, axe),
                "three exist means three, whoever buys them");

        assertEquals(2, stock.sold("war_axe"));
    }

    @Test
    void stockSurvivesBeingSnapshotAndRestored() {
        ShopEntry axe = entry("war_axe", 100, 3, null);
        view.purchase(buyer, axe);

        ShopStock restored = new ShopStock();
        restored.restore(stock.snapshot());

        assertEquals(2, restored.remaining(axe));
    }

    @Test
    void nullEntriesAreRejectedRatherThanThrowing() {
        assertFalse(view.purchase(buyer, null));
        assertEquals(1000, context.balance);
    }

    // ------------------------------------------------ the catalogue behind it

    @Test
    void aCategoryHiddenFromTheBuyerBlocksItsEntries() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("categories.late.display_name", "Late");
        config.set("categories.late.visible_when", "late_game");
        config.set("categories.late.items", List.of(
                Map.of("id", "late_gear", "name", "Late Gear", "material", "DIAMOND", "price", 100)));
        catalog.load(config.getConfigurationSection("categories"));

        ShopEntry gear = catalog.entry("late_gear");
        context.visible = false;

        assertFalse(view.purchase(buyer, gear),
                "an entry in a hidden category must not be buyable");
        assertEquals(1000, context.balance);
    }

    @Test
    void anEntryInAVisibleCategoryIsBuyable() {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("categories.supplies.display_name", "Supplies");
        config.set("categories.supplies.items", List.of(
                Map.of("id", "iron", "name", "Iron", "material", "IRON_INGOT", "price", 40)));
        catalog.load(config.getConfigurationSection("categories"));

        assertTrue(view.purchase(buyer, catalog.entry("iron")));
        assertEquals(960, context.balance);
    }

    // ------------------------------------------------- the runtime price hook

    /**
     * A price that moves at runtime - a sale, a scaling cost, an anti-stall valve - belongs to the
     * plugin, but the engine has to bill it. When these two disagree the shop quotes one number
     * and charges another, and the buyer is refused an item the menu said they could afford. That
     * is precisely what happened downstream: an item discounted 400 -> 200, a buyer holding 309,
     * and a refusal reading "costs 200. You have 309."
     */
    @Test
    void anOverriddenPriceIsWhatGetsCharged() {
        context.overriddenPrice = 200;

        assertTrue(view.purchase(buyer, entry("shard", 400, ShopEntry.UNLIMITED, null)));

        assertEquals(800, context.balance, "the quoted price is the price taken");
        assertEquals(List.of("200"), context.spent);
    }

    @Test
    void anOverriddenPriceIsWhatAffordabilityIsCheckedAgainst() {
        context.overriddenPrice = 200;
        context.balance = 309;

        assertTrue(view.purchase(buyer, entry("shard", 400, ShopEntry.UNLIMITED, null)),
                "309 must buy an item discounted to 200, whatever config says it costs");

        assertEquals(109, context.balance);
        assertEquals(ShopContext.Outcome.PURCHASED, context.lastOutcome());
    }

    @Test
    void aFailedDeliveryRefundsTheOverriddenPriceNotTheConfigOne() {
        context.overriddenPrice = 200;
        context.deliverSucceeds = false;

        assertFalse(view.purchase(buyer, entry("shard", 400, ShopEntry.UNLIMITED, null)));

        assertEquals(1000, context.balance, "refunding the config price would mint currency");
        assertEquals(List.of("200"), context.refunded);
        assertEquals(ShopContext.Outcome.DELIVERY_FAILED, context.lastOutcome());
    }

    @Test
    void anOverrideCanMakeAConfigPricedEntryUnbuyable() {
        context.overriddenPrice = -1;

        assertFalse(view.purchase(buyer, entry("seasonal", 40, ShopEntry.UNLIMITED, null)));

        assertEquals(1000, context.balance);
        assertEquals(ShopContext.Outcome.UNPRICED, context.lastOutcome());
    }

    @Test
    void withoutAnOverrideTheConfigPriceStillRules() {
        assertTrue(view.purchase(buyer, entry("iron", 40, ShopEntry.UNLIMITED, null)));

        assertEquals(960, context.balance);
        assertEquals(List.of("40"), context.spent);
    }
}
