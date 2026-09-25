package dev.anchorlight.stonelib.scheduler;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerServiceTest {

    private ServerMock server;
    private SchedulerService scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin("StoneLibTest");
        scheduler = new SchedulerService(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void oneShotTasksStopBeingTrackedOnceTheyRun() {
        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 1000; i++) {
            scheduler.runSync(ran::incrementAndGet);
            scheduler.runSyncLater(ran::incrementAndGet, 2);
        }
        assertEquals(2000, scheduler.trackedCount());

        server.getScheduler().performTicks(3);
        assertEquals(2000, ran.get());
        assertEquals(0, scheduler.trackedCount());
    }

    @Test
    void timersStayTrackedUntilCancelled() {
        AtomicInteger ran = new AtomicInteger();
        BukkitTask timer = scheduler.runTimer(ran::incrementAndGet, 1, 1);
        server.getScheduler().performTicks(5);
        assertEquals(1, scheduler.trackedCount());

        timer.cancel();
        assertEquals(0, scheduler.trackedCount());
    }

    @Test
    void cancelledOneShotsArePrunedAsNewTasksArrive() {
        for (int i = 0; i < 1000; i++) {
            scheduler.runSyncLater(() -> {}, 100).cancel();
        }
        // Each cancelled task never runs, so only pruning on add keeps the set bounded.
        assertEquals(0, scheduler.trackedCount());
    }

    @Test
    void supplyAsyncDeliversOnTheMainThread() throws InterruptedException {
        AtomicInteger delivered = new AtomicInteger();
        scheduler.supplyAsync(() -> 7, delivered::set);
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performOneTick();
        assertEquals(7, delivered.get());
        assertEquals(0, scheduler.trackedCount());
    }

    @Test
    void cancelAllStopsPendingWork() {
        AtomicInteger ran = new AtomicInteger();
        scheduler.runSyncLater(ran::incrementAndGet, 5);
        scheduler.runTimer(ran::incrementAndGet, 5, 1);
        scheduler.cancelAll();
        server.getScheduler().performTicks(10);
        assertEquals(0, ran.get());
        assertEquals(0, scheduler.trackedCount());
    }
}
