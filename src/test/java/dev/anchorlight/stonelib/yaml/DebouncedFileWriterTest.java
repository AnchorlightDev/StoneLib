package dev.anchorlight.stonelib.yaml;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncedFileWriterTest {

    private File file(String name) throws IOException {
        Path dir = Path.of("target", "debounced-writer-test");
        Files.createDirectories(dir);
        Path path = dir.resolve(name);
        Files.deleteIfExists(path);
        return path.toFile();
    }

    @Test
    void burstsCoalesceIntoOneWrite() throws Exception {
        File file = file("burst.yml");
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger value = new AtomicInteger();
        try (DebouncedFileWriter writer = new DebouncedFileWriter(file,
                () -> { writes.incrementAndGet(); return "value: " + value.get() + "\n"; },
                Duration.ofMillis(200), Logger.getAnonymousLogger())) {
            for (int i = 1; i <= 50; i++) {
                value.set(i);
                writer.markDirty();
            }
            assertTrue(writer.hasPendingWrite());
            Thread.sleep(600);
            assertEquals(1, writes.get());
            assertEquals("value: 50\n", Files.readString(file.toPath()));
        }
    }

    @Test
    void flushWritesSynchronouslyAndCancelsThePendingWrite() throws Exception {
        File file = file("flush.yml");
        AtomicInteger writes = new AtomicInteger();
        DebouncedFileWriter writer = new DebouncedFileWriter(file,
                () -> { writes.incrementAndGet(); return "ok\n"; }, Duration.ofSeconds(30), Logger.getAnonymousLogger());
        writer.markDirty();
        assertTrue(writer.flush());
        assertFalse(writer.hasPendingWrite());
        assertEquals("ok\n", Files.readString(file.toPath()));
        writer.close();
        assertEquals(2, writes.get(), "close flushes once more");
        writer.markDirty();
        assertFalse(writer.hasPendingWrite(), "changes after close are ignored");
    }
}
