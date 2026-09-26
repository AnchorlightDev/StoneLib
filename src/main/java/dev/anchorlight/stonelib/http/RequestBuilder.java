package dev.anchorlight.stonelib.http;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds a {@link Request}. Obtain one with {@link Request#builder()}. */
public final class RequestBuilder {
    private String url;
    private Request.Method requestMethod;
    private String requestBody;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public RequestBuilder() {
    }

    /**
     * Adds a header. {@code Accept} and {@code Content-Type} already default to JSON; setting
     * either here replaces the default.
     *
     * @throws IllegalArgumentException if the header or value is null
     */
    public RequestBuilder addHeader(String header, String value) {
        if (header == null) {
            throw new IllegalArgumentException("Header cannot be null.");
        } else if (value == null) {
            throw new IllegalArgumentException("Header value cannot be null.");
        }
        headers.put(header, value);
        return this;
    }

    /**
     * Sets the url. Its validity is checked in {@link #build()}.
     *
     * @throws IllegalArgumentException if the url is null
     */
    public RequestBuilder setURL(String url) {
        if (url == null) {
            throw new IllegalArgumentException("url cannot be null.");
        }
        this.url = url;
        return this;
    }

    /** @throws IllegalArgumentException if the method is null */
    public RequestBuilder setMethod(Request.Method requestMethod) {
        if (requestMethod == null) {
            throw new IllegalArgumentException("requestMethod cannot be null.");
        }
        this.requestMethod = requestMethod;
        return this;
    }

    /**
     * Sets the request body, for methods that carry one such as POST. Without one the request is
     * sent with an empty body.
     *
     * @throws IllegalArgumentException if the body is null
     */
    public RequestBuilder setRequestBody(String body) {
        if (body == null) {
            throw new IllegalArgumentException("body cannot be null.");
        }
        this.requestBody = body;
        return this;
    }

    /**
     * @throws IllegalStateException    if the url or method has not been set
     * @throws IllegalArgumentException if the url is not a valid URI
     */
    public Request build() {
        if (url == null) {
            throw new IllegalStateException("url is required.");
        } else if (requestMethod == null) {
            throw new IllegalStateException("requestMethod is required.");
        }
        return new Request(url, requestMethod, requestBody, headers);
    }
}
