package dev.anchorlight.stonelib.yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes a state file off the server thread, coalescing bursts of changes into one write.
 *
 * <p>Saving a YAML file on every change blocks the tick on disk I/O, and on a busy server a burst
 * of changes turns into a burst of identical writes. Here a change only calls {@link #markDirty()};
 * the file is written once, {@code debounce} after the first unsaved change, on a dedicated
 * background thread. {@link #flush()} writes synchronously and belongs in {@code onDisable}, after
 * the last change and before the plugin's classes go away.
 *
 * <h2>Content</h2>
 * The content supplier runs on the writer thread, so it must read from thread-safe state - a
 * {@code ConcurrentHashMap} it serialises, say - rather than from a {@code YamlConfiguration}
 * shared with the server thread. That is also what makes this safe on Folia, where changes arrive
 * from many region threads at once.
 *
 * <h2>Durability</h2>
 * Each write goes to a temporary file that is then moved over the target, atomically where the
 * filesystem allows. A crash or a full disk mid-write leaves the previous file intact rather than a
 * truncated one.
 */
public final class DebouncedFileWriter implements AutoCloseable {

    private final File file;
    private final Supplier<String> content;
    private final Logger logger;
    private final long debounceMillis;
    private final ScheduledExecutorService executor;
    private final Object lock = new Object();
    private ScheduledFuture<?> pending;
    private volatile boolean closed;

    /**
     * @param file     the file to write
     * @param content  produces the full file content; called on the writer thread
     * @param debounce how long to wait after the first unsaved change before writing
     * @param logger   where write failures are reported
     */
    public DebouncedFileWriter(File file, Supplier<String> content, Duration debounce, Logger logger) {
        this.file = file;
        this.content = content;
        this.logger = logger;
        this.debounceMillis = Math.max(0, debounce.toMillis());
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StoneLib-Writer-" + file.getName());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Schedules a write, unless one is already waiting. Cheap; call it on every change. */
    public void markDirty() {
        if (closed) {
            return;
        }
        synchronized (lock) {
            if (pending != null && !pending.isDone()) {
                return;
            }
            pending = executor.schedule(this::writeQuietly, debounceMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** True while a scheduled write has not run yet. */
    public boolean hasPendingWrite() {
        synchronized (lock) {
            return pending != null && !pending.isDone();
        }
    }

    /**
     * Writes now, on the calling thread, cancelling any scheduled write. Waits for a write already in
     * progress on the background thread, so the file on disk is the latest content when this returns.
     *
     * @return true when the write succeeded
     */
    public boolean flush() {
        synchronized (lock) {
            if (pending != null) {
                pending.cancel(false);
                pending = null;
            }
        }
        return write();
    }

    /** Flushes, then stops the writer thread. Further changes are ignored. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        flush();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeQuietly() {
        synchronized (lock) {
            pending = null;
        }
        write();
    }

    /** Serialised by the monitor so a flush and a background write never interleave. */
    private synchronized boolean write() {
        String text;
        try {
            text = content.get();
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Could not build the content for " + file.getName(), ex);
            return false;
        }
        Path target = file.toPath();
        Path parent = target.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = (parent == null ? Path.of(".") : parent).resolve(file.getName() + ".tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save " + file.getName()
                    + "; the previous copy on disk is unchanged", ex);
            return false;
        }
    }
}
