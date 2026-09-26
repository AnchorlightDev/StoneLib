package dev.anchorlight.stonelib.menu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigns inventory slots to menu entries. Entries with an explicit slot keep it; the rest are
 * spread evenly and centred across the remaining free slots, in the order given.
 *
 * <pre>{@code
 * for (SlotLayout.Assignment slot : SlotLayout.assign(serverIds, configuredSlots, inventory.getSize())) {
 *     inventory.setItem(slot.slot(), iconFor(slot.entryId()));
 * }
 * }</pre>
 */
public final class SlotLayout {

    public record Assignment(String entryId, int slot) {
    }

    private SlotLayout() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /**
     * @param entryIdsInOrder every entry, in display order
     * @param explicitSlots   entries pinned to a slot; ids not in {@code entryIdsInOrder} are ignored
     * @param inventorySize   number of slots available
     * @return explicit assignments first (in entry order), then automatic ones
     * @throws IllegalArgumentException for an out-of-range or duplicate explicit slot, or when there
     *                                  are more automatic entries than free slots
     */
    public static List<Assignment> assign(List<String> entryIdsInOrder, Map<String, Integer> explicitSlots, int inventorySize) {
        Set<Integer> usedSlots = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : explicitSlots.entrySet()) {
            int slot = entry.getValue();
            if (slot < 0 || slot >= inventorySize) {
                throw new IllegalArgumentException("Slot " + slot + " for '" + entry.getKey()
                        + "' is outside the inventory (size " + inventorySize + ")");
            }
            if (!usedSlots.add(slot)) {
                throw new IllegalArgumentException("Duplicate slot " + slot + " requested by '" + entry.getKey() + "'");
            }
        }

        List<String> automatic = new ArrayList<>();
        for (String id : entryIdsInOrder) {
            if (!explicitSlots.containsKey(id)) {
                automatic.add(id);
            }
        }

        List<Integer> freeSlots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) {
            if (!usedSlots.contains(i)) {
                freeSlots.add(i);
            }
        }
        if (automatic.size() > freeSlots.size()) {
            throw new IllegalArgumentException(automatic.size() + " entries need a slot but only "
                    + freeSlots.size() + " are free (inventory size " + inventorySize + ")");
        }

        List<Assignment> result = new ArrayList<>();
        for (String id : entryIdsInOrder) {
            if (explicitSlots.containsKey(id)) {
                result.add(new Assignment(id, explicitSlots.get(id)));
            }
        }
        int[] offsets = centredOffsets(automatic.size(), freeSlots.size());
        for (int i = 0; i < automatic.size(); i++) {
            result.add(new Assignment(automatic.get(i), freeSlots.get(offsets[i])));
        }
        return result;
    }

    /**
     * Splits {@code available} positions into {@code count} equal bins and takes the centre of
     * each, so the result is symmetric, strictly increasing and always in range.
     */
    static int[] centredOffsets(int count, int available) {
        int[] offsets = new int[count];
        if (count == 0) {
            return offsets;
        }
        double binWidth = (double) available / count;
        for (int i = 0; i < count; i++) {
            offsets[i] = (int) Math.floor(binWidth * i + binWidth / 2.0);
        }
        return offsets;
    }
}
