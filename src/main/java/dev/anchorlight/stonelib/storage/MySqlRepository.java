package dev.anchorlight.stonelib.storage;

import dev.anchorlight.stonelib.storage.sql.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A MySQL-backed {@link Repository} storing one row per record, for state shared by several
 * servers behind a proxy.
 *
 * <p>It differs from {@link SqliteRepository} in the two ways that matter once more than one
 * backend is writing:</p>
 *
 * <ul>
 *   <li>{@link #save()} upserts only the records changed since the last save and deletes only the
 *       ones removed. It never clears the table, so one server saving cannot discard another
 *       server's writes.</li>
 *   <li>{@link #reload(Object)} re-reads a single key, which is what a cache-invalidation message
 *       from the proxy needs — a full {@link #load()} on every change would not scale.</li>
 * </ul>
 *
 * <p>Every method that touches the database blocks and must be called off the server thread.</p>
 */
public class MySqlRepository<K, V> implements Repository<K, V> {

    private final ConnectionPool pool;
    private final Logger logger;
    private final String tableName;
    private final RecordCodec<V> codec;
    private final KeyParser<K> keyParser;
    private final List<String> columns;

    private final Map<K, V> cache = new ConcurrentHashMap<>();
    private final Set<K> dirty = new LinkedHashSet<>();
    private final Set<K> removed = new LinkedHashSet<>();

    /**
     * @param columns the value columns this repository stores, matching the keys the codec
     *                produces. Declared up front rather than sampled from a record, so the table
     *                can be created before any record exists.
     */
    public MySqlRepository(ConnectionPool pool, Logger logger, String tableName, RecordCodec<V> codec,
                           KeyParser<K> keyParser, List<String> columns) {
        this.pool = pool;
        this.logger = logger;
        this.tableName = validateIdentifier(tableName);
        this.codec = codec;
        this.keyParser = keyParser;
        this.columns = List.copyOf(columns.stream().map(MySqlRepository::validateIdentifier).toList());
    }

    /**
     * Creates the table if it does not exist and adds any column this build knows about that the
     * server's table does not. Call once on enable, before {@link #load()}.
     *
     * <p>This covers additive changes only. Anything else — renaming a column, changing a type,
     * backfilling — belongs in a
     * {@link dev.anchorlight.stonelib.storage.sql.SchemaMigrator} migration.</p>
     */
    public void createTable() throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName
                + " (`key` VARCHAR(191) NOT NULL PRIMARY KEY");
        for (String column : columns) {
            ddl.append(", `").append(column).append("` TEXT");
        }
        ddl.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        pool.update(ddl.toString());

        Set<String> existing = new LinkedHashSet<>();
        try (Connection conn = pool.connection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SHOW COLUMNS FROM " + tableName)) {
            while (rs.next()) {
                existing.add(rs.getString("Field").toLowerCase(Locale.ROOT));
            }
        }
        for (String column : columns) {
            if (!existing.contains(column.toLowerCase(Locale.ROOT))) {
                pool.update("ALTER TABLE " + tableName + " ADD COLUMN `" + column + "` TEXT");
            }
        }
    }

    @Override
    public void load() {
        try (Connection conn = pool.connection();
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName)) {
            Map<K, V> loaded = new LinkedHashMap<>();
            while (rs.next()) {
                Row row = readRow(rs);
                if (row == null) {
                    continue;
                }
                try {
                    loaded.put(keyParser.parse(row.key()), codec.fromMap(row.values()));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Skipping malformed row '" + row.key() + "' in " + tableName, e);
                }
            }
            cache.clear();
            cache.putAll(loaded);
            dirty.clear();
            removed.clear();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load " + tableName, e);
        }
    }

    /**
     * Re-reads one key from the database into the cache, dropping it from the cache if the row is
     * gone. This is the cache-invalidation entry point for proxy messages.
     *
     * @return the refreshed value, or null if there is no such row
     */
    public V reload(K key) {
        try (Connection conn = pool.connection();
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT * FROM " + tableName + " WHERE `key` = ?")) {
            statement.setString(1, key.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    cache.remove(key);
                    return null;
                }
                Row row = readRow(rs);
                if (row == null) {
                    return null;
                }
                V value = codec.fromMap(row.values());
                cache.put(key, value);
                // A value we just took from the database is not a local change to write back.
                synchronized (this) {
                    dirty.remove(key);
                }
                return value;
            }
        } catch (SQLException | RuntimeException e) {
            logger.log(Level.WARNING, "Failed to reload '" + key + "' from " + tableName, e);
            return cache.get(key);
        }
    }

    @Override
    public void save() {
        List<K> toWrite;
        List<K> toDelete;
        synchronized (this) {
            toWrite = new ArrayList<>(dirty);
            toDelete = new ArrayList<>(removed);
            dirty.clear();
            removed.clear();
        }
        if (toWrite.isEmpty() && toDelete.isEmpty()) {
            return;
        }

        try {
            pool.transaction(conn -> {
                if (!toDelete.isEmpty()) {
                    try (PreparedStatement statement = conn.prepareStatement(
                            "DELETE FROM " + tableName + " WHERE `key` = ?")) {
                        for (K key : toDelete) {
                            statement.setString(1, key.toString());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
                if (!toWrite.isEmpty()) {
                    try (PreparedStatement statement = conn.prepareStatement(upsertSql())) {
                        for (K key : toWrite) {
                            V value = cache.get(key);
                            if (value == null) {
                                continue;
                            }
                            Map<String, Object> row = codec.toMap(value);
                            statement.setString(1, key.toString());
                            for (int i = 0; i < columns.size(); i++) {
                                Object field = row.get(columns.get(i));
                                statement.setString(i + 2, field == null ? null : String.valueOf(field));
                            }
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                }
            });
        } catch (SQLException e) {
            // Put the work back so the next save retries it rather than losing the change.
            synchronized (this) {
                dirty.addAll(toWrite);
                removed.addAll(toDelete);
            }
            logger.log(Level.SEVERE, "Failed to save " + tableName, e);
        }
    }

    /** Writes a single record straight through, for a change that must not wait for the next save. */
    public void saveNow(K key) throws SQLException {
        V value = cache.get(key);
        if (value == null) {
            pool.update("DELETE FROM " + tableName + " WHERE `key` = ?", key.toString());
        } else {
            Map<String, Object> row = codec.toMap(value);
            Object[] params = new Object[columns.size() + 1];
            params[0] = key.toString();
            for (int i = 0; i < columns.size(); i++) {
                Object field = row.get(columns.get(i));
                params[i + 1] = field == null ? null : String.valueOf(field);
            }
            pool.update(upsertSql(), params);
        }
        synchronized (this) {
            dirty.remove(key);
            removed.remove(key);
        }
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
        synchronized (this) {
            removed.remove(key);
            dirty.add(key);
        }
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
        synchronized (this) {
            dirty.remove(key);
            removed.add(key);
        }
    }

    @Override
    public Collection<V> all() {
        return cache.values();
    }

    /** Drops a key from the local cache without touching the database. */
    public void invalidate(K key) {
        cache.remove(key);
        synchronized (this) {
            dirty.remove(key);
        }
    }

    /** Whether there are local changes not yet written. */
    public synchronized boolean hasPendingWrites() {
        return !dirty.isEmpty() || !removed.isEmpty();
    }

    private String upsertSql() {
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (`key`");
        for (String column : columns) {
            sql.append(", `").append(column).append("`");
        }
        sql.append(") VALUES (?");
        sql.append(", ?".repeat(columns.size()));
        sql.append(") ON DUPLICATE KEY UPDATE ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(columns.get(i)).append("` = VALUES(`").append(columns.get(i)).append("`)");
        }
        return sql.toString();
    }

    private Row readRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> values = new LinkedHashMap<>();
        String key = null;
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String column = meta.getColumnLabel(i);
            if (column.equalsIgnoreCase("key")) {
                key = rs.getString(i);
            } else {
                values.put(column, rs.getString(i));
            }
        }
        return key == null ? null : new Row(key, values);
    }

    private record Row(String key, Map<String, Object> values) {}

    /**
     * Turns the stored string form of a key back into the key type. Separate from
     * {@link java.util.function.Function} only so it can be named at call sites.
     */
    @FunctionalInterface
    public interface KeyParser<K> {
        K parse(String stored);
    }

    /**
     * Table and column names are interpolated into SQL rather than bound, so they are restricted
     * to plain identifiers.
     */
    private static String validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Not a valid SQL identifier: " + identifier);
        }
        return identifier;
    }
}
