package dev.anchorlight.stonelib.file;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * File helpers for paths that come from outside the plugin: an uploaded file name, an id read from
 * an API, a folder name typed into a command.
 *
 * <ul>
 *   <li>{@link #resolveWithinRoot} refuses any path that would land outside its root
 *       ({@code ../../config.yml}, an absolute path), lexically and before touching the disk.</li>
 *   <li>{@link #safeName} turns an arbitrary string into a single, boring path segment.</li>
 *   <li>{@link #writeAtomically} and {@link #atomicMove} never leave a half-written file where a
 *       reader can see it.</li>
 * </ul>
 *
 * <pre>{@code
 * Path slide = SafeFiles.resolveWithinRoot(slidesDir, SafeFiles.safeName(deck) + "/" + SafeFiles.safeName(upload));
 * SafeFiles.writeAtomically(slide, bytes);
 * }</pre>
 *
 * <p>Ported from PixelPost's media cache.
 */
public final class SafeFiles {

    /** Longest name {@link #safeName} returns. */
    public static final int MAX_NAME_LENGTH = 64;

    private SafeFiles() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /**
     * Resolves {@code relative} against {@code root} and returns the normalised result, or throws
     * {@link SecurityException} if it escapes the root. The check is purely lexical: symlinks
     * inside the root are not followed.
     */
    public static Path resolveWithinRoot(Path root, String relative) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(relative).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new SecurityException("Path escapes root: " + relative);
        }
        return candidate;
    }

    /**
     * Reduces {@code raw} to a single safe path segment: lower case ASCII letters, digits,
     * {@code .}, {@code -} and {@code _}; everything else becomes {@code _}. Leading dots are
     * stripped (no hidden files, no {@code ..}) and the result is capped at
     * {@value #MAX_NAME_LENGTH} characters. Returns {@code "_"} when nothing usable is left.
     */
    public static String safeName(String raw) {
        if (raw == null) {
            return "_";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_NAME_LENGTH));
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if (out.length() >= MAX_NAME_LENGTH) {
                break;
            }
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            out.append(ok ? c : '_');
        }
        int start = 0;
        while (start < out.length() && out.charAt(start) == '.') {
            start++;
        }
        String result = out.substring(start);
        return result.isEmpty() ? "_" : result;
    }

    /**
     * Moves {@code source} over {@code destination}, atomically where the file system supports it,
     * creating the destination's parent directories first.
     */
    public static void atomicMove(Path source, Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (FileSystemException atomicUnsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Writes {@code data} to a temporary sibling and then moves it over {@code destination}. */
    public static void writeAtomically(Path destination, byte[] data) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".tmp-", ".part");
        try {
            Files.write(temp, data);
            atomicMove(temp, destination);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
