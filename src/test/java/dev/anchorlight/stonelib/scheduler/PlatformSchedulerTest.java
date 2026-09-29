package dev.anchorlight.stonelib.scheduler;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlatformSchedulerTest {

    private ServerMock server;
    private PlatformScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin("StoneLibTest");
        scheduler = new PlatformScheduler(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void entityGlobalAndRegionTasksRunOnTick() {
        PlayerMock player = server.addPlayer();
        AtomicInteger ran = new AtomicInteger();
        scheduler.entity(player, ran::incrementAndGet);
        scheduler.global(ran::incrementAndGet);
        scheduler.region(player.getLocation(), ran::incrementAndGet);
        scheduler.globalLater(ran::incrementAndGet, 5);

        server.getScheduler().performOneTick();
        assertEquals(3, ran.get());
        server.getScheduler().performTicks(5);
        assertEquals(4, ran.get());
    }

    @Test
    void runOnIsImmediateOnTheOwningThread() {
        PlayerMock player = server.addPlayer();
        AtomicInteger ran = new AtomicInteger();
        scheduler.runOn(player, ran::incrementAndGet);
        assertEquals(1, ran.get());
    }

    @Test
    void cancelAllStopsTimers() {
        PlayerMock player = server.addPlayer();
        AtomicInteger ran = new AtomicInteger();
        scheduler.entityTimer(player, ran::incrementAndGet, null, 1, 1);
        scheduler.globalTimer(ran::incrementAndGet, 1, 1);
        server.getScheduler().performTicks(3);
        int before = ran.get();
        scheduler.cancelAll();
        server.getScheduler().performTicks(5);
        assertEquals(before, ran.get());
        assertFalse(PlatformScheduler.isFolia());
    }
}
