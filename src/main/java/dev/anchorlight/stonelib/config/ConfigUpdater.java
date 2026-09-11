package dev.anchorlight.stonelib.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Brings a server's copy of a config file up to date with the version bundled in the jar, then
 * hands it back as an ordinary Bukkit {@link FileConfiguration}.
 *
 * <h2>Why this exists</h2>
 * A plain "copy the resource if the file is missing" never updates an existing file, so a plugin
 * update that adds a key silently reads a default that is not in the server's file. Merging in
 * missing keys fixes that much, but it still cannot <em>rename</em> or <em>remove</em> a key: once
 * a setting is renamed, every existing server keeps the old key forever, and the plugin has to keep
 * reading both names.
 *
 * <h2>Versioning</h2>
 * Put a version route (by convention {@code config-version}) at the top of the bundled resource and
 * increment it whenever you restructure the file. BoostedYAML then compares the server's version to
 * the bundled one and applies the relocations you declare for the versions in between, so a rename
 * is a one-line entry rather than a permanent compatibility branch.
 *
 * <pre>{@code
 * # config.yml, in the jar
 * config-version: 2
 * }</pre>
 *
 * <pre>{@code
 * // "old.path" became "new.path" in version 2
 * ConfigUpdater.update(plugin, "config.yml", UpdaterSettings.builder()
 *         .setVersioning(new BasicVersioning(ConfigUpdater.VERSION_ROUTE))
 *         .addRelocation("2", Route.fromString("old.path"), Route.fromString("new.path"))
 *         .build());
 * }</pre>
 *
 * <h2>Files with no version key</h2>
 * BoostedYAML requires the version ID to be present in the <em>defaults</em>, and throws if it is
 * not. So a resource with no version route falls back to a plain merge of missing keys — the same
 * behaviour as before. That makes adopting versioning a per-file decision rather than a flag day:
 * add {@code config-version} to a resource when you are ready, and that file starts being versioned.
 *
 * <h2>What comes back</h2>
 * The update happens on disk through BoostedYAML, which preserves comments and key order. The
 * returned object is a Bukkit {@code FileConfiguration} loaded from the updated file, so callers
 * keep the familiar {@code getInt} / {@code getConfigurationSection} API. If you need to <em>write</em>
 * back without destroying comments, use {@link #document} instead and save through BoostedYAML.
 */
public final class ConfigUpdater {

    /** The version route this codebase uses by convention. */
    public static final String VERSION_ROUTE = "config-version";

    private ConfigUpdater() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /**
     * Updates {@code fileName} against the bundled resource and returns it as a Bukkit config.
     * Versioning is applied automatically when the bundled resource carries {@link #VERSION_ROUTE}.
     */
    public static FileConfiguration update(JavaPlugin plugin, String fileName) {
        return update(plugin, fileName, null);
    }

    /**
     * As {@link #update(JavaPlugin, String)}, but with caller-supplied updater settings — use this
     * when you need to declare relocations for a version bump.
     *
     * @param updaterSettings settings to use, or null to derive them from the bundled resource
     */
    public static FileConfiguration update(JavaPlugin plugin, String fileName,
                                           UpdaterSettings updaterSettings) {
        // Run the update for its effect on disk. If it fails it has already logged, and loading
        // whatever is on disk lets the plugin start on a stale config rather than not at all.
        document(plugin, fileName, updaterSettings);
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), fileName));
    }

    /**
     * The updated file as a BoostedYAML document, for callers that need to write values back with
     * comments preserved. Returns null if the resource is missing or the update failed.
     */
    public static YamlDocument document(JavaPlugin plugin, String fileName) {
        return document(plugin, fileName, null);
    }

    public static YamlDocument document(JavaPlugin plugin, String fileName,
                                        UpdaterSettings updaterSettings) {
        if (plugin.getResource(fileName) == null) {
            plugin.getLogger().warning(String.format("Missing resource '%s'", fileName));
            return null;
        }
        File target = new File(plugin.getDataFolder(), fileName);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create " + parent);
        }
        try {
            if (!target.exists()) {
                try (InputStream initial = plugin.getResource(fileName)) {
                    Files.copy(initial, target.toPath());
                }
            }
            UpdaterSettings settings = updaterSettings != null
                    ? updaterSettings
                    : defaultUpdaterSettings(plugin, fileName);

            // Driven from streams rather than YamlDocument.create(File, ...), because that overload
            // loads via a FileInputStream it never closes - which would leak a file handle per
            // config file per reload, in every plugin that uses this.
            YamlDocument document;
            try (InputStream current = Files.newInputStream(target.toPath());
                 InputStream defaults = plugin.getResource(fileName)) {
                document = YamlDocument.create(
                        current,
                        defaults,
                        GeneralSettings.DEFAULT,
                        LoaderSettings.builder().setAutoUpdate(true).build(),
                        DumperSettings.DEFAULT,
                        settings);
            }
            document.save(target);
            return document;
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    String.format("Could not update '%s'; the server copy is left as it is", fileName), ex);
            return null;
        }
    }

    /**
     * Versioned settings when the bundled resource declares a version route, plain merge otherwise.
     * BoostedYAML throws if versioning is enabled and the defaults carry no version ID, so this
     * check is what keeps unversioned files working.
     */
    private static UpdaterSettings defaultUpdaterSettings(JavaPlugin plugin, String fileName) {
        return resourceDeclaresVersion(plugin, fileName)
                ? UpdaterSettings.builder().setVersioning(new BasicVersioning(VERSION_ROUTE)).build()
                : UpdaterSettings.builder().build();
    }

    /** True when the bundled copy of the resource has a usable {@link #VERSION_ROUTE} value. */
    public static boolean resourceDeclaresVersion(JavaPlugin plugin, String fileName) {
        try (InputStream resource = plugin.getResource(fileName)) {
            if (resource == null) {
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            return defaults.get(VERSION_ROUTE) != null;
        } catch (IOException ex) {
            return false;
        }
    }
}
