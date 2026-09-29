package dev.anchorlight.stonelib.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks GitHub for a repository's latest release and compares it to the running version.
 *
 * <p>The request is asynchronous and never throws: an offline server, a rate limit or a renamed
 * repository all complete with {@link Result#failed}, so a startup check can never stop a plugin
 * from enabling. Redirects are followed, which matters because GitHub answers a transferred
 * repository's old name with a 301.
 *
 * <pre>{@code
 * new UpdateChecker("AnchorlightDev", "ConsentPvP", getPluginMeta().getVersion())
 *         .check()
 *         .thenAccept(result -> {
 *             if (result.outdated()) {
 *                 getLogger().warning("Version " + result.latest() + " is available: " + result.url());
 *             }
 *         });
 * }</pre>
 *
 * <p>The callback runs on an HTTP client thread. Hop to the right scheduler before touching
 * players or the world.
 */
public final class UpdateChecker {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

    /** The outcome of a check. */
    public record Result(String current, String latest, String url, boolean outdated, String error) {

        public static Result failed(String current, String error) {
            return new Result(current, null, null, false, error);
        }

        public boolean succeeded() {
            return error == null;
        }
    }

    private final String owner;
    private final String repository;
    private final String currentVersion;
    private final Function<URI, CompletableFuture<String>> fetcher;

    public UpdateChecker(String owner, String repository, String currentVersion) {
        this(owner, repository, currentVersion, UpdateChecker::fetch);
    }

    /** For tests, or another transport: {@code fetcher} returns the response body for a URI. */
    public UpdateChecker(String owner, String repository, String currentVersion,
                         Function<URI, CompletableFuture<String>> fetcher) {
        this.owner = owner;
        this.repository = repository;
        this.currentVersion = currentVersion;
        this.fetcher = fetcher;
    }

    public URI endpoint() {
        return URI.create("https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest");
    }

    public CompletableFuture<Result> check() {
        CompletableFuture<String> body;
        try {
            body = fetcher.apply(endpoint());
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(Result.failed(currentVersion, ex.toString()));
        }
        return body.handle((json, error) -> {
            if (error != null) {
                return Result.failed(currentVersion, error.toString());
            }
            return parse(currentVersion, json);
        });
    }

    /** Reads a GitHub "latest release" response. Exposed for tests. */
    public static Result parse(String currentVersion, String json) {
        if (json == null) {
            return Result.failed(currentVersion, "empty response");
        }
        Matcher tag = TAG.matcher(json);
        if (!tag.find()) {
            return Result.failed(currentVersion, "no tag_name in response");
        }
        String latest = tag.group(1);
        Matcher url = URL.matcher(json);
        String link = url.find() ? url.group(1) : null;
        return new Result(currentVersion, latest, link, Versions.compare(latest, currentVersion) > 0, null);
    }

    private static CompletableFuture<String> fetch(URI uri) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "StoneLib-UpdateChecker")
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return response.body();
                });
    }
}
