package dev.anchorlight.stonelib.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;

class LocationCodecTest {

    private MockedStatic<Bukkit> bukkitMock;
    private World world;

    @BeforeEach
    void setUp() {
        world = Mockito.mock(World.class);
        Mockito.when(world.getName()).thenReturn("world");

        bukkitMock = Mockito.mockStatic(Bukkit.class);
        bukkitMock.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(world);
        bukkitMock.when(() -> Bukkit.getWorld(eq("nonexistent"))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        bukkitMock.close();
    }

    @Test
    void roundTripsLocationWithYawAndPitch() {
        Location original = new Location(world, 10.5, 64.0, -20.25, 90f, 45f);

        String serialized = LocationCodec.serialize(original);
        Location result = LocationCodec.deserialize(serialized);

        assertEquals(world, result.getWorld());
        assertEquals(10.5, result.getX());
        assertEquals(64.0, result.getY());
        assertEquals(-20.25, result.getZ());
        assertEquals(90f, result.getYaw());
        assertEquals(45f, result.getPitch());
    }

    @Test
    void deserializeReturnsNullForUnknownWorld() {
        assertNull(LocationCodec.deserialize("nonexistent:0:0:0:0:0"));
    }

    @Test
    void deserializeReturnsNullForMalformedInput() {
        assertNull(LocationCodec.deserialize("garbage"));
    }
}
