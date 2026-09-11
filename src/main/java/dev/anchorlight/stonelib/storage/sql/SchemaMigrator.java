package dev.anchorlight.StoneLib.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies ordered, one-time schema migrations to a MySQL database.
 *
 * <p>Applied migrations are recorded in a {@code stonelib_schema_version} table keyed by owner, so
 * several plugins can share one database without colliding and a migration that has already run on
 * a server is never re-applied.</p>
 *
 * <pre>
 * new SchemaMigrator(pool, "EdenEffects")
 *         .migration(1, "CREATE TABLE selections (...)")
 *         .migration(2, "ALTER TABLE selections ADD COLUMN glowing BOOLEAN NOT NULL DEFAULT FALSE")
 *         .migrate();
 * </pre>
 *
 * <p>A shipped migration is never edited — a server that already ran version 2 will not run it
 * again. Correct a mistake by adding version 3.</p>
 */
public final class SchemaMigrator {

    private static final String VERSION_TABLE = "stonelib_schema_version";

    private final ConnectionPool pool;
    private final String owner;
    private final Map<Integer, List<String>> migrations = new LinkedHashMap<>();

    public SchemaMigrator(ConnectionPool pool, String owner) {
        this.pool = pool;
        this.owner = owner;
    }

    /** Registers the statements making up one schema version. */
    public SchemaMigrator migration(int version, String... statements) {
        if (version <= 0) {
            throw new IllegalArgumentException("Migration versions start at 1: " + version);
        }
        if (migrations.putIfAbsent(version, List.of(statements)) != null) {
            throw new IllegalArgumentException("Duplicate migration version " + version + " for " + owner);
        }
        return this;
    }

    /**
     * Applies every registered migration newer than the version recorded for this owner.
     *
     * <p>Blocking; call it off the server thread. Each version is applied in its own transaction,
     * so a failure part-way leaves earlier versions applied and recorded, and the failed one rolled
     * back entirely.</p>
     *
     * @return the number of migrations applied
     */
    public int migrate() throws SQLException {
        ensureVersionTable();
        int current = currentVersion();
        List<Integer> pending = new ArrayList<>(migrations.keySet());
        pending.sort(Integer::compareTo);

        int applied = 0;
        for (int version : pending) {
            if (version <= current) {
                continue;
            }
            List<String> statements = migrations.get(version);
            pool.transaction(conn -> {
                try (Statement statement = conn.createStatement()) {
                    for (String sql : statements) {
                        statement.execute(sql);
                    }
                }
                record(conn, version);
            });
            applied++;
        }
        return applied;
    }

    /** The highest schema version recorded for this owner, or 0 if none has been applied. */
    public int currentVersion() throws SQLException {
        ensureVersionTable();
        Integer version = pool.queryOne(
                "SELECT version FROM " + VERSION_TABLE + " WHERE owner = ?",
                rs -> rs.getInt("version"), owner);
        return version == null ? 0 : version;
    }

    /** Whether the version table already knows this owner. Exposed for diagnostics commands. */
    public boolean hasRun() throws SQLException {
        ensureVersionTable();
        try (Connection conn = pool.connection();
             PreparedStatement statement = conn.prepareStatement(
                     "SELECT 1 FROM " + VERSION_TABLE + " WHERE owner = ?")) {
            statement.setString(1, owner);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void ensureVersionTable() throws SQLException {
        pool.update("CREATE TABLE IF NOT EXISTS " + VERSION_TABLE + " ("
                + "owner VARCHAR(64) NOT NULL PRIMARY KEY,"
                + "version INT NOT NULL,"
                + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private void record(Connection conn, int version) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO " + VERSION_TABLE + " (owner, version) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE version = VALUES(version), applied_at = CURRENT_TIMESTAMP")) {
            statement.setString(1, owner);
            statement.setInt(2, version);
            statement.executeUpdate();
        }
    }
}
