package dev.anchorlight.stonelib.http;

import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeDownloaderTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startStubServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.jpg", exchange -> {
            byte[] body = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/too-big.jpg", exchange -> {
            byte[] body = new byte[2048];
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/not-image.html", exchange -> {
            byte[] body = "<html></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect-hop1", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/redirect-hop2");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-hop2", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.2:1/loopback-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    @Test
    void downloadsAndHashesAllowedContent() throws Exception {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = SafeDownloader.forTesting(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        SafeDownloader.Result result = service.download(URI.create("http://127.0.0.1:" + port + "/image.jpg")).get();

        assertEquals("image/jpeg", result.contentType());
        assertEquals(15, result.bytes().length);
        assertEquals(64, result.sha256().length());
    }

    @Test
    void rejectsResponsesLargerThanMaxDownloadBytes() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = SafeDownloader.forTesting(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> service.download(URI.create("http://127.0.0.1:" + port + "/too-big.jpg")).get());
        assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    void rejectsNonImageContentType() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = SafeDownloader.forTesting(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> service.download(URI.create("http://127.0.0.1:" + port + "/not-image.html")).get());
        assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    void rejectsRedirectChainWhoseSecondHopTargetsBlockedAddress() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        // forTesting() (used elsewhere in this file) disables SSRF address checks entirely,
        // which would make this test pass vacuously. Real production checks can't be exercised
        // end-to-end here either, since the stub server itself lives at a loopback address that
        // isAddressBlocked() always rejects. So this uses the package-private test seam that
        // keeps real per-hop address revalidation switched ON but substitutes which address counts
        // as "safe" (127.0.0.1, standing in for the stub server / a real public-looking host) versus
        // "blocked" (127.0.0.2, standing in for an internal address a redirect could point to) --
        // this proves each hop's resolved address is independently checked, not just the first.
        SafeDownloader service = SafeDownloader.forTesting(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
            Executors.newSingleThreadExecutor(), config,
            true, addr -> !addr.getHostAddress().equals("127.0.0.1"));

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> service.download(URI.create("http://127.0.0.1:" + port + "/redirect-hop1")).get());
        assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    void rejectsRedirectLimitExceeded() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = SafeDownloader.forTesting(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> service.download(URI.create("http://127.0.0.1:" + port + "/redirect-loop")).get());
        assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    void rejectsBlockedLoopbackHostWhenUsingProductionConstructor() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = new SafeDownloader(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        assertThrows(ExecutionException.class,
            () -> service.download(URI.create("https://127.0.0.1:" + port + "/image.jpg")).get());
    }

    @Test
    void httpsOnlyRejectsPlainHttpUrlUnderProductionConstructor() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        SafeDownloader service = new SafeDownloader(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), Executors.newSingleThreadExecutor(), config);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> service.download(URI.create("http://127.0.0.1:" + port + "/image.jpg")).get());
        assertEquals(IllegalArgumentException.class, ex.getCause().getClass());
    }

    @Test
    void constructorRejectsHttpClientThatDoesNotDisableRedirects() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

        assertThrows(IllegalArgumentException.class,
            () -> new SafeDownloader(client, Executors.newSingleThreadExecutor(), config));
    }

    @Test
    void forTestingFactoryRejectsHttpClientThatDoesNotDisableRedirects() {
        SafeDownloader.Limits config = new SafeDownloader.Limits(1024, Duration.ofSeconds(5), 3, SafeDownloader.IMAGE_CONTENT_TYPES);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        assertThrows(IllegalArgumentException.class,
            () -> SafeDownloader.forTesting(client, Executors.newSingleThreadExecutor(), config));
    }

    @Test
    void sha256HexMatchesKnownVector() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SafeDownloader.sha256Hex(new byte[0]));
    }

    @Test
    void limitsRejectNonPositiveMaxBytes() {
        assertThrows(IllegalArgumentException.class, () -> SafeDownloader.Limits.images(0));
    }

    @Test
    void readCappedGivesUpAfterTheDeadline() {
        java.io.InputStream endless = new java.io.InputStream() {
            @Override
            public int read() {
                return 1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return 1;
            }
        };
        assertThrows(IllegalArgumentException.class,
            () -> SafeDownloader.readCapped(endless, Long.MAX_VALUE, System.nanoTime() - 1));
    }
}
