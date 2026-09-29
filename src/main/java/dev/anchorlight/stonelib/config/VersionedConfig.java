package dev.anchorlight.stonelib.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * One versioned config file: migrated through {@link ConfigUpdater} on every reload, read through
 * the familiar Bukkit API, and written back through BoostedYAML so comments survive.
 *
 * <h2>The broken-file rule</h2>
 * A YAML syntax error in a hand-edited config must never cost the admin their file. The pattern
 * this replaces - {@code YamlConfiguration.loadConfiguration}, copy defaults, save - reads a file
 * that fails to parse as <em>empty</em>, then saves the defaults straight over the top of it, so
 * one stray tab erases every customised value.
 *
 * <p>Here the file is parsed on its own first. If that fails:
 * <ul>
 *   <li>nothing is written to it, by the migration or by {@link #set} - ever, until a reload finds
 *       it parseable again;</li>
 *   <li>the error is logged at {@code SEVERE}, with the parser's line and column;</li>
 *   <li>{@link #config()} serves the bundled defaults, so the plugin keeps running.</li>
 * </ul>
 *
 * <pre>{@code
 * VersionedConfig config = new VersionedConfig(this, "config.yml");
 * int duration = config.config().getInt("cooldown.duration");
 * config.set("pvp.disable-on-death", true); // comment-preserving write
 * }</pre>
 */
public final class VersionedConfig {

    private final JavaPlugin plugin;
    private final String fileName;
    private final UpdaterSettings updaterSettings;

    private FileConfiguration config = new YamlConfiguration();
    private YamlDocument document;
    private String problem;

    public VersionedConfig(JavaPlugin plugin, String fileName) {
        this(plugin, fileName, null);
    }

    /**
     * @param updaterSettings settings with the relocations for each version bump, or null to derive
     *                        them from the bundled resource
     */
    public VersionedConfig(JavaPlugin plugin, String fileName, UpdaterSettings updaterSettings) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.updaterSettings = updaterSettings;
        reload();
    }

    /**
     * Re-reads the file, migrating it first when it parses.
     *
     * @return true when the file parsed and is being served; false when the bundled defaults are
     *         being served instead
     */
    public boolean reload() {
        File file = file();
        YamlConfiguration defaults = bundledDefaults();

        if (file.exists()) {
            String parseError = parseError(file);
            if (parseError != null) {
                problem = parseError;
                document = null;
                config = defaults;
                plugin.getLogger().severe(String.format(
                        "%s could not be parsed and has NOT been modified. Running on the built-in defaults "
                                + "until it is fixed and reloaded. Problem: %s", fileName, parseError));
                return false;
            }
        }

        YamlDocument updated = ConfigUpdater.document(plugin, fileName, updaterSettings);
        if (updated == null) {
            // ConfigUpdater has already logged why; it does not save on failure either.
            problem = "update failed; see the earlier error";
            document = null;
            config = defaults;
            return false;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            problem = ex.getMessage();
            document = null;
            config = defaults;
            plugin.getLogger().log(Level.SEVERE, "Could not read " + fileName + " after updating it", ex);
            return false;
        }
        loaded.setDefaults(defaults);
        problem = null;
        document = updated;
        config = loaded;
        return true;
    }

    /** The current values. After a failed parse, the bundled defaults. */
    public FileConfiguration config() {
        return config;
    }

    /** False while the server's file is unparseable and defaults are being served. */
    public boolean isHealthy() {
        return problem == null;
    }

    /** Why the file is not being served, or null when it is. */
    public String problem() {
        return problem;
    }

    public File file() {
        return new File(plugin.getDataFolder(), fileName);
    }

    /**
     * Sets a value in memory and, when the file is healthy, writes it to disk with comments intact.
     *
     * @return true when the value was persisted; false when it only applies until the next reload,
     *         because the file on disk is unparseable and is being left alone
     */
    public boolean set(String path, Object value) {
        config.set(path, value);
        if (document == null || !isHealthy()) {
            plugin.getLogger().warning(String.format(
                    "Not saving '%s' to %s: the file could not be parsed, so it is being left untouched.",
                    path, fileName));
            return false;
        }
        document.set(path, value);
        try {
            document.save(file());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + fileName, ex);
            return false;
        }
    }

    private YamlConfiguration bundledDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream resource = plugin.getResource(fileName)) {
            if (resource != null) {
                defaults.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "The bundled " + fileName + " is itself invalid", ex);
        }
        return defaults;
    }

    /** The parser's message when {@code file} is not valid YAML, or null when it is. */
    static String parseError(File file) {
        try {
            new YamlConfiguration().loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return null;
        } catch (InvalidConfigurationException ex) {
            String message = ex.getMessage();
            return message == null ? ex.getClass().getSimpleName() : message.replace('\n', ' ').trim();
        } catch (IOException ex) {
            return "could not read the file: " + ex.getMessage();
        }
    }
}
