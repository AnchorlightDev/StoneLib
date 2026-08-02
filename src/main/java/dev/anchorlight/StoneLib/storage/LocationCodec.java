package dev.anchorlight.StoneLib.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Canonical (de)serialization for {@link Location}: "world:x:y:z:yaw:pitch".
 */
public final class LocationCodec {

    private LocationCodec() {
    }

    public static String serialize(Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        return worldName + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ()
                + ":" + location.getYaw() + ":" + location.getPitch();
    }

    public static Location deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        String[] parts = serialized.split(":");
        if (parts.length < 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
