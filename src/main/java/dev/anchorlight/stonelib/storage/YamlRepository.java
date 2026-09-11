package dev.anchorlight.stonelib.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * YAML-file-backed {@link Repository} with an in-memory cache. Load/save are
 * synchronous; wrap calls in {@code Bukkit.getScheduler().runTaskAsynchronously}
 * at the call site if off-thread I/O is desired.
 */
public class YamlRepository<K, V> implements Repository<K, V> {

    private final JavaPlugin plugin;
    private final File file;
    private final RecordCodec<V> codec;
    private final Function<V, K> keyExtractor;
    private final Function<String, K> keyParser;
    private final Map<K, V> cache = new LinkedHashMap<>();

    public YamlRepository(JavaPlugin plugin, String fileName, RecordCodec<V> codec,
                           Function<V, K> keyExtractor, Function<String, K> keyParser) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.codec = codec;
        this.keyExtractor = keyExtractor;
        this.keyParser = keyParser;
    }

    @Override
    public void load() {
        cache.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection recordSection = section.getConfigurationSection(rawKey);
            if (recordSection == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (String field : recordSection.getKeys(false)) {
                map.put(field, recordSection.get(field));
            }
            try {
                V value = codec.fromMap(map);
                cache.put(keyParser.apply(rawKey), value);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed record '" + rawKey + "'", e);
            }
        }
    }

    @Override
    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<K, V> entry : cache.entrySet()) {
            String path = "records." + entry.getKey();
            Map<String, Object> map = codec.toMap(entry.getValue());
            for (Map.Entry<String, Object> field : map.entrySet()) {
                config.set(path + "." + field.getKey(), field.getValue());
            }
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public Collection<V> all() {
        return cache.values();
    }
}
