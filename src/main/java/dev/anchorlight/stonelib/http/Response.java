package dev.anchorlight.stonelib.http;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/** The result of {@link Request#execute()}: status code, body and headers, whatever the status. */
public final class Response {
    private final String body;
    private final int statusCode;
    private final Map<String, List<String>> headers;

    public Response(HttpResponse<String> response) {
        this(response.statusCode(), response.body(), response.headers().map());
    }

    public Response(int statusCode, String body, Map<String, List<String>> headers) {
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers == null ? Map.of() : headers;
    }

    /** The response body. May be empty. */
    public String getBody() {
        return body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /** True for a 2xx status. */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** Response headers, keyed by lower-case name. */
    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}
