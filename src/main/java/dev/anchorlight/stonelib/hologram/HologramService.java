package dev.anchorlight.StoneLib.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages floating text using native Paper {@link TextDisplay} entities, tagged
 * with a NamespacedKey so managed entities are identifiable across restarts.
 */
public class HologramService {

    private static final String HOLOGRAM_ID_KEY = "stonelib-hologram-id";

    private final JavaPlugin plugin;
    private final NamespacedKey idKey;

    public HologramService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, HOLOGRAM_ID_KEY);
    }

    public void create(UUID id, Location location, List<Component> lines) {
        remove(id);
        try {
            location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
                entity.setBillboard(Display.Billboard.CENTER);
                applyLines(entity, lines);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create hologram " + id, e);
        }
    }

    public void update(UUID id, List<Component> lines) {
        find(id).ifPresent(entity -> applyLines(entity, lines));
    }

    public void remove(UUID id) {
        find(id).ifPresent(entity -> {
            try {
                entity.remove();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove hologram " + id, e);
            }
        });
    }

    public boolean has(UUID id) {
        return find(id).isPresent();
    }

    private void applyLines(TextDisplay entity, List<Component> lines) {
        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            text = text.append(lines.get(i));
            if (i < lines.size() - 1) {
                text = text.append(Component.newline());
            }
        }
        entity.text(text);
    }

    private java.util.Optional<TextDisplay> find(UUID id) {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (TextDisplay entity : world.getEntitiesByClass(TextDisplay.class)) {
                String storedId = entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
                if (id.toString().equals(storedId)) {
                    return java.util.Optional.of(entity);
                }
            }
        }
        return java.util.Optional.empty();
    }
}
