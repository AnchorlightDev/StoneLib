package dev.anchorlight.stonelib.storage.sql;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

/**
 * Connection settings for a {@link ConnectionPool}, normally read from a plugin's config.yml.
 *
 * <p>Read one with {@link #fromSection(ConfigurationSection)}:</p>
 *
 * <pre>
 * database:
 *   host: 127.0.0.1
 *   port: 3306
 *   name: edeneffects
 *   user: eden
 *   password: "..."
 *   pool-size: 10
 *   connection-timeout-ms: 5000
 *   properties: "useSSL=false&amp;allowPublicKeyRetrieval=true"
 * </pre>
 */
public record DatabaseConfig(String host, int port, String database, String username, String password,
                             int poolSize, long connectionTimeoutMillis, String extraProperties) {

    public DatabaseConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (poolSize <= 0) {
            throw new IllegalArgumentException("pool-size must be positive: " + poolSize);
        }
        if (password == null) {
            password = "";
        }
        if (extraProperties == null) {
            extraProperties = "";
        }
    }

    /**
     * Reads a config from a {@code database:} section, applying defaults for everything but the
     * database name and credentials.
     *
     * @throws IllegalArgumentException if the section is missing or incomplete
     */
    public static DatabaseConfig fromSection(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("No database section in config");
        }
        String database = section.getString("name");
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("database.name is required");
        }
        String username = section.getString("user");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("database.user is required");
        }
        return new DatabaseConfig(
                section.getString("host", "127.0.0.1"),
                section.getInt("port", 3306),
                database,
                username,
                section.getString("password", ""),
                section.getInt("pool-size", 10),
                section.getLong("connection-timeout-ms", 5000L),
                section.getString("properties", ""));
    }

    /** The JDBC URL these settings describe. */
    public String jdbcUrl() {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        return extraProperties.isBlank() ? url : url + "?" + extraProperties;
    }

    /** Redacts the password, so a config can be logged safely. */
    @Override
    public String toString() {
        return "DatabaseConfig[" + jdbcUrl() + ", user=" + username + ", password=***, poolSize=" + poolSize + "]";
    }
}
