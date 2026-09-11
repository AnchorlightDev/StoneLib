package dev.anchorlight.stonelib.storage.sql;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    private static YamlConfiguration config(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return configuration;
    }

    @Test
    void readsSectionAndAppliesDefaults() {
        DatabaseConfig read = DatabaseConfig.fromSection(config("""
                database:
                  name: edeneffects
                  user: eden
                  password: secret
                """).getConfigurationSection("database"));

        assertEquals("127.0.0.1", read.host());
        assertEquals(3306, read.port());
        assertEquals("edeneffects", read.database());
        assertEquals(10, read.poolSize());
    }

    @Test
    void buildsJdbcUrlWithAndWithoutProperties() {
        DatabaseConfig plain = new DatabaseConfig("db.host", 3307, "eden", "user", "pw", 4, 5000, "");
        assertEquals("jdbc:mysql://db.host:3307/eden", plain.jdbcUrl());

        DatabaseConfig withProperties = new DatabaseConfig("db.host", 3307, "eden", "user", "pw", 4, 5000, "useSSL=false");
        assertEquals("jdbc:mysql://db.host:3307/eden?useSSL=false", withProperties.jdbcUrl());
    }

    @Test
    void missingRequiredValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromSection(null));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromSection(config("database:\n  user: eden\n").getConfigurationSection("database")));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromSection(config("database:\n  name: eden\n").getConfigurationSection("database")));
    }

    @Test
    void rejectsNonsenseNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("h", 0, "d", "u", "p", 4, 5000, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConfig("h", 3306, "d", "u", "p", 0, 5000, ""));
    }

    @Test
    void toStringHidesThePassword() {
        String printed = new DatabaseConfig("h", 3306, "d", "u", "hunter2", 4, 5000, "").toString();

        assertFalse(printed.contains("hunter2"), "password must never reach a log line");
        assertTrue(printed.contains("***"));
    }
}
