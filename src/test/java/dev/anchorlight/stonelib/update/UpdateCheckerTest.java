package dev.anchorlight.stonelib.update;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void versionsCompareNumericallyAndIgnoreV() {
        assertEquals(0, Versions.compare("v1.1.0", "1.1"));
        assertTrue(Versions.compare("1.10.0", "1.9.9") > 0);
        assertTrue(Versions.compare("2.0.0-beta.1", "2.0.0") < 0);
        assertTrue(Versions.compare("2.0.0", "2.0.0-SNAPSHOT") > 0);
        assertEquals(0, Versions.compare("1.2.0+build.5", "1.2.0"));
    }

    @Test
    void parsesTheLatestRelease() {
        String json = "{\"html_url\":\"https://github.com/o/r/releases/tag/v1.2.0\",\"tag_name\":\"v1.2.0\"}";
        UpdateChecker.Result result = UpdateChecker.parse("1.1.0", json);
        assertTrue(result.succeeded());
        assertTrue(result.outdated());
        assertEquals("v1.2.0", result.latest());
        assertEquals("https://github.com/o/r/releases/tag/v1.2.0", result.url());
        assertFalse(UpdateChecker.parse("1.2.0", json).outdated());
    }

    @Test
    void failuresCompleteInsteadOfThrowing() {
        UpdateChecker checker = new UpdateChecker("o", "r", "1.0.0",
                uri -> CompletableFuture.failedFuture(new java.io.IOException("offline")));
        UpdateChecker.Result result = checker.check().join();
        assertFalse(result.succeeded());
        assertFalse(result.outdated());

        UpdateChecker throwing = new UpdateChecker("o", "r", "1.0.0", uri -> { throw new IllegalStateException(); });
        assertFalse(throwing.check().join().succeeded());
        assertFalse(UpdateChecker.parse("1.0.0", "{\"message\":\"Not Found\"}").succeeded());
    }
}
