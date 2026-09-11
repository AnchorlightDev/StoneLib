package dev.anchorlight.stonelib.http;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe connectivity state for a remote service: whether the last request succeeded, when
 * requests last succeeded and failed, and whether a persistent stream (a WebSocket, say) is up.
 * {@link ApiClient} updates it automatically; read it for status commands and heartbeats.
 */
public final class ConnectionHealth {

    private final AtomicBoolean reachable = new AtomicBoolean(false);
    private final AtomicBoolean streamConnected = new AtomicBoolean(false);
    private final AtomicLong lastSuccessMillis = new AtomicLong(0);
    private final AtomicLong lastFailureMillis = new AtomicLong(0);

    /** True when the most recent request succeeded. False until the first success. */
    public boolean isReachable() {
        return reachable.get();
    }

    public void markSuccess() {
        reachable.set(true);
        lastSuccessMillis.set(System.currentTimeMillis());
    }

    public void markFailure() {
        reachable.set(false);
        lastFailureMillis.set(System.currentTimeMillis());
    }

    public boolean isStreamConnected() {
        return streamConnected.get();
    }

    public void setStreamConnected(boolean connected) {
        streamConnected.set(connected);
    }

    /** Epoch millis of the last successful request, or 0 if there hasn't been one. */
    public long lastSuccessMillis() {
        return lastSuccessMillis.get();
    }

    /** Epoch millis of the last failed request, or 0 if there hasn't been one. */
    public long lastFailureMillis() {
        return lastFailureMillis.get();
    }
}
