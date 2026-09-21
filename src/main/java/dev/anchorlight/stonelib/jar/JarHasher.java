package dev.anchorlight.stonelib.jar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SHA-256 and SHA-512 digests of files on disk, cached by (path, size, last-modified) so a
 * repeated scan does not rehash files that have not changed.
 *
 * <p>Written for plugin JARs: identifying which build of a plugin is actually installed, verifying
 * a download against a published checksum, or noticing that a jar changed underneath you without
 * its version being bumped. A plugins folder holds dozens of files and a scan may run on a timer,
 * so the cache is the point — without it, every scan reads every jar twice.
 *
 * <p>No Bukkit types, so it is usable on a proxy and in plain unit tests. Thread-safe.
 *
 * <pre>{@code
 * JarHasher hasher = new JarHasher();
 * for (Path jar : jars) {
 *     JarHasher.HashResult result = hasher.hash(jar);
 *     if (result != null) {
 *         index.put(jar.getFileName().toString(), result.sha256());
 *     }
 * }
 * hasher.pruneMissing(currentPaths);
 * }</pre>
 */
public final class JarHasher {

    /** Both digests of a file, plus the size that was hashed. */
    public record HashResult(String sha256, String sha512, long fileSize) {
    }

    private record CacheKey(String path, long size, long modifiedAtMillis) {
    }

    private record CacheEntry(CacheKey key, HashResult result) {
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Hashes a file, reusing the cached result when its size and last-modified time are unchanged.
     *
     * @return the digests, or {@code null} when the file cannot be read — a jar being replaced
     *         while a scan walks the folder is ordinary, not exceptional
     */
    public HashResult hash(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            long size = Files.size(file);
            long modifiedAt = Files.getLastModifiedTime(file).toMillis();
            String pathKey = file.toAbsolutePath().normalize().toString();
            CacheKey key = new CacheKey(pathKey, size, modifiedAt);

            CacheEntry cached = cache.get(pathKey);
            if (cached != null && cached.key().equals(key)) {
                return cached.result();
            }

            HashResult result = computeHash(file, size);
            cache.put(pathKey, new CacheEntry(key, result));
            return result;
        } catch (IOException e) {
            return null;
        }
    }

    /** Drops cached entries for paths no longer present, so a shrinking folder does not leak. */
    public void pruneMissing(Iterable<String> currentPaths) {
        Objects.requireNonNull(currentPaths, "currentPaths");
        Set<String> keep = new HashSet<>();
        for (String path : currentPaths) {
            keep.add(path);
        }
        cache.keySet().removeIf(path -> !keep.contains(path));
    }

    public int cachedCount() {
        return cache.size();
    }

    private HashResult computeHash(Path file, long size) throws IOException {
        try {
            // One pass per algorithm rather than a tee stream: jars are small enough that the
            // second read is cheap, and the cache above means unchanged files are not read at all.
            return new HashResult(digest(file, "SHA-256"), digest(file, "SHA-512"), size);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Required digest algorithm unavailable", e);
        }
    }

    private static String digest(Path file, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestStream = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestStream.read(buffer) != -1) {
                // DigestInputStream updates the digest as bytes are read.
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
