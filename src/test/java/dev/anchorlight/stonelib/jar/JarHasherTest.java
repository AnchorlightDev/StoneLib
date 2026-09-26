package dev.anchorlight.stonelib.jar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JarHasherTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void hashesAFileWithKnownDigests(@TempDir Path dir) throws IOException {
        Path file = write(dir, "a.jar", "abc");
        JarHasher.HashResult result = new JarHasher().hash(file);

        // The published SHA-256 and SHA-512 of "abc".
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256());
        assertEquals(
                "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                result.sha512());
        assertEquals(3, result.fileSize());
    }

    @Test
    void differentContentHashesDifferently(@TempDir Path dir) throws IOException {
        JarHasher hasher = new JarHasher();
        String one = hasher.hash(write(dir, "a.jar", "alpha")).sha256();
        String two = hasher.hash(write(dir, "b.jar", "beta")).sha256();
        assertNotEquals(one, two);
    }

    @Test
    void reusesTheCachedResultForAnUnchangedFile(@TempDir Path dir) throws IOException {
        Path file = write(dir, "a.jar", "abc");
        JarHasher hasher = new JarHasher();

        JarHasher.HashResult first = hasher.hash(file);
        JarHasher.HashResult second = hasher.hash(file);

        // Same instance, so the file genuinely was not read a second time.
        assertSame(first, second);
        assertEquals(1, hasher.cachedCount());
    }

    @Test
    void rehashesWhenTheContentChanges(@TempDir Path dir) throws IOException {
        Path file = write(dir, "a.jar", "abc");
        JarHasher hasher = new JarHasher();
        String before = hasher.hash(file).sha256();

        Files.writeString(file, "abcd");
        String after = hasher.hash(file).sha256();

        assertNotEquals(before, after);
    }

    @Test
    void rehashesWhenOnlyTheTimestampChanges(@TempDir Path dir) throws IOException {
        // A jar swapped for a different build of the same size is the case the
        // cache key exists to catch; size alone would miss it.
        Path file = write(dir, "a.jar", "abcd");
        JarHasher hasher = new JarHasher();
        JarHasher.HashResult first = hasher.hash(file);

        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));
        JarHasher.HashResult second = hasher.hash(file);

        assertNotSameInstance(first, second);
        assertEquals(first.sha256(), second.sha256());
    }

    @Test
    void returnsNullForAFileThatCannotBeRead(@TempDir Path dir) {
        // A jar being replaced while a scan walks the folder is ordinary.
        assertNull(new JarHasher().hash(dir.resolve("missing.jar")));
    }

    @Test
    void prunesCachedEntriesForPathsThatAreGone(@TempDir Path dir) throws IOException {
        Path kept = write(dir, "kept.jar", "one");
        Path removed = write(dir, "removed.jar", "two");

        JarHasher hasher = new JarHasher();
        hasher.hash(kept);
        hasher.hash(removed);
        assertEquals(2, hasher.cachedCount());

        hasher.pruneMissing(List.of(kept.toAbsolutePath().normalize().toString()));
        assertEquals(1, hasher.cachedCount());
    }

    @Test
    void pruningEverythingEmptiesTheCache(@TempDir Path dir) throws IOException {
        JarHasher hasher = new JarHasher();
        hasher.hash(write(dir, "a.jar", "one"));

        hasher.pruneMissing(List.of());
        assertEquals(0, hasher.cachedCount());
    }

    private static void assertNotSameInstance(Object a, Object b) {
        if (a == b) {
            throw new AssertionError("expected the file to be rehashed rather than served from cache");
        }
    }
}
