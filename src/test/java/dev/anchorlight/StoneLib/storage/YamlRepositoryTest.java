package dev.anchorlight.StoneLib.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlRepositoryTest {

    record Note(UUID id, String text) {}

    private JavaPlugin plugin;
    private YamlRepository<UUID, Note> repository;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        RecordCodec<Note> codec = new RecordCodec<>() {
            @Override
            public Map<String, Object> toMap(Note value) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("text", value.text());
                return map;
            }

            @Override
            public Note fromMap(Map<String, Object> map) {
                return new Note(null, (String) map.get("text"));
            }
        };
        repository = new YamlRepository<>(plugin, "notes.yml", codec, Note::id, UUID::fromString);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void savesAndReloadsRecords() {
        UUID id = UUID.randomUUID();
        repository.put(id, new Note(id, "hello world"));
        repository.save();

        YamlRepository<UUID, Note> reloaded = new YamlRepository<>(
                plugin, "notes.yml",
                new RecordCodec<Note>() {
                    @Override
                    public Map<String, Object> toMap(Note value) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("text", value.text());
                        return map;
                    }

                    @Override
                    public Note fromMap(Map<String, Object> map) {
                        return new Note(null, (String) map.get("text"));
                    }
                },
                Note::id, UUID::fromString);
        reloaded.load();

        assertEquals(1, reloaded.all().size());
        assertEquals("hello world", reloaded.all().iterator().next().text());
    }
}
