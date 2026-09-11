package dev.anchorlight.stonelib.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thin wrapper over the Bukkit scheduler that tracks every task it creates, so a plugin can cancel
 * all of them cleanly on disable with one call.
 *
 * <p>It also enforces the golden rule by construction: Bukkit objects are only ever touched on the
 * main thread. {@link #supplyAsync} runs the expensive part off-thread and hops back to main before
 * handing you the result, which is the shape most database and file work actually wants.
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

    private final Plugin plugin;
    private final Set<BukkitTask> tracked = Collections.synchronizedSet(new HashSet<>());

    public SchedulerService(Plugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runSync(Runnable runnable) {
        return track(plugin.getServer().getScheduler().runTask(plugin, runnable));
    }

    public BukkitTask runSyncLater(Runnable runnable, long delayTicks) {
        return track(plugin.getServer().getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }

    public BukkitTask runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return track(plugin.getServer().getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks));
    }

    public BukkitTask runAsync(Runnable runnable) {
        return track(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public BukkitTask runAsyncLater(Runnable runnable, long delayTicks) {
        return track(plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks));
    }

    public BukkitTask runAsyncTimer(Runnable runnable, long delayTicks, long periodTicks) {
        return track(plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks));
    }

    /**
     * Runs {@code supplier} off the main thread, then delivers its result to {@code mainThreadCallback}
     * on the main thread. Use this for anything that reads or writes storage and then touches the world.
     */
    public <T> void supplyAsync(Supplier<T> supplier, Consumer<T> mainThreadCallback) {
        runAsync(() -> {
            T value = supplier.get();
            runSync(() -> mainThreadCallback.accept(value));
        });
    }

    /** Tracks a task this service did not create, so cancelAll covers it too. */
    public BukkitTask track(BukkitTask task) {
        if (task != null) {
            tracked.add(task);
        }
        return task;
    }

    /** How many tasks are currently tracked. Cancelled tasks are only pruned on cancelAll. */
    public int trackedCount() {
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
}
