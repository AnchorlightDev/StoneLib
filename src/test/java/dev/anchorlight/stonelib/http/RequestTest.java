package dev.anchorlight.stonelib.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<List<String>> lastToken = new AtomicReference<>();
    private final AtomicReference<List<String>> lastContentType = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastToken.set(exchange.getRequestHeaders().get("x-access-token"));
            lastContentType.set(exchange.getRequestHeaders().get("Content-Type"));
            exchange.getResponseHeaders().add("X-Reply", "yes");
            respond(exchange, 200, "{\"success\":true}");
        });
        server.createContext("/missing", exchange -> respond(exchange, 404, "{\"success\":false}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void postSendsBodyAndHeaders() {
        Response response = Request.builder()
                .setURL(baseUrl + "/echo")
                .setMethod(Request.Method.POST)
                .addHeader("x-access-token", "secret")
                .setRequestBody("{\"a\":1}")
                .build()
                .execute();

        assertEquals(200, response.getStatusCode());
        assertTrue(response.isSuccess());
        assertEquals("{\"success\":true}", response.getBody());
        assertEquals(List.of("yes"), response.getHeaders().get("x-reply"));
        assertEquals("POST", lastMethod.get());
        assertEquals("{\"a\":1}", lastBody.get());
        assertEquals(List.of("secret"), lastToken.get());
        assertEquals(List.of("application/json"), lastContentType.get());
    }

    @Test
    void getReturnsNonSuccessStatusWithoutThrowing() {
        Response response = Request.builder().setURL(baseUrl + "/missing").setMethod(Request.Method.GET).build().execute();
        assertEquals(404, response.getStatusCode());
        assertFalse(response.isSuccess());
        assertEquals("{\"success\":false}", response.getBody());
    }

    @Test
    void postWithoutBodySendsEmptyBody() {
        Request.builder().setURL(baseUrl + "/echo").setMethod(Request.Method.POST).build().execute();
        assertEquals("POST", lastMethod.get());
        assertEquals("", lastBody.get());
    }

    @Test
    void addedHeaderReplacesJsonDefault() {
        Request.builder().setURL(baseUrl + "/echo").setMethod(Request.Method.PUT)
                .addHeader("Content-Type", "text/plain").setRequestBody("hi").build().execute();
        assertEquals("PUT", lastMethod.get());
        assertEquals(List.of("text/plain"), lastContentType.get());
    }

    @Test
    void executeAsyncCompletesWithResponse() throws Exception {
        Response response = Request.builder().setURL(baseUrl + "/echo").setMethod(Request.Method.DELETE).build()
                .executeAsync().get(5, TimeUnit.SECONDS);
        assertEquals(200, response.getStatusCode());
        assertEquals("DELETE", lastMethod.get());
    }

    @Test
    void networkFailureThrows() {
        server.stop(0);
        Request request = Request.builder().setURL(baseUrl + "/echo").setMethod(Request.Method.GET).build();
        RuntimeException error = assertThrows(RuntimeException.class, request::execute);
        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void builderChangesAfterBuildDoNotLeakIntoRequest() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-access-token", "first");
        Request request = new Request(baseUrl + "/echo", Request.Method.GET, null, headers);
        headers.put("x-access-token", "second");
        request.execute();
        assertEquals(List.of("first"), lastToken.get());
    }

    @Test
    void builderValidatesRequiredFields() {
        assertThrows(IllegalStateException.class, () -> Request.builder().setMethod(Request.Method.GET).build());
        assertThrows(IllegalStateException.class, () -> Request.builder().setURL(baseUrl).build());
        assertThrows(IllegalArgumentException.class, () -> Request.builder().setURL(null));
        assertThrows(IllegalArgumentException.class, () -> Request.builder().addHeader("a", null));
        assertThrows(IllegalArgumentException.class,
                () -> Request.builder().setURL("not a uri").setMethod(Request.Method.GET).build());
    }
}
