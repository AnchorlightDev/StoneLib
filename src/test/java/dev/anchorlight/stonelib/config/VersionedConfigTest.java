package dev.anchorlight.stonelib.config;

import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.route.Route;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionedConfigTest {

    private static final String NAME = "versioned-config-test.yml";

    private JavaPlugin plugin;
    private File file;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), NAME);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void write(String yaml) throws IOException {
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
    }

    @Test
    void aBrokenFileIsNeverOverwrittenAndDefaultsAreServed() throws IOException {
        String broken = "config-version: 2\ngreeting: custom\nnested:\n  value: [unclosed\n\tbad: tab\n";
        write(broken);

        VersionedConfig config = new VersionedConfig(plugin, NAME);

        assertFalse(config.isHealthy());
        assertNotNull(config.problem());
        assertEquals("hello", config.config().getString("greeting"), "bundled defaults are served");
        assertEquals(broken, Files.readString(file.toPath()), "the admin's file is byte-for-byte untouched");

        assertFalse(config.set("greeting", "changed"), "a write to a broken file is refused");
        assertEquals(broken, Files.readString(file.toPath()));
        assertEquals("changed", config.config().getString("greeting"), "but applies in memory");
    }

    @Test
    void fixingTheFileAndReloadingRecovers() throws IOException {
        write("greeting: [broken\n");
        VersionedConfig config = new VersionedConfig(plugin, NAME);
        assertFalse(config.isHealthy());

        write("config-version: 2\ngreeting: fixed\n");
        assertTrue(config.reload());
        assertEquals("fixed", config.config().getString("greeting"));
        assertEquals(5, config.config().getInt("nested.value"), "missing keys are merged in");
    }

    @Test
    void setPersistsAndKeepsComments() throws IOException {
        write("config-version: 2\n# keep me\ngreeting: custom\nnested:\n  value: 7\n");
        VersionedConfig config = new VersionedConfig(plugin, NAME);

        assertTrue(config.set("nested.value", 9));

        String saved = Files.readString(file.toPath());
        assertTrue(saved.contains("# keep me"));
        assertTrue(saved.contains("value: 9"));
        assertEquals("custom", new VersionedConfig(plugin, NAME).config().getString("greeting"));
    }

    @Test
    void relocationsMigrateOldKeys() throws IOException {
        write("config-version: 1\nold-greeting: migrated\n");
        UpdaterSettings settings = UpdaterSettings.builder()
                .setVersioning(new BasicVersioning(ConfigUpdater.VERSION_ROUTE))
                .addRelocation("2", Route.fromString("old-greeting"), Route.fromString("greeting"))
                .build();

        VersionedConfig config = new VersionedConfig(plugin, NAME, settings);

        assertTrue(config.isHealthy());
        assertEquals("migrated", config.config().getString("greeting"));
        assertEquals(2, config.config().getInt("config-version"));
    }

    @Test
    void anUnversionedFileIsTreatedAsVersionOneAndMigrated() throws IOException {
        write("old-greeting: from-before-versioning\n");
        UpdaterSettings settings = UpdaterSettings.builder()
                .setVersioning(new BasicVersioning(ConfigUpdater.VERSION_ROUTE))
                .addRelocation("2", Route.fromString("old-greeting"), Route.fromString("greeting"))
                .build();

        VersionedConfig config = new VersionedConfig(plugin, NAME, settings);

        assertEquals("from-before-versioning", config.config().getString("greeting"), "version 2's relocation ran");
        assertTrue(Files.readString(file.toPath()).contains("config-version: 2"),
                "and the version is recorded in the file: " + Files.readString(file.toPath()));
    }

    @Test
    void aMissingFileIsCreatedFromTheResource() {
        VersionedConfig config = new VersionedConfig(plugin, NAME);
        assertTrue(config.isHealthy());
        assertTrue(file.exists());
        assertEquals("hello", config.config().getString("greeting"));
    }
}
