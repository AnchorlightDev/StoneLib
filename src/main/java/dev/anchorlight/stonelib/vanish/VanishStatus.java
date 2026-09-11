package dev.anchorlight.stonelib.vanish;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Detects vanished players on a Paper server without depending on any vanish plugin.
 *
 * <p>PremiumVanish, SuperVanish, EssentialsX, CMI and most other vanish plugins set a
 * {@code "vanished"} metadata value on the player, so reading it covers them all. Use it to hide
 * vanished staff from join messages, player counts and tab completion.
 */
public final class VanishStatus {

    private VanishStatus() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    public static boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
