package dev.anchorlight.stonelib.yaml;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A flat file of plugin state: points, progress, per-player contributions, which gates are open.
 *
 * <p>This is state the plugin wrote and will read back, not configuration a human edits, and not
 * anything that outlives the world it belongs to. It exists because that shape - load, guard the
 * version, read some keyed values, mark dirty, save - gets re-typed once per state file per plugin,
 * and every copy gets the same details slightly wrong: the parent directory that does not exist
 * yet, the save that throws on a full disk and takes the event down with it, the schema change that
 * silently reads a stale file as if it were current.
 *
 * <p><strong>Not the {@code storage} module.</strong> {@code storage} is the database path -
 * {@code Repository}, SQLite, MySQL, connection pools. This has no driver behind it and never will,
 * so a plugin that wants flat files and no database can exclude {@code storage} from its shaded jar
 * and still keep its state. Keep the two apart; re-adding the driver by accident is the thing this
 * separation is here to prevent.
 *
 * <p>Not thread-safe. Touch it from the main thread, as you would a {@code YamlConfiguration}.
 */
public final class YamlStore {

    /** Key holding the schema version, so a format change can be detected rather than misread. */
    public static final String VERSION_KEY = "store-version";

    private final File file;
    private final Logger logger;
    private final int version;

    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean dirty;

    /**
     * @param plugin   supplies the data folder and the logger
     * @param fileName file name inside the plugin data folder, e.g. {@code "contributions.yml"}
     * @param version  current schema version. A file written by an older version is detected on
     *                 {@link #load()} rather than being read as if it were current.
     */
    public YamlStore(Plugin plugin, String fileName, int version) {
        this(new File(plugin.getDataFolder(), fileName), plugin.getLogger(), version);
    }

    public YamlStore(File file, Logger logger, int version) {
        this.file = file;
        this.logger = logger;
        this.version = Math.max(1, version);
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Reads the file, or starts empty if it does not exist yet.
     *
     * @return the version the file was written by: the current {@link #version()} for a new or
     *         current file, or the older value when a migration is owed. Callers that have changed
     *         their schema should check this and migrate before reading anything else.
     */
    public int load() {
        yaml = new YamlConfiguration();
        dirty = false;
        if (!file.exists()) {
            return version;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        int found = yaml.getInt(VERSION_KEY, version);
        if (found != version) {
            logger.warning(file.getName() + " was written by store version " + found
                    + " but this build expects " + version
                    + ". Values that moved will read as missing until they are migrated.");
        }
        return found;
    }

    /**
     * Writes the file.
     *
     * <p>Never throws. A state file that cannot be written is serious but it is not a reason to
     * take a running event down, so the failure is logged loudly and the in-memory state is kept.
     *
     * @return true when the write succeeded
     */
    public boolean save() {
        yaml.set(VERSION_KEY, version);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            logger.severe("Could not create " + parent + " - " + file.getName() + " was not saved.");
            return false;
        }
        try {
            yaml.save(file);
            dirty = false;
            return true;
        } catch (IOException ex) {
            logger.severe("Failed to save " + file.getName() + ": " + ex.getMessage()
                    + " - state held since the last successful save would be lost on a restart.");
            return false;
        }
    }

    /** Saves only when something has changed since the last save. Cheap to call on a timer. */
    public boolean saveIfDirty() {
        return !dirty || save();
    }

    /** Marks the store as needing a save without changing anything through it. */
    public void markDirty() {
        dirty = true;
    }

    public boolean dirty() {
        return dirty;
    }

    public int version() {
        return version;
    }

    public File file() {
        return file;
    }

    /** Drops every value. The file is not removed until the next {@link #save()}. */
    public void clear() {
        yaml = new YamlConfiguration();
        dirty = true;
    }

    /** The configuration underneath, for reads this class does not wrap. */
    public YamlConfiguration raw() {
        return yaml;
    }

    // ----------------------------------------------------------------- reads

    public boolean contains(String path) {
        return yaml.contains(path);
    }

    public int getInt(String path, int fallback) {
        return yaml.getInt(path, fallback);
    }

    public long getLong(String path, long fallback) {
        return yaml.getLong(path, fallback);
    }

    public double getDouble(String path, double fallback) {
        return yaml.getDouble(path, fallback);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return yaml.getBoolean(path, fallback);
    }

    public String getString(String path, String fallback) {
        return yaml.getString(path, fallback);
    }

    public List<String> getStringList(String path) {
        return yaml.getStringList(path);
    }

    /** Child keys directly under {@code path}, or empty when the section is absent. */
    public Set<String> keys(String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        return section == null ? Set.of() : section.getKeys(false);
    }

    /**
     * Reads a {@code UUID -> int} section, the shape per-player state almost always takes.
     *
     * <p>Unparseable keys are dropped with a warning rather than throwing: a hand-edited or
     * partially written state file should cost one player their row, not the whole event.
     */
    public Map<UUID, Integer> getUuidInts(String path) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = uuid(key);
            if (uuid == null) {
                logger.warning("Skipping unparseable UUID key '" + key + "' under " + path
                        + " in " + file.getName() + ".");
                continue;
            }
            out.put(uuid, section.getInt(key));
        }
        return out;
    }

    /** Reads a {@code UUID -> list of strings} section. */
    public Map<UUID, List<String>> getUuidStringLists(String path) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid = uuid(key);
            if (uuid == null) {
                logger.warning("Skipping unparseable UUID key '" + key + "' under " + path
                        + " in " + file.getName() + ".");
                continue;
            }
            out.put(uuid, new ArrayList<>(section.getStringList(key)));
        }
        return out;
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- writes

    /** Sets a value. Passing null removes the key. */
    public void set(String path, Object value) {
        yaml.set(path, value);
        dirty = true;
    }

    /** Removes a key and everything under it. */
    public void remove(String path) {
        yaml.set(path, null);
        dirty = true;
    }

    /**
     * Replaces a whole {@code UUID -> int} section.
     *
     * <p>The section is cleared first, so a key that has gone from the map goes from the file too.
     * Writing entries over the top of a stale section is how a removed player keeps their points.
     */
    public void setUuidInts(String path, Map<UUID, Integer> values) {
        yaml.set(path, null);
        values.forEach((uuid, value) -> yaml.set(path + "." + uuid, value));
        dirty = true;
    }

    /** Replaces a whole {@code UUID -> collection of strings} section. */
    public void setUuidStringLists(String path, Map<UUID, ? extends Collection<String>> values) {
        yaml.set(path, null);
        values.forEach((uuid, value) -> yaml.set(path + "." + uuid, new ArrayList<>(value)));
        dirty = true;
    }
}
