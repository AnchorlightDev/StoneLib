package dev.anchorlight.stonelib.messaging.request;

import dev.anchorlight.stonelib.cooldown.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRequestsTest {

    @Test
    void replyCompletesMatchingRequestAndRemovesIt() throws Exception {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofSeconds(5));
        AtomicReference<String> sentId = new AtomicReference<>();

        CompletableFuture<String> reply = pending.send(sentId::set);
        assertEquals(1, pending.size());

        assertTrue(pending.complete(sentId.get(), "pong"));
        assertEquals("pong", reply.get(1, TimeUnit.SECONDS));
        assertEquals(0, pending.size());
    }

    @Test
    void unknownOrNullIdsAreIgnored() {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofSeconds(5));
        assertFalse(pending.complete("nope", "x"));
        assertFalse(pending.complete(null, "x"));
    }

    @Test
    void unansweredRequestTimesOutAndIsRemoved() {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofMillis(30));
        CompletableFuture<String> reply = pending.send(id -> { });

        ExecutionException error = assertThrows(ExecutionException.class, () -> reply.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertEquals(0, pending.size());
    }

    @Test
    void transmitFailureFailsFutureImmediately() {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofSeconds(5));
        CompletableFuture<String> reply = pending.send(id -> {
            throw new IllegalStateException("player offline");
        });

        assertTrue(reply.isCompletedExceptionally());
        assertEquals(0, pending.size());
    }

    @Test
    void duplicatePendingIdIsRejected() {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofSeconds(5));
        pending.send("fixed", id -> { });
        assertThrows(IllegalStateException.class, () -> pending.send("fixed", id -> { }));
    }

    @Test
    void cancelAllFailsEveryPendingRequest() {
        PendingRequests<String> pending = new PendingRequests<>(Duration.ofSeconds(5));
        CompletableFuture<String> a = pending.send(id -> { });
        CompletableFuture<String> b = pending.send(id -> { });

        pending.cancelAll();

        assertTrue(a.isCompletedExceptionally());
        assertTrue(b.isCompletedExceptionally());
        assertEquals(0, pending.size());
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new PendingRequests<String>(Duration.ZERO));
    }

    // --- RateLimiter -------------------------------------------------------------

    @Test
    void rateLimiterAllowsOncePerIntervalPerKey() {
        AtomicLong now = new AtomicLong(1_000);
        RateLimiter limiter = new RateLimiter(Duration.ofMillis(500), now::get);
        UUID a = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(a));
        assertFalse(limiter.tryAcquire(a));
        assertTrue(limiter.tryAcquire(UUID.randomUUID()));

        now.addAndGet(499);
        assertFalse(limiter.tryAcquire(a));
        now.addAndGet(1);
        assertTrue(limiter.tryAcquire(a));
    }

    @Test
    void rateLimiterClearResetsKey() {
        RateLimiter limiter = new RateLimiter(Duration.ofSeconds(10));
        UUID a = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(a));
        limiter.clear(a);
        assertTrue(limiter.tryAcquire(a));
    }
}
