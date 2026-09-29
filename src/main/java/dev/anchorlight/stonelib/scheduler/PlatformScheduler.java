package dev.anchorlight.stonelib.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Scheduling that is correct on both Paper and Folia, built on Paper's regionised schedulers.
 *
 * <h2>Why not {@link SchedulerService}</h2>
 * {@code SchedulerService} wraps the Bukkit scheduler, which Folia does not implement: every call
 * throws {@code UnsupportedOperationException}. Folia splits the world into regions ticked on
 * different threads, so "the main thread" no longer exists and work has to be sent to whichever
 * thread owns the thing it touches.
 *
 * <p>Paper ships the same four schedulers Folia uses, backed by the main thread, so one code path
 * serves both servers:
 *
 * <ul>
 *   <li>{@link #runOn(Entity, Runnable)} / {@link #entityLater} / {@link #entityTimer} - anything
 *       that reads or changes an entity (a player's potion effects, velocity, action bar). The task
 *       follows the entity between regions and is dropped if it is removed.</li>
 *   <li>{@link #region} / {@link #regionLater} - anything that touches blocks or the world at a
 *       location.</li>
 *   <li>{@link #global} / {@link #globalLater} / {@link #globalTimer} - server-wide work that
 *       touches neither: world time, weather, console commands.</li>
 *   <li>{@link #async} / {@link #asyncLater} / {@link #asyncTimer} - I/O and pure data work.</li>
 * </ul>
 *
 * <p>Every task this service creates is tracked, so {@link #cancelAll()} in {@code onDisable}
 * cleans up, the same contract as {@code SchedulerService}.
 *
 * <p>Delays are in ticks for the tick-driven schedulers and are clamped to at least one tick,
 * because the regionised schedulers reject zero.
 */
public final class PlatformScheduler {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private final Plugin plugin;
    private final Set<Task> tracked = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** True on a Folia server, where the Bukkit scheduler and several global APIs are unavailable. */
    public static boolean isFolia() {
        return FOLIA;
    }

    // ------------------------------------------------------------------ entity

    /**
     * Runs {@code task} on the thread that owns {@code entity}: immediately when the caller is
     * already on it, otherwise on the entity's next tick. Nothing runs if the entity is removed
     * first.
     *
     * <p>This is the call to reach for from an event handler that needs to act on a <em>second</em>
     * entity, such as messaging the shooter of an arrow that may be in another region.
     */
    public void runOn(Entity entity, Runnable task) {
        if (entity == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }
        entity(entity, task);
    }

    /** Runs {@code task} on the entity's next tick. */
    public Task entity(Entity entity, Runnable task) {
        return entityLater(entity, task, 1);
    }

    public Task entityLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null) {
            return Task.NONE;
        }
        Task handle = new Task(false);
        return track(handle, entity.getScheduler().runDelayed(plugin, handle.wrap(task), null, clamp(delayTicks)));
    }

    /**
     * Repeats {@code task} on the entity's thread until cancelled or the entity is removed.
     *
     * @param retired run once if the entity is removed while the timer is live, or null
     */
    public Task entityTimer(Entity entity, Runnable task, Runnable retired, long delayTicks, long periodTicks) {
        if (entity == null) {
            return Task.NONE;
        }
        Task handle = new Task(true);
        return track(handle, entity.getScheduler().runAtFixedRate(plugin, handle.wrap(task), retired,
                clamp(delayTicks), clamp(periodTicks)));
    }

    // ------------------------------------------------------------------ region

    /** Runs {@code task} on the thread that owns {@code location}, immediately if that is this one. */
    public void runAt(Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }
        region(location, task);
    }

    public Task region(Location location, Runnable task) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getRegionScheduler().run(plugin, location, handle.wrap(task)));
    }

    public Task regionLater(Location location, Runnable task, long delayTicks) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getRegionScheduler().runDelayed(plugin, location, handle.wrap(task),
                clamp(delayTicks)));
    }

    // ------------------------------------------------------------------ global

    public Task global(Runnable task) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getGlobalRegionScheduler().run(plugin, handle.wrap(task)));
    }

    public Task globalLater(Runnable task, long delayTicks) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getGlobalRegionScheduler().runDelayed(plugin, handle.wrap(task), clamp(delayTicks)));
    }

    public Task globalTimer(Runnable task, long delayTicks, long periodTicks) {
        Task handle = new Task(true);
        return track(handle, Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, handle.wrap(task),
                clamp(delayTicks), clamp(periodTicks)));
    }

    // ------------------------------------------------------------------- async

    public Task async(Runnable task) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getAsyncScheduler().runNow(plugin, handle.wrap(task)));
    }

    public Task asyncLater(Runnable task, Duration delay) {
        Task handle = new Task(false);
        return track(handle, Bukkit.getAsyncScheduler().runDelayed(plugin, handle.wrap(task),
                Math.max(1, delay.toMillis()), TimeUnit.MILLISECONDS));
    }

    public Task asyncTimer(Runnable task, Duration delay, Duration period) {
        Task handle = new Task(true);
        return track(handle, Bukkit.getAsyncScheduler().runAtFixedRate(plugin, handle.wrap(task),
                Math.max(1, delay.toMillis()), Math.max(1, period.toMillis()), TimeUnit.MILLISECONDS));
    }

    // --------------------------------------------------------------- lifecycle

    /** How many live tasks are tracked. Finished one-shot tasks are pruned as they are counted. */
    public int trackedCount() {
        tracked.removeIf(Task::isDone);
        return tracked.size();
    }

    /** Cancels every tracked task. Safe to call more than once; call it from onDisable. */
    public void cancelAll() {
        for (Task task : tracked) {
            task.cancel();
        }
        tracked.clear();
    }

    private Task track(Task handle, ScheduledTask scheduled) {
        if (scheduled == null) {
            // An entity scheduler returns null when the entity has already been removed.
            return Task.NONE;
        }
        handle.bind(scheduled);
        tracked.add(handle);
        if (tracked.size() > 256) {
            tracked.removeIf(Task::isDone);
        }
        return handle;
    }

    private static long clamp(long ticks) {
        return Math.max(1L, ticks);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * A handle on one scheduled task.
     *
     * <p>Cancellation is recorded here as well as passed to the platform, and a cancelled handle
     * skips its next run. That keeps {@link #cancel()} reliable on schedulers that cannot cancel a
     * task that is already queued, and on test servers that do not implement cancellation at all.
     */
    public static final class Task {

        /** Returned when nothing was scheduled, e.g. the entity was already removed. */
        public static final Task NONE = new Task(false, true);

        private final boolean repeating;
        private volatile ScheduledTask scheduled;
        private volatile boolean cancelled;
        private volatile boolean finished;

        private Task(boolean repeating) {
            this(repeating, false);
        }

        private Task(boolean repeating, boolean finished) {
            this.repeating = repeating;
            this.finished = finished;
        }

        private void bind(ScheduledTask scheduled) {
            this.scheduled = scheduled;
        }

        private java.util.function.Consumer<ScheduledTask> wrap(Runnable task) {
            return running -> {
                if (cancelled) {
                    cancelNative(running);
                    return;
                }
                try {
                    task.run();
                } finally {
                    if (!repeating) {
                        finished = true;
                    }
                }
            };
        }

        public void cancel() {
            if (cancelled || this == NONE) {
                return;
            }
            cancelled = true;
            cancelNative(scheduled);
        }

        public boolean isCancelled() {
            return cancelled || this == NONE;
        }

        /** True once the task can no longer run: cancelled, or a one-shot that has finished. */
        public boolean isDone() {
            return cancelled || finished;
        }

        private static void cancelNative(ScheduledTask scheduled) {
            if (scheduled == null) {
                return;
            }
            try {
                scheduled.cancel();
            } catch (RuntimeException ignored) {
                // Already finished, unsupported, or the scheduler is gone. The flag still stops it.
            }
        }
    }
}
