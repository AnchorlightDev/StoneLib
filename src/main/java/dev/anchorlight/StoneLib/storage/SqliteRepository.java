package dev.anchorlight.StoneLib.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * SQLite-file-backed {@link Repository} storing one row per record in a single table.
 */
public class SqliteRepository<K, V> implements Repository<K, V> {

    private final JavaPlugin plugin;
    private final File dbFile;
    private final String tableName;
    private final RecordCodec<V> codec;
    private final Function<V, K> keyExtractor;
    private final Function<String, K> keyParser;
    private final Map<K, V> cache = new LinkedHashMap<>();

    public SqliteRepository(JavaPlugin plugin, String fileName, String tableName, RecordCodec<V> codec,
                             Function<V, K> keyExtractor, Function<String, K> keyParser) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), fileName);
        this.tableName = tableName;
        this.codec = codec;
        this.keyExtractor = keyExtractor;
        this.keyParser = keyParser;
    }

    private Connection connect() throws SQLException {
        File parent = dbFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    @Override
    public void load() {
        cache.clear();
        try (Connection conn = connect()) {
            ensureTable(conn, null);
            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName)) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String key = null;
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String column = meta.getColumnName(i);
                        if (column.equalsIgnoreCase("key")) {
                            key = rs.getString(i);
                        } else {
                            row.put(column, rs.getString(i));
                        }
                    }
                    if (key == null) {
                        continue;
                    }
                    try {
                        cache.put(keyParser.apply(key), codec.fromMap(row));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Skipping malformed row '" + key + "'", e);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load " + dbFile.getName(), e);
        }
    }

    @Override
    public void save() {
        try (Connection conn = connect()) {
            Map<String, Object> sampleRow = cache.isEmpty() ? Map.of() : codec.toMap(cache.values().iterator().next());
            ensureTable(conn, sampleRow);

            try (Statement clear = conn.createStatement()) {
                clear.execute("DELETE FROM " + tableName);
            }

            for (Map.Entry<K, V> entry : cache.entrySet()) {
                Map<String, Object> row = codec.toMap(entry.getValue());
                List<String> columns = new ArrayList<>();
                columns.add("key");
                List<Object> values = new ArrayList<>();
                values.add(entry.getKey().toString());
                for (Map.Entry<String, Object> field : row.entrySet()) {
                    columns.add(field.getKey());
                    values.add(field.getValue());
                }
                String placeholders = String.join(",", columns.stream().map(c -> "?").toArray(String[]::new));
                String sql = "INSERT INTO " + tableName + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < values.size(); i++) {
                        ps.setString(i + 1, String.valueOf(values.get(i)));
                    }
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + dbFile.getName(), e);
        }
    }

    private void ensureTable(Connection conn, Map<String, Object> sampleRow) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (key TEXT PRIMARY KEY");
        if (sampleRow != null) {
            for (String column : sampleRow.keySet()) {
                ddl.append(", ").append(column).append(" TEXT");
            }
        }
        ddl.append(")");
        try (Statement statement = conn.createStatement()) {
            statement.execute(ddl.toString());
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
    public java.util.Collection<V> all() {
        return cache.values();
    }
}
