package dev.anchorlight.StoneLib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.anchorlight.StoneLib.CopyResources;

/**
 * Wraps a plugin's config.yml, merging any new default keys from the bundled
 * resource on every reload via {@link CopyResources#mirror}.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        CopyResources.mirror(plugin, "config.yml");
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
