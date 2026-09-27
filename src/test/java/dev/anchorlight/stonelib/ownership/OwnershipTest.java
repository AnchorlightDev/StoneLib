package dev.anchorlight.stonelib.ownership;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OwnershipTest {

    private final AtomicLong now = new AtomicLong(0);
    private final UUID world = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void packingRoundTripsNegativeAndExtremeCoordinates() {
        int[][] cases = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-30_000_000, 2047, 12}, {5, -2048, -7}};
        for (int[] c : cases) {
            long packed = BlockPositions.pack(c[0], c[1], c[2]);
            assertEquals(c[0], BlockPositions.x(packed));
            assertEquals(c[1], BlockPositions.y(packed));
            assertEquals(c[2], BlockPositions.z(packed));
        }
    }

    @Test
    void nearestReturnsTheClosestOwnerNotTheFirstScanned() {
        BlockOwnership owners = new BlockOwnership(Duration.ofMinutes(1), now::get);
        // Alice's block is scanned first (lowest dx) but is further away.
        owners.put(world, -4, 64, 0, alice);
        owners.put(world, 1, 64, 0, bob);

        BlockOwnership.Match match = owners.nearest(world, 0.5, 64.5, 0.5, 5);
        assertEquals(bob, match.owner());
        assertEquals(1, match.x());
    }

    @Test
    void entriesExpireAndArePurged() {
        BlockOwnership owners = new BlockOwnership(Duration.ofSeconds(10), now::get);
        owners.put(world, 1, 2, 3, alice);
        assertEquals(alice, owners.owner(world, 1, 2, 3));

        now.set(10_000);
        assertNull(owners.owner(world, 1, 2, 3));
        assertNull(owners.nearest(world, 1, 2, 3, 2));
        assertEquals(1, owners.purgeExpired());
        assertEquals(0, owners.size());
    }

    @Test
    void worldsAreSeparateAndRemoveClears() {
        BlockOwnership owners = new BlockOwnership(Duration.ofMinutes(1), now::get);
        owners.put(world, 0, 0, 0, alice);
        assertNull(owners.owner(UUID.randomUUID(), 0, 0, 0));
        owners.remove(world, 0, 0, 0);
        assertNull(owners.owner(world, 0, 0, 0));
    }

    @Test
    void expiringOwnersHonourTtl() {
        ExpiringOwners<UUID> owners = new ExpiringOwners<>(Duration.ofSeconds(5), now::get);
        UUID crystal = UUID.randomUUID();
        owners.put(crystal, alice);
        assertEquals(alice, owners.owner(crystal));
        now.set(5_000);
        assertNull(owners.owner(crystal));
        assertEquals(1, owners.purgeExpired());
    }
}
