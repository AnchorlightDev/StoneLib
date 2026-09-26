package dev.anchorlight.stonelib.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * An async JSON client for a plugin's companion web API.
 *
 * <p>Every call runs on the JDK {@link HttpClient}'s own executor, so it is safe to call from the
 * main thread, and <b>never throws or completes exceptionally</b>: failures are logged, recorded on
 * the {@link ConnectionHealth}, and reported through the return value. Pair it with a
 * {@link RetryQueue} for delivery that survives an outage. Bodies are plain strings, so it works
 * with whatever JSON library the plugin already shades.
 *
 * <pre>{@code
 * ApiClient api = ApiClient.builder("https://example.com/api")
 *         .bearerToken(config.token())
 *         .header("X-Server-Id", config.serverId())
 *         .logger(getLogger())
 *         .build();
 *
 * api.post("/api/events", gson.toJson(event)).thenAccept(ok -> { if (!ok) retries.offer(event); });
 * api.get("/api/vote/current").thenAccept(body -> { if (body != null) ... });
 * }</pre>
 */
public final class ApiClient {

    /** A completed HTTP exchange. */
    public record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    private static final int LOGGED_BODY_LIMIT = 300;

    private final String baseUrl;
    private final Duration requestTimeout;
    private final Map<String, String> headers;
    private final ConnectionHealth health;
    private final Logger logger;
    private final boolean debug;
    private final HttpClient http;

    private ApiClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.requestTimeout = builder.requestTimeout;
        this.headers = Map.copyOf(builder.headers);
        this.health = builder.health;
        this.logger = builder.logger;
        this.debug = builder.debug;
        this.http = HttpClient.newBuilder().connectTimeout(builder.connectTimeout).build();
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    public ConnectionHealth health() {
        return health;
    }

    /** POSTs a JSON body. Completes with true only for a 2xx response. */
    public CompletableFuture<Boolean> post(String path, String json) {
        return exchange("POST", path, json, true).thenApply(response -> response.map(Response::isSuccess).orElse(false));
    }

    /** GETs a path. Completes with the body of a 2xx response, or null otherwise. */
    public CompletableFuture<String> get(String path) {
        return exchange("GET", path, null, true)
                .thenApply(response -> response.filter(Response::isSuccess).map(Response::body).orElse(null));
    }

    /**
     * Sends a request and completes with the response whatever its status - for endpoints that
     * explain rejections in the body. Empty only when no response arrived (network error, timeout).
     * Non-2xx statuses are not logged or counted as failures here; that is the caller's call.
     */
    public CompletableFuture<Optional<Response>> send(String method, String path, String json) {
        return exchange(method, path, json, false);
    }

    private CompletableFuture<Optional<Response>> exchange(String method, String path, String json, boolean statusIsFailure) {
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl(baseUrl, path)))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json");
            headers.forEach(builder::header);
            builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            request = builder.build();
        } catch (RuntimeException e) {
            warn("Failed to build " + method + " " + path + ": " + e.getMessage());
            return CompletableFuture.completedFuture(Optional.empty());
        }

        debug(method + " " + path);
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        health.markFailure();
                        warn(method + " " + path + " failed: " + error.getMessage());
                        return Optional.<Response>empty();
                    }
                    Response result = new Response(response.statusCode(), response.body());
                    if (statusIsFailure && !result.isSuccess()) {
                        health.markFailure();
                        warn(method + " " + path + " returned HTTP " + result.status() + ": " + truncate(result.body()));
                    } else {
                        health.markSuccess();
                        debug(method + " " + path + " completed (" + result.status() + ")");
                    }
                    return Optional.of(result);
                });
    }

    /**
     * Joins a base URL and a path. A base ending in {@code /api} combined with a path starting
     * {@code /api/} is not doubled, so either style of configured base URL works.
     */
    public static String resolveUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalisedPath = path.startsWith("/") ? path : "/" + path;
        if (base.endsWith("/api") && normalisedPath.startsWith("/api/")) {
            return base.substring(0, base.length() - 4) + normalisedPath;
        }
        return base + normalisedPath;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > LOGGED_BODY_LIMIT ? body.substring(0, LOGGED_BODY_LIMIT) + "..." : body;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    private void debug(String message) {
        if (debug && logger != null) {
            logger.info("[debug] " + message);
        }
    }

    public static final class Builder {
        private final String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private final Map<String, String> headers = new LinkedHashMap<>();
        private ConnectionHealth health = new ConnectionHealth();
        private Logger logger;
        private boolean debug;

        private Builder(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** Adds {@code Authorization: Bearer <token>}. */
        public Builder bearerToken(String token) {
            return header("Authorization", "Bearer " + token);
        }

        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /** Share one health object between clients (REST plus a WebSocket, say). */
        public Builder health(ConnectionHealth health) {
            this.health = Objects.requireNonNull(health, "health");
            return this;
        }

        /** Where warnings go. Without one, failures are silent. */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /** Also log every request and response at INFO, prefixed {@code [debug]}. */
        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public ApiClient build() {
            return new ApiClient(this);
        }
    }
}
