package dev.anchorlight.stonelib.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFilesTest {

    @Test
    void resolvesNormalRelativePathWithinRoot(@TempDir Path root) {
        Path resolved = SafeFiles.resolveWithinRoot(root, "cache/original/post123/1.jpg");
        assertEquals(root.resolve("cache/original/post123/1.jpg").normalize(), resolved);
    }

    @Test
    void rejectsDotDotTraversal(@TempDir Path root) {
        assertThrows(SecurityException.class, () -> SafeFiles.resolveWithinRoot(root, "../../etc/passwd"));
    }

    @Test
    void rejectsAbsolutePathEscape(@TempDir Path root) {
        String absoluteElsewhere = root.getRoot().resolve("outside").toString();
        assertThrows(SecurityException.class, () -> SafeFiles.resolveWithinRoot(root, absoluteElsewhere));
    }

    @Test
    void rejectsDotDotEmbeddedDeeperInPath(@TempDir Path root) {
        assertThrows(SecurityException.class,
                () -> SafeFiles.resolveWithinRoot(root, "cache/../../escaped/file.jpg"));
    }

    @Test
    void atomicMoveCreatesDestinationDirectoriesAndMovesFile(@TempDir Path root) throws IOException {
        Path source = Files.writeString(root.resolve("source.txt"), "hello");
        Path destination = root.resolve("nested/dir/dest.txt");

        SafeFiles.atomicMove(source, destination);

        assertTrue(Files.exists(destination));
        assertEquals("hello", Files.readString(destination));
        assertTrue(Files.notExists(source));
    }

    @Test
    void safeNameKeepsOnlyBoringCharacters() {
        assertEquals("sunday_service.png", SafeFiles.safeName("Sunday Service.PNG"));
        assertEquals("_.._etc_passwd", SafeFiles.safeName("/../etc/passwd"));
        assertEquals("hidden", SafeFiles.safeName("...hidden"));
        assertEquals("_", SafeFiles.safeName(".."));
        assertEquals("_", SafeFiles.safeName(null));
        assertEquals(SafeFiles.MAX_NAME_LENGTH, SafeFiles.safeName("x".repeat(500)).length());
    }

    @Test
    void writeAtomicallyCreatesParentsAndLeavesNoTempFiles(@TempDir Path root) throws IOException {
        Path target = root.resolve("a/b/c.bin");
        SafeFiles.writeAtomically(target, new byte[]{1, 2, 3});
        SafeFiles.writeAtomically(target, new byte[]{4});
        assertEquals(1, Files.readAllBytes(target).length);
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count());
        }
    }
}
