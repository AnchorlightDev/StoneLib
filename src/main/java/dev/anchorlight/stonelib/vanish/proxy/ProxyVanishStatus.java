package dev.anchorlight.stonelib.vanish.proxy;

import com.velocitypowered.api.proxy.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects vanished players on a Velocity proxy through PremiumVanish's proxy API, looked up
 * reflectively so the plugin runs whether or not PremiumVanish is installed. Reports everyone as
 * visible when it isn't.
 */
public final class ProxyVanishStatus {

    private static final String API_CLASS = "de.myzelyam.api.vanish.VelocityVanishAPI";

    private ProxyVanishStatus() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    public static boolean isVanished(Player player) {
        return player != null && isVanished(player.getUniqueId());
    }

    public static boolean isVanished(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        try {
            // The UUID overload avoids the Velocity/Bukkit Player type mismatch in PremiumVanish's API.
            Class<?> api = Class.forName(API_CLASS);
            Method isInvisible = api.getMethod("isInvisible", UUID.class);
            return Boolean.TRUE.equals(isInvisible.invoke(null, playerId));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
