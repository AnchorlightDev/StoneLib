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

class SqliteRepositoryTest {

    record Note(UUID id, String text) {}

    private JavaPlugin plugin;
    private RecordCodec<Note> codec;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        codec = new RecordCodec<>() {
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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void savesAndReloadsRecords() {
        SqliteRepository<UUID, Note> repository = new SqliteRepository<>(
                plugin, "notes.db", "notes", codec, Note::id, UUID::fromString);
        UUID id = UUID.randomUUID();
        repository.put(id, new Note(id, "hello sqlite"));
        repository.save();

        SqliteRepository<UUID, Note> reloaded = new SqliteRepository<>(
                plugin, "notes.db", "notes", codec, Note::id, UUID::fromString);
        reloaded.load();

        assertEquals(1, reloaded.all().size());
        assertEquals("hello sqlite", reloaded.all().iterator().next().text());
    }
}
