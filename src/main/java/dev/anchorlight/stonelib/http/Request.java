package dev.anchorlight.stonelib.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A single JSON request, built with {@link #builder()}. Ported from ModularEnigma's Requests
 * library with the same API, so migrating is an import change from {@code io.github.ModularEnigma}
 * to {@code dev.anchorlight.stonelib.http}.
 *
 * <p>{@link #execute()} blocks and throws on network failure, so call it off the main thread (or
 * use {@link #executeAsync()}). For a plugin's own companion API, prefer {@link ApiClient}, which is
 * async, never throws and tracks {@link ConnectionHealth}.
 *
 * <pre>{@code
 * Response response = Request.builder()
 *         .setURL(baseUrl + "/api/user/create")
 *         .setMethod(Request.Method.POST)
 *         .addHeader("x-access-token", token)
 *         .setRequestBody(json)
 *         .build()
 *         .execute();
 *
 * if (response.getStatusCode() == 200) { ... response.getBody() ... }
 * }</pre>
 */
public final class Request {

    public enum Method {
        GET, POST, PUT, PATCH, DELETE
    }

    // One client for every request: a client per request leaks selector threads until OOM.
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String url;
    private final URI uri;
    private final Method requestMethod;
    private final String requestBody;
    private final Map<String, String> headers;

    public static RequestBuilder builder() {
        return new RequestBuilder();
    }

    /**
     * @param requestBody may be null, in which case the request is sent without a body
     * @param headers     extra headers; may override the default {@code Accept} and {@code Content-Type}
     * @throws IllegalArgumentException if the url or method is null, or the url is not a valid URI
     */
    public Request(String url, Method requestMethod, String requestBody, Map<String, String> headers) {
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null");
        } else if (requestMethod == null) {
            throw new IllegalArgumentException("Request method must be set");
        }

        try {
            this.uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URI is not valid as per RFC 2396: " + url, e);
        }

        this.url = url;
        this.requestMethod = requestMethod;
        this.requestBody = requestBody;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Sends the request and waits for the response.
     *
     * @return the response's status code, body and headers, whatever the status
     * @throws RuntimeException if the request could not be sent or no response arrived
     */
    public Response execute() {
        try {
            return new Response(CLIENT.send(toHttpRequest(), HttpResponse.BodyHandlers.ofString()));
        } catch (IOException e) {
            throw new RuntimeException("Exception raised while sending request: " + requestMethod + " " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending request: " + requestMethod + " " + url, e);
        }
    }

    /** Sends the request without blocking. Completes exceptionally if no response arrived. */
    public CompletableFuture<Response> executeAsync() {
        return CLIENT.sendAsync(toHttpRequest(), HttpResponse.BodyHandlers.ofString()).thenApply(Response::new);
    }

    private HttpRequest toHttpRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        headers.forEach(builder::setHeader);
        builder.method(requestMethod.name(), requestBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody));
        return builder.build();
    }

    public String getUrl() {
        return url;
    }

    public Method getMethod() {
        return requestMethod;
    }

    @Override
    public String toString() {
        return "Request:\n" + requestMethod + " " + url + "\n" +
                "Body: " + requestBody + "\n";
    }
}
