package dev.anchorlight.StoneLib.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadPicksUpExistingConfigValues() throws IOException {
        plugin.getDataFolder().mkdirs();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("greeting: hello\n");
        }

        ConfigManager configManager = new ConfigManager(plugin);

        assertEquals("hello", configManager.getConfig().getString("greeting"));
    }
}
