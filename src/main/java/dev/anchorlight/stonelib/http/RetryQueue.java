package dev.anchorlight.stonelib.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * A bounded, thread-safe queue of work that failed to send and should be retried later - events
 * for a web API that is briefly offline, for example.
 *
 * <p>When full, the oldest entry is dropped and counted, so a long outage costs a bounded amount of
 * memory instead of an out-of-memory crash. Drops are logged on the first and every hundredth
 * occurrence to keep the console readable.
 *
 * <pre>{@code
 * RetryQueue<Event> retries = new RetryQueue<>(5000, getLogger());
 *
 * api.post("/events", json).thenAccept(ok -> { if (!ok) retries.offer(event); });
 *
 * // on a timer
 * List<Event> batch = retries.drain(100);
 * api.post("/events/batch", toJson(batch)).thenAccept(ok -> { if (!ok) retries.requeueFront(batch); });
 * }</pre>
 *
 * @param <E> the queued item type
 */
public final class RetryQueue<E> {

    private final ConcurrentLinkedDeque<E> queue = new ConcurrentLinkedDeque<>();
    private final AtomicLong size = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();
    private final int maxSize;
    private final Logger logger;

    /**
     * @param maxSize capacity; values below 1 are treated as 1
     * @param logger  where drop warnings go, or null for silence
     */
    public RetryQueue(int maxSize, Logger logger) {
        this.maxSize = Math.max(1, maxSize);
        this.logger = logger;
    }

    public void offer(E item) {
        queue.addLast(item);
        size.incrementAndGet();
        while (size.get() > maxSize) {
            E dropped = queue.pollFirst();
            if (dropped == null) {
                break;
            }
            size.decrementAndGet();
            long total = droppedCount.incrementAndGet();
            if (logger != null && (total == 1 || total % 100 == 0)) {
                logger.warning("Retry queue full; dropped oldest entry (total dropped: " + total + ")");
            }
        }
    }

    /** Removes and returns up to {@code max} entries, oldest first. */
    public List<E> drain(int max) {
        List<E> batch = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            E item = queue.pollFirst();
            if (item == null) {
                break;
            }
            size.decrementAndGet();
            batch.add(item);
        }
        return batch;
    }

    /** Puts a drained batch back at the front, preserving its order, after a failed retry. */
    public void requeueFront(List<E> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            queue.addFirst(items.get(i));
            size.incrementAndGet();
        }
    }

    public int size() {
        return (int) Math.max(0, size.get());
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** How many entries have been dropped for lack of space since creation. */
    public long droppedCount() {
        return droppedCount.get();
    }
}
