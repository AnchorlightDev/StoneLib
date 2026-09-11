package dev.anchorlight.StoneLib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads several YAML files from the plugin data folder, bringing each one up to date with its
 * bundled default on every reload via {@link ConfigUpdater} - so every file in the set gets both
 * new-key merging and, where the resource declares a {@code config-version}, versioned migration.
 *
 * <p>{@link ConfigManager} covers the common case of a single {@code config.yml}. A plugin with a
 * lot of tunable surface is better off splitting it - separate files for balance numbers, loot
 * tables, messages and so on - so the person doing a balance pass is not scrolling through one
 * enormous file. This manager is that case.
 *
 * <pre>{@code
 * MultiConfigManager configs = new MultiConfigManager(this,
 *         List.of("config.yml", "loot.yml", "arenas.yml"));
 * int size = configs.get("arenas.yml").getInt("default_size");
 * }</pre>
 */
public class MultiConfigManager {

    private final JavaPlugin plugin;
    private final List<String> fileNames;
    private final Map<String, FileConfiguration> loaded = new LinkedHashMap<>();

    /**
     * @param fileNames names of the YAML files, each of which must exist as a bundled resource
     */
    public MultiConfigManager(JavaPlugin plugin, List<String> fileNames) {
        this.plugin = plugin;
        this.fileNames = List.copyOf(fileNames);
        reload();
    }

    /** Re-reads every file, updating each against its bundled default first. */
    public void reload() {
        loaded.clear();
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create data folder " + dataFolder);
        }
        for (String name : fileNames) {
            loaded.put(name, ConfigUpdater.update(plugin, name));
        }
    }

    /**
     * The loaded configuration for {@code fileName}.
     *
     * @throws IllegalArgumentException if the file was not one of the names this manager was built with
     */
    public FileConfiguration get(String fileName) {
        FileConfiguration configuration = loaded.get(fileName);
        if (configuration == null) {
            throw new IllegalArgumentException("Config " + fileName + " is not managed by this MultiConfigManager");
        }
        return configuration;
    }

    /** The file names this manager handles, in the order they were declared. */
    public List<String> fileNames() {
        return fileNames;
    }
}
