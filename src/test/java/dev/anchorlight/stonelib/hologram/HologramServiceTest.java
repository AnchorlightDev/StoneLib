package dev.anchorlight.stonelib.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link HologramService}'s own id-tracking/find logic (create -> has -> remove),
 * without relying on MockBukkit's {@code addSimpleWorld}/real entity construction.
 *
 * MockBukkit's {@code addSimpleWorld("world")} reliably throws on {@code org.bukkit.block.Biome}
 * static init with the pinned paper-api 1.21.3-R0.1-SNAPSHOT build 87 (registry mismatch, root
 * caused in Task 7 - see LocationCodecTest for the same fix pattern). Instead, this test builds
 * plain Mockito mocks for {@link JavaPlugin}, {@link Server}, and {@link World}, and stubs
 * {@code World.spawn}/{@code getEntitiesByClass} against a real in-memory list of mocked
 * {@link TextDisplay} entities, each backed by a real {@code Map<NamespacedKey, String>} standing
 * in for its {@code PersistentDataContainer}. That's enough state to exercise the service's real
 * tagging/lookup/removal logic without touching Paper's real entity system.
 */
class HologramServiceTest {

    private World world;
    private JavaPlugin plugin;
    private HologramService service;
    private List<TextDisplay> entities;

    @BeforeEach
    void setUp() {
        entities = new ArrayList<>();

        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("stonelib");
        // Paper now builds a NamespacedKey from Plugin.namespace() rather than getName(); an
        // unstubbed mock returns null and the key constructor rejects it.
        when(plugin.namespace()).thenReturn("stonelib");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("StoneLibTest"));

        world = mock(World.class);

        Server server = mock(Server.class);
        when(server.getWorlds()).thenReturn(List.of(world));
        when(plugin.getServer()).thenReturn(server);

        doAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            Consumer<TextDisplay> consumer = invocation.getArgument(2);
            TextDisplay entity = createMockTextDisplay();
            consumer.accept(entity);
            entities.add(entity);
            return entity;
        }).when(world).spawn(any(Location.class), eq(TextDisplay.class), any(Consumer.class));

        when(world.getEntitiesByClass(eq(TextDisplay.class)))
                .thenAnswer(invocation -> new ArrayList<>(entities));

        service = new HologramService(plugin);
    }

    private TextDisplay createMockTextDisplay() {
        TextDisplay entity = mock(TextDisplay.class);
        Map<NamespacedKey, String> store = new HashMap<>();

        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        doAnswer(invocation -> {
            NamespacedKey key = invocation.getArgument(0);
            String value = invocation.getArgument(2);
            store.put(key, value);
            return null;
        }).when(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any());
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> store.get((NamespacedKey) invocation.getArgument(0)));
        when(entity.getPersistentDataContainer()).thenReturn(pdc);

        doAnswer(invocation -> {
            entities.remove(entity);
            return null;
        }).when(entity).remove();

        return entity;
    }

    @Test
    void createThenHasReturnsTrue() {
        UUID id = UUID.randomUUID();
        service.create(id, new Location(world, 0, 64, 0), List.of(Component.text("Line 1")));

        assertTrue(service.has(id));
    }

    @Test
    void removeDeletesTrackedHologram() {
        UUID id = UUID.randomUUID();
        service.create(id, new Location(world, 0, 64, 0), List.of(Component.text("Line 1")));
        service.remove(id);

        assertFalse(service.has(id));
    }
}
