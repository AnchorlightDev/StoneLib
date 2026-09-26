package dev.anchorlight.stonelib.shop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How many of each limited entry are left, across the whole event and everyone in it.
 *
 * <p>Limited stock is what stops a single large balance emptying the interesting half of a shop.
 * It is a property of the shop, not of a player: three war axes means three, whoever buys them.
 *
 * <p>Counts what has been <em>sold</em> rather than what remains, so that raising an entry's stock
 * in config between restarts increases what is available instead of being overwritten by a stale
 * remaining-count. Export with {@link #snapshot()} and restore with {@link #restore} to persist.
 */
public final class ShopStock {

    /** entryId -> units sold. */
    private final Map<String, Integer> sold = new ConcurrentHashMap<>();

    /** How many of an entry are left, or {@link Integer#MAX_VALUE} for an unlimited entry. */
    public int remaining(ShopEntry entry) {
        if (entry == null || !entry.limited()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, entry.stock() - sold.getOrDefault(entry.id(), 0));
    }

    public boolean soldOut(ShopEntry entry) {
        return remaining(entry) <= 0;
    }

    /**
     * Takes one unit if any is left.
     *
     * <p>Atomic against concurrent callers, though in practice purchases are main-thread. Returns
     * false when the entry is sold out, in which case nothing was taken.
     */
    public boolean tryTake(ShopEntry entry) {
        if (entry == null) {
            return false;
        }
        if (!entry.limited()) {
            return true;
        }
        // compute() so the read and the increment cannot interleave with another take.
        boolean[] taken = {false};
        sold.compute(entry.id(), (id, current) -> {
            int used = current == null ? 0 : current;
            if (used >= entry.stock()) {
                return current;
            }
            taken[0] = true;
            return used + 1;
        });
        return taken[0];
    }

    /**
     * Puts one unit back. Call this when a purchase was charged but could not be delivered.
     *
     * <p>Without it, a failed delivery silently burns stock: the buyer gets their points back and
     * the item is gone from the shop for good.
     */
    public void restoreOne(ShopEntry entry) {
        if (entry == null || !entry.limited()) {
            return;
        }
        sold.computeIfPresent(entry.id(), (id, current) -> current <= 1 ? null : current - 1);
    }

    /** How many units of an entry have been sold. */
    public int sold(String entryId) {
        return entryId == null ? 0 : sold.getOrDefault(entryId, 0);
    }

    /** Everything sold so far, for persistence. */
    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(sold);
    }

    /** Replaces the ledger, e.g. from a state file on startup. */
    public void restore(Map<String, Integer> values) {
        sold.clear();
        if (values == null) {
            return;
        }
        values.forEach((id, count) -> {
            if (id != null && count != null && count > 0) {
                sold.put(id, count);
            }
        });
    }

    /** Forgets every sale, making all limited entries fully available again. */
    public void reset() {
        sold.clear();
    }
}
