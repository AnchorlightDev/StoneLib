package dev.anchorlight.stonelib.messaging.request;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Request/response correlation for one-way transports such as plugin messaging, where a reply
 * arrives later as an unrelated inbound message.
 *
 * <p>Each request gets an id and a future. Hand the id to your wire format when sending; when a
 * reply carrying that id comes back, pass it to {@link #complete}. Futures that get no reply fail
 * with a {@link java.util.concurrent.TimeoutException} after the configured timeout, and are
 * always removed from the table once they settle, so nothing leaks.
 *
 * <p>Transport- and format-agnostic, and free of Bukkit and Velocity types, so the same class
 * works on either side of a proxy.
 *
 * <pre>{@code
 * PendingRequests<BridgeMessage> pending = new PendingRequests<>(Duration.ofMillis(1500));
 *
 * CompletableFuture<BridgeMessage> reply = pending.send(id ->
 *         player.sendPluginMessage(plugin, CHANNEL, codec.encode(new ConnectRequest(id, server))));
 *
 * // in the PluginMessageListener
 * BridgeMessage message = codec.decode(bytes);
 * pending.complete(message.requestId(), message);
 * }</pre>
 *
 * @param <R> the response type
 */
public final class PendingRequests<R> {

    private final Duration timeout;
    private final Map<String, CompletableFuture<R>> pending = new ConcurrentHashMap<>();

    public PendingRequests(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
    }

    /** A fresh, unguessable request id. */
    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Registers a request under a new id and runs {@code transmit} with it. If {@code transmit}
     * throws, the returned future fails with that exception instead of waiting for the timeout.
     */
    public <T extends R> CompletableFuture<T> send(Consumer<String> transmit) {
        return send(newRequestId(), transmit);
    }

    /** As {@link #send(Consumer)}, for callers whose request object already carries its id. */
    @SuppressWarnings("unchecked")
    public <T extends R> CompletableFuture<T> send(String requestId, Consumer<String> transmit) {
        Objects.requireNonNull(requestId, "requestId");
        CompletableFuture<R> future = new CompletableFuture<>();
        if (pending.putIfAbsent(requestId, future) != null) {
            throw new IllegalStateException("Request id already pending: " + requestId);
        }
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> pending.remove(requestId, future));
        try {
            transmit.accept(requestId);
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return (CompletableFuture<T>) future;
    }

    /**
     * Resolves the request with this id. Returns false when no such request is pending, which is
     * normal for late replies that already timed out, or for ids this side never sent.
     */
    public boolean complete(String requestId, R response) {
        if (requestId == null) {
            return false;
        }
        CompletableFuture<R> future = pending.get(requestId);
        return future != null && future.complete(response);
    }

    public int size() {
        return pending.size();
    }

    /** Fails every pending request, for use on shutdown. */
    public void cancelAll() {
        for (CompletableFuture<R> future : pending.values()) {
            future.completeExceptionally(new CancellationException("Request cancelled"));
        }
        pending.clear();
    }
}
