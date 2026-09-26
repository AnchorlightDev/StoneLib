package dev.anchorlight.stonelib.block;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeBlocksTest {

    @Test
    void onlyEmptySpaceMayBeReplaced() {
        assertFalse(SafeBlocks.wouldOverwrite(Material.AIR, Material.NETHER_PORTAL));
        assertFalse(SafeBlocks.wouldOverwrite(Material.CAVE_AIR, Material.OBSIDIAN));
        assertFalse(SafeBlocks.wouldOverwrite(Material.FIRE, Material.NETHER_PORTAL));
        assertFalse(SafeBlocks.wouldOverwrite(Material.OBSIDIAN, Material.OBSIDIAN));
        assertTrue(SafeBlocks.wouldOverwrite(Material.STONE, Material.OBSIDIAN));
        assertTrue(SafeBlocks.wouldOverwrite(Material.WATER, Material.NETHER_PORTAL));
        assertTrue(SafeBlocks.wouldOverwrite(Material.SHORT_GRASS, Material.AIR));
    }

}
