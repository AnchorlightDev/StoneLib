package dev.anchorlight.stonelib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Wraps a plugin's config.yml, bringing it up to date with the bundled resource on every reload
 * via {@link ConfigUpdater} - merging new keys, and applying versioned relocations when the
 * resource declares a {@code config-version}.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigUpdater.update(plugin, "config.yml");
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
