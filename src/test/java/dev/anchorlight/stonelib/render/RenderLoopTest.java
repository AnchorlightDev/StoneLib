package dev.anchorlight.StoneLib.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the registry side of the loop — what is registered under which key, and what clean-up
 * runs. The drawing pass itself needs a running server and is not exercised here.
 */
class RenderLoopTest {

    /** A renderable that records its own clean-up and touches no Bukkit API. */
    private static final class StubRenderable implements Renderable {
        private final UUID owner;
        private final String key;
        private boolean cleanedUp;

        StubRenderable(UUID owner, String key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public void render(RenderContext context) {
        }

        @Override
        public void cleanUp() {
            cleanedUp = true;
        }
    }

    @BeforeEach
    void setUp() {
        // Clean-up asks Bukkit whether it is on the primary thread before deciding to schedule.
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static RenderLoop newLoop() {
        // Never started, so no scheduler or plugin is touched; only the registry is under test.
        return new RenderLoop(null, null, 2L, 48.0);
    }

    @Test
    void oneOwnerCanWearSeveralRenderablesAtOnce() {
        RenderLoop loop = newLoop();
        UUID owner = UUID.randomUUID();

        loop.add(new StubRenderable(owner, "TRAIL"));
        loop.add(new StubRenderable(owner, "HAT"));
        loop.add(new StubRenderable(owner, "WINGS"));

        // Keying by owner alone would leave one survivor here, and a player wearing a trail, a hat
        // and wings would only ever see the last one applied.
        assertEquals(3, loop.size());
        assertNotNull(loop.get(owner, "TRAIL"));
        assertNotNull(loop.get(owner, "HAT"));
        assertNotNull(loop.get(owner, "WINGS"));
    }

    @Test
    void sameKeyReplacesAndCleansUpThePrevious() {
        RenderLoop loop = newLoop();
        UUID owner = UUID.randomUUID();
        StubRenderable first = new StubRenderable(owner, "TRAIL");
        StubRenderable second = new StubRenderable(owner, "TRAIL");

        loop.add(first);
        loop.add(second);

        assertEquals(1, loop.size());
        assertSame(second, loop.get(owner, "TRAIL"));
        assertTrue(first.cleanedUp, "the replaced renderable must be cleaned up, not just dropped");
    }

    @Test
    void removingOneKeyLeavesTheOthers() {
        RenderLoop loop = newLoop();
        UUID owner = UUID.randomUUID();
        StubRenderable trail = new StubRenderable(owner, "TRAIL");
        loop.add(trail);
        loop.add(new StubRenderable(owner, "HAT"));

        loop.remove(owner, "TRAIL");

        assertEquals(1, loop.size());
        assertNull(loop.get(owner, "TRAIL"));
        assertNotNull(loop.get(owner, "HAT"));
        assertTrue(trail.cleanedUp);
    }

    @Test
    void removingAnOwnerRemovesEverythingTheyHad() {
        RenderLoop loop = newLoop();
        UUID owner = UUID.randomUUID();
        StubRenderable trail = new StubRenderable(owner, "TRAIL");
        StubRenderable hat = new StubRenderable(owner, "HAT");
        loop.add(trail);
        loop.add(hat);

        loop.remove(owner);

        assertEquals(0, loop.size());
        assertTrue(trail.cleanedUp);
        assertTrue(hat.cleanedUp);
    }

    @Test
    void ownersDoNotInterfereWithEachOther() {
        RenderLoop loop = newLoop();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        loop.add(new StubRenderable(first, "TRAIL"));
        loop.add(new StubRenderable(second, "TRAIL"));
        loop.remove(first);

        assertEquals(1, loop.size());
        assertNotNull(loop.get(second, "TRAIL"));
    }

    @Test
    void removingSomethingThatIsNotThereIsHarmless() {
        RenderLoop loop = newLoop();
        UUID owner = UUID.randomUUID();

        loop.remove(owner);
        loop.remove(owner, "TRAIL");

        assertEquals(0, loop.size());
    }
}
