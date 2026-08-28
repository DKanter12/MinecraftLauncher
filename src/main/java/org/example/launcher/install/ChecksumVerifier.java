package org.example.launcher.install;

import java.nio.file.Path;

/**
 * Verifies file integrity via checksum (SHA1).
 * <p>
 * Used to determine whether a local file matches the expected hash,
 * enabling skip-if-exists-and-valid logic during installation.
 */
public interface ChecksumVerifier {

    /**
     * Verifies that the file at {@code path} has the expected SHA1 hash.
     *
     * @param path         the local file to verify
     * @param expectedSha1 the expected SHA1 hex string (lowercase)
     * @return {@code true} if the file exists and its hash matches;
     *         {@code false} otherwise
     */
    boolean verify(Path path, String expectedSha1);
}
