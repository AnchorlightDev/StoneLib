package dev.anchorlight.StoneLib.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.route.Route;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the update semantics {@link ConfigUpdater} relies on: that a versioned relocation actually
 * migrates a renamed key, and that a resource with no version route still merges cleanly.
 *
 * These drive BoostedYAML through the same settings ConfigUpdater builds, rather than through a
 * mock plugin, because what needs proving is the update behaviour and not the resource lookup.
 */
class ConfigUpdaterTest {

    // Not @TempDir: BoostedYAML retains the file handle, and JUnit's cleanup then fails on
    // Windows. The directory is under target/, so a normal clean removes it.
    private Path temp;

    @BeforeEach
    void setUp() throws IOException {
        temp = Path.of("target", "config-updater-test");
        Files.createDirectories(temp);
        try (var entries = Files.list(temp)) {
            for (Path entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static InputStream resource(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private File serverFile(String name, String yaml) throws IOException {
        File file = temp.resolve(name).toFile();
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
        return file;
    }

    /** Mirrors the stream-driven path ConfigUpdater uses, so no file handle is retained. */
    private YamlDocument update(File serverFile, String bundled, UpdaterSettings settings) throws IOException {
        YamlDocument document;
        try (InputStream current = Files.newInputStream(serverFile.toPath());
             InputStream defaults = resource(bundled)) {
            document = YamlDocument.create(
                    current,
                    defaults,
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    settings);
        }
        document.save(serverFile);
        return document;
    }

    @Test
    void versionedRelocationMigratesARenamedKey() throws IOException {
        // The server is still on version 1, where the key was called "old-name".
        File file = serverFile("config.yml", "config-version: 1\nold-name: kept-value\n");

        // Version 2 of the bundled resource renames it to "new-name".
        String bundled = "config-version: 2\nnew-name: default-value\n";

        YamlDocument document = update(file, bundled, UpdaterSettings.builder()
                .setVersioning(new BasicVersioning("config-version"))
                .addRelocation("2", Route.fromString("old-name"), Route.fromString("new-name"))
                .build());

        // The player's own value survives the rename rather than being reset to the default.
        assertEquals("kept-value", document.getString("new-name"));
        assertNull(document.getString("old-name"), "the old key should be gone after relocation");
        assertEquals("2", document.getString("config-version"));

        String onDisk = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains("new-name"), "the rename must be persisted, not just in memory");
        assertFalse(onDisk.contains("old-name"));
    }

    @Test
    void versionedUpdateStillAddsNewKeys() throws IOException {
        File file = serverFile("config.yml", "config-version: 1\nexisting: mine\n");
        String bundled = "config-version: 2\nexisting: default\nadded: new-default\n";

        YamlDocument document = update(file, bundled, UpdaterSettings.builder()
                .setVersioning(new BasicVersioning("config-version"))
                .build());

        assertEquals("mine", document.getString("existing"), "an existing value must not be overwritten");
        assertEquals("new-default", document.getString("added"));
    }

    @Test
    void aServerFileWithNoVersionIsTreatedAsTheFirstVersion() throws IOException {
        // Files written before versioning was adopted have no version key at all.
        File file = serverFile("config.yml", "old-name: kept-value\n");
        String bundled = "config-version: 2\nnew-name: default-value\n";

        YamlDocument document = update(file, bundled, UpdaterSettings.builder()
                .setVersioning(new BasicVersioning("config-version"))
                .addRelocation("2", Route.fromString("old-name"), Route.fromString("new-name"))
                .build());

        assertEquals("kept-value", document.getString("new-name"),
                "an unversioned server file should migrate from version 1, not be left behind");
    }

    @Test
    void unversionedResourceStillMergesMissingKeys() throws IOException {
        // The fallback path: no config-version anywhere, so plain merge, as before versioning.
        File file = serverFile("config.yml", "existing: mine\n");
        String bundled = "existing: default\nadded: new-default\n";

        YamlDocument document = update(file, bundled, UpdaterSettings.builder().build());

        assertEquals("mine", document.getString("existing"));
        assertEquals("new-default", document.getString("added"));
    }
}
