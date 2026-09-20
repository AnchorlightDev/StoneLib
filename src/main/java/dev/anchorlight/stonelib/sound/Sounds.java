package dev.anchorlight.stonelib.sound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Sound cues named by their vanilla key ({@code entity.wither.spawn}), so config can drive them.
 *
 * <p>An unknown key is ignored rather than thrown. A typo in a config file should cost one sound
 * effect, not take an event down mid-run.
 */
public final class Sounds {

    private Sounds() {
    }

    /** Plays a sound to every online player, at their own position. */
    public static void toAll(String soundKey, float volume, float pitch) {
        Sound sound = resolve(soundKey);
        if (sound == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    /** Plays a sound to one player, at their own position, so it is never directional. */
    public static void toPlayer(Player player, String soundKey, float volume, float pitch) {
        Sound sound = resolve(soundKey);
        if (sound == null || player == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }

    /** Plays a sound in the world, audible to anyone in range. */
    public static void atLocation(String soundKey, Location location, float volume, float pitch) {
        Sound sound = resolve(soundKey);
        if (sound == null || location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    /** Plays a sound to every player within {@code radius} blocks of a point. */
    public static void withinRadius(String soundKey, Location centre, double radius,
                                    float volume, float pitch) {
        Sound sound = resolve(soundKey);
        if (sound == null || centre == null || centre.getWorld() == null) {
            return;
        }
        double radiusSquared = radius * radius;
        for (Player player : centre.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(centre) <= radiusSquared) {
                player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
            }
        }
    }

    /**
     * Looks up a sound by key, accepting {@code entity.wither.spawn} or a fully namespaced key.
     *
     * @return the sound, or null when the key is blank, malformed or unknown
     */
    public static Sound resolve(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        NamespacedKey namespaced = NamespacedKey.fromString(key.trim().toLowerCase(Locale.ROOT));
        return namespaced == null ? null : Registry.SOUNDS.get(namespaced);
    }

    /** Whether a key names a real sound. For validating config on startup. */
    public static boolean exists(String key) {
        return resolve(key) != null;
    }
}
