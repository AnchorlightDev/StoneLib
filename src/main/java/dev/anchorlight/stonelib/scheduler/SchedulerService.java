package dev.anchorlight.stonelib.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Thin wrapper over the Bukkit scheduler that tracks every task it creates, so a plugin can cancel
 * all of them cleanly on disable with one call.
 *
 * <p>It also enforces the golden rule by construction: Bukkit objects are only ever touched on the
 * main thread. {@link #supplyAsync} runs the expensive part off-thread and hops back to main before
 * handing you the result, which is the shape most database and file work actually wants.
 *
 * <p>Only live tasks are tracked. A one-shot task drops itself once it has run, and cancelled or
 * finished tasks are pruned as new ones arrive, so scheduling one-shots freely does not retain them
 * (or whatever their runnables captured) for the life of the plugin.
 *
 * <pre>{@code
 * // in onEnable
 * scheduler = new SchedulerService(this);
 * scheduler.runTimer(this::tick, 20L, 20L);
 *
 * // load off-thread, apply on the main thread
 * scheduler.supplyAsync(() -> repository.load(uuid), profile -> player.setLevel(profile.level()));
 *
 * // in onDisable
 * scheduler.cancelAll();
 * }</pre>
 */
public final class SchedulerService {

    /** Above this many tracked tasks, each new one first prunes the dead ones. */
    private static final int PRUNE_THRESHOLD = 256;

    private final Plugin plugin;
    private final Set<BukkitTask> tracked = Collections.synchronizedSet(new HashSet<>());

    public SchedulerService(Plugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runSync(Runnable runnable) {
        return once(runnable, task -> scheduler().runTask(plugin, task));
    }

    public BukkitTask runSyncLater(Runnable runnable, long delayTicks) {
        return once(runnable, task -> scheduler().runTaskLater(plugin, task, delayTicks));
    }

    public BukkitTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return track(scheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
    }

    public BukkitTask runAsync(Runnable runnable) {
        return once(runnable, task -> scheduler().runTaskAsynchronously(plugin, task));
    }

    public BukkitTask runAsyncLater(Runnable runnable, long delayTicks) {
        return once(runnable, task -> scheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    public BukkitTask runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return track(scheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
    }

    /**
     * Runs {@code supplier} off the main thread, then delivers its result to {@code mainThreadCallback}
     * on the main thread. Use this for anything that reads or writes storage and then touches the world.
     * The callback is skipped if the plugin was disabled while the supplier ran.
     */
    public <T> void supplyAsync(Supplier<T> supplier, Consumer<T> mainThreadCallback) {
        runAsync(() -> {
            T value = supplier.get();
            if (plugin.isEnabled()) {
                runSync(() -> mainThreadCallback.accept(value));
            }
        });
    }

    /** Tracks a task this service did not create, so cancelAll covers it too. */
    public BukkitTask track(BukkitTask task) {
        if (task != null) {
            if (tracked.size() > PRUNE_THRESHOLD) {
                prune();
            }
            tracked.add(task);
        }
        return task;
    }

    /** How many live tasks are tracked. Dead tasks are pruned as they are counted. */
    public int trackedCount() {
        prune();
        return tracked.size();
    }

    /** Cancels every tracked task. Safe to call more than once; call it from onDisable. */
    public void cancelAll() {
        synchronized (tracked) {
            for (BukkitTask task : tracked) {
                try {
                    task.cancel();
                } catch (RuntimeException ignored) {
                    // Already cancelled, or the scheduler is gone. Nothing useful to do either way.
                }
            }
            tracked.clear();
        }
    }

    private BukkitScheduler scheduler() {
        return plugin.getServer().getScheduler();
    }

    private BukkitTask once(Runnable runnable, Function<Runnable, BukkitTask> schedule) {
        OneShot shot = new OneShot(runnable);
        BukkitTask task = schedule.apply(shot);
        shot.bind(task);
        return task;
    }

    /** Drops tasks that were cancelled or have finished and will never run again. */
    private void prune() {
        BukkitScheduler scheduler = scheduler();
        tracked.removeIf(task -> task.isCancelled()
                || !(scheduler.isQueued(task.getTaskId()) || scheduler.isCurrentlyRunning(task.getTaskId())));
    }

    /**
     * Wraps a one-shot runnable so its task stops being tracked once it has run. An async task can
     * finish before {@link #bind} sees its handle, so whichever side comes second does the cleanup.
     */
    private final class OneShot implements Runnable {

        private final Runnable delegate;
        private BukkitTask task;
        private boolean finished;

        OneShot(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                synchronized (this) {
                    finished = true;
                    if (task != null) {
                        tracked.remove(task);
                    }
                }
            }
        }

        synchronized void bind(BukkitTask scheduled) {
            if (scheduled != null && !finished) {
                task = scheduled;
                track(scheduled);
            }
        }
    }
}
