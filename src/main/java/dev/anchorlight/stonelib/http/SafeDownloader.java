package dev.anchorlight.stonelib.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * SSRF-hardened async downloader for URLs the plugin did not write itself: images from an external
 * API, a URL an operator pasted into a web form, anything a third party can influence.
 *
 * <p>Redirects are followed manually (the injected {@link HttpClient} must be built with
 * {@link HttpClient.Redirect#NEVER}) so that every hop - the initial URL and each {@code Location}
 * target - is independently revalidated against {@link UrlSafety} before a connection is opened to
 * it. Validating only the first URL and then trusting the JDK's automatic redirect handling would
 * let a public-looking first hop redirect to an internal address on the second hop.
 *
 * <p>Response bodies are read incrementally and the byte count is checked as each chunk arrives,
 * so an oversized response is rejected mid-stream - a lying or absent {@code Content-Length} cannot
 * smuggle an unbounded body past the cap. Only the content types in {@link Limits#contentTypes()}
 * are accepted.
 *
 * <p>Failures complete the future exceptionally with an {@link IllegalArgumentException} (policy
 * rejections) or an {@link IOException} (transport), wrapped in a {@link CompletionException}.
 *
 * <pre>{@code
 * HttpClient client = HttpClient.newBuilder()
 *         .followRedirects(HttpClient.Redirect.NEVER)
 *         .connectTimeout(Duration.ofSeconds(5))
 *         .build();
 * SafeDownloader downloader = new SafeDownloader(client, executor, SafeDownloader.Limits.images(8_000_000));
 * downloader.download(URI.create(url)).thenAccept(result -> cache.store(result.sha256(), result.bytes()));
 * }</pre>
 *
 * <p>Ported from PixelPost's Instagram media downloader.
 */
public final class SafeDownloader {

    /** The common web image formats. */
    public static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /**
     * What a download may be.
     *
     * @param maxBytes     the largest body accepted, checked while streaming
     * @param timeout      per-request timeout (each redirect hop gets its own)
     * @param maxRedirects how many redirects are followed before giving up
     * @param contentTypes accepted {@code Content-Type} values, lower case, without parameters
     */
    public record Limits(long maxBytes, Duration timeout, int maxRedirects, Set<String> contentTypes) {

        public Limits {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("maxBytes must be positive");
            }
            if (maxRedirects < 0) {
                throw new IllegalArgumentException("maxRedirects must not be negative");
            }
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
            contentTypes = Set.copyOf(contentTypes);
        }

        /** Images only, a 10 second timeout per hop and at most 3 redirects. */
        public static Limits images(long maxBytes) {
            return new Limits(maxBytes, Duration.ofSeconds(10), 3, IMAGE_CONTENT_TYPES);
        }

        public Limits withTimeout(Duration newTimeout) {
            return new Limits(maxBytes, newTimeout, maxRedirects, contentTypes);
        }

        public Limits withMaxRedirects(int newMaxRedirects) {
            return new Limits(maxBytes, timeout, newMaxRedirects, contentTypes);
        }
    }

    /** A downloaded body, its lower-case hex SHA-256 and its content type. */
    public record Result(byte[] bytes, String sha256, String contentType) {
    }

    private final HttpClient httpClient;
    private final Executor executor;
    private final Limits limits;
    private final boolean enforceAddressChecks;
    private final boolean allowHttp;
    private final Predicate<InetAddress> addressBlocked;

    /** HTTPS only, with full host and resolved-address validation on every hop. */
    public SafeDownloader(HttpClient httpClient, Executor executor, Limits limits) {
        this(httpClient, executor, limits, true, false, UrlSafety::isAddressBlocked);
    }

    private SafeDownloader(HttpClient httpClient, Executor executor, Limits limits,
                           boolean enforceAddressChecks, boolean allowHttp, Predicate<InetAddress> addressBlocked) {
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException(
                    "SafeDownloader requires an HttpClient built with HttpClient.Redirect.NEVER: per-hop "
                            + "revalidation depends on redirects never being followed by the JDK, but this client "
                            + "uses " + httpClient.followRedirects());
        }
        this.httpClient = httpClient;
        this.executor = executor;
        this.limits = limits;
        this.enforceAddressChecks = enforceAddressChecks;
        this.allowHttp = allowHttp;
        this.addressBlocked = addressBlocked;
    }

    /**
     * <b>Tests only.</b> Disables every host and address check and allows plain HTTP, so size,
     * content-type and redirect-limit behaviour can be exercised against a local stub server. Never
     * wire this into production code.
     */
    public static SafeDownloader forTesting(HttpClient httpClient, Executor executor, Limits limits) {
        return new SafeDownloader(httpClient, executor, limits, false, true, address -> false);
    }

    /**
     * <b>Tests only.</b> Keeps per-hop address validation switched on but lets the test choose
     * which resolved address counts as blocked (a local stub server always lives on loopback, which
     * the real check rejects) and whether plain HTTP is allowed.
     */
    public static SafeDownloader forTesting(HttpClient httpClient, Executor executor, Limits limits,
                                            boolean allowHttp, Predicate<InetAddress> addressBlocked) {
        return new SafeDownloader(httpClient, executor, limits, true, allowHttp, addressBlocked);
    }

    public Limits limits() {
        return limits;
    }

    /** Downloads {@code source} on the executor. Never throws; failures complete the future exceptionally. */
    public CompletableFuture<Result> download(URI source) {
        return CompletableFuture.supplyAsync(() -> downloadWithRedirects(source, 0), executor);
    }

    private Result downloadWithRedirects(URI uri, int redirectCount) {
        try {
            validateHop(uri);

            HttpRequest request = HttpRequest.newBuilder(uri).timeout(limits.timeout()).GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                drainAndClose(response);
                if (redirectCount >= limits.maxRedirects()) {
                    throw new IllegalArgumentException("Too many redirects fetching " + uri.getPath());
                }
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IllegalArgumentException("Redirect with no Location header"));
                return downloadWithRedirects(uri.resolve(location), redirectCount + 1);
            }
            if (status != 200) {
                drainAndClose(response);
                throw new IllegalArgumentException("Unexpected HTTP status " + status + " for " + uri.getPath());
            }

            String contentType = response.headers().firstValue("Content-Type")
                    .map(v -> v.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("");
            if (!limits.contentTypes().contains(contentType)) {
                drainAndClose(response);
                throw new IllegalArgumentException("Rejected content-type: " + contentType);
            }

            byte[] body = readCapped(response.body(), limits.maxBytes(),
                    System.nanoTime() + limits.timeout().toNanos());
            return new Result(body, sha256Hex(body), contentType);
        } catch (IOException e) {
            throw new CompletionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        }
    }

    /**
     * Reads the body, checking the running total against the cap after every chunk, and giving up
     * once {@code deadlineNanos} has passed (the request timeout only covers the headers, so a
     * server trickling bytes could otherwise hold the thread indefinitely).
     */
    static byte[] readCapped(InputStream in, long maxBytes, long deadlineNanos) throws IOException {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Response exceeded the " + maxBytes + " byte limit");
                }
                if (System.nanoTime() > deadlineNanos) {
                    throw new IllegalArgumentException("Response body took longer than the timeout to arrive");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /** Lower-case hex SHA-256 of {@code data}. */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Discards a body we do not want. It is closed, never read: reading it to the end would let a
     * hostile server stream an unbounded redirect or error body past the size cap.
     */
    private static void drainAndClose(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    private void validateHop(URI uri) {
        if (!UrlSafety.isSchemeAllowed(uri, allowHttp)) {
            throw new IllegalArgumentException("Blocked scheme: " + uri.getScheme());
        }
        if (UrlSafety.isHostBlocked(uri.getHost())) {
            throw new IllegalArgumentException("Blocked host: " + uri.getHost());
        }
        if (enforceAddressChecks) {
            InetAddress resolved;
            try {
                resolved = InetAddress.getByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Could not resolve host: " + uri.getHost());
            }
            if (addressBlocked.test(resolved)) {
                throw new IllegalArgumentException("Blocked address for host: " + uri.getHost());
            }
        }
    }
}
