package dev.anchorlight.stonelib.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ok", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"ok\":true}");
        });
        server.createContext("/api/reject", exchange -> respond(exchange, 409, "{\"error\":\"insufficient tokens\"}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ApiClient client(ConnectionHealth health) {
        return ApiClient.builder(baseUrl).bearerToken("secret").health(health).requestTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void postSendsBodyAndAuthAndMarksSuccess() throws Exception {
        ConnectionHealth health = new ConnectionHealth();
        assertTrue(client(health).post("/api/ok", "{\"a\":1}").get(5, TimeUnit.SECONDS));
        assertEquals("Bearer secret", lastAuth.get());
        assertEquals("{\"a\":1}", lastBody.get());
        assertTrue(health.isReachable());
        assertTrue(health.lastSuccessMillis() > 0);
    }

    @Test
    void getReturnsBodyOnSuccessAndNullOnErrorStatus() throws Exception {
        ConnectionHealth health = new ConnectionHealth();
        ApiClient api = client(health);
        assertEquals("{\"ok\":true}", api.get("/api/ok").get(5, TimeUnit.SECONDS));
        assertNull(api.get("/api/reject").get(5, TimeUnit.SECONDS));
        assertFalse(health.isReachable());
    }

    @Test
    void sendReturnsRejectionBodyWithoutCountingItAsFailure() throws Exception {
        ConnectionHealth health = new ConnectionHealth();
        Optional<ApiClient.Response> response = client(health).send("POST", "/api/reject", "{}").get(5, TimeUnit.SECONDS);
        assertTrue(response.isPresent());
        assertEquals(409, response.get().status());
        assertTrue(response.get().body().contains("insufficient"));
        assertTrue(health.isReachable());
    }

    @Test
    void networkFailureNeverThrowsAndMarksFailure() throws Exception {
        server.stop(0);
        ConnectionHealth health = new ConnectionHealth();
        ApiClient api = client(health);
        assertFalse(api.post("/api/ok", "{}").get(10, TimeUnit.SECONDS));
        assertNull(api.get("/api/ok").get(10, TimeUnit.SECONDS));
        assertTrue(api.send("GET", "/api/ok", null).get(10, TimeUnit.SECONDS).isEmpty());
        assertTrue(health.lastFailureMillis() > 0);
    }

    @Test
    void resolveUrlAvoidsDoubleApiSegment() {
        assertEquals("https://x.dev/api/events", ApiClient.resolveUrl("https://x.dev/api", "/api/events"));
        assertEquals("https://x.dev/api/events", ApiClient.resolveUrl("https://x.dev/api/", "/api/events"));
        assertEquals("https://x.dev/api/events", ApiClient.resolveUrl("https://x.dev", "/api/events"));
        assertEquals("https://x.dev/events", ApiClient.resolveUrl("https://x.dev/", "events"));
    }

    // --- RetryQueue --------------------------------------------------------------

    @Test
    void retryQueueDropsOldestWhenFull() {
        RetryQueue<Integer> queue = new RetryQueue<>(3, null);
        for (int i = 1; i <= 5; i++) {
            queue.offer(i);
        }
        assertEquals(3, queue.size());
        assertEquals(2, queue.droppedCount());
        assertEquals(List.of(3, 4, 5), queue.drain(10));
        assertTrue(queue.isEmpty());
    }

    @Test
    void retryQueueRequeuesBatchAtFrontInOrder() {
        RetryQueue<Integer> queue = new RetryQueue<>(10, null);
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
        List<Integer> batch = queue.drain(2);
        assertEquals(List.of(1, 2), batch);

        queue.requeueFront(batch);
        assertEquals(3, queue.size());
        assertEquals(List.of(1, 2, 3), queue.drain(10));
    }
}
