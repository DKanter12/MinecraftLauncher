package org.example.launcher.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Sha1ChecksumVerifier")
class Sha1ChecksumVerifierTest {

    private final Sha1ChecksumVerifier verifier = new Sha1ChecksumVerifier();

    // SHA1 of "hello" = aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
    private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

    @Test
    @DisplayName("returns true when file hash matches expected")
    void verifiesCorrectHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        assertTrue(verifier.verify(file, HELLO_SHA1));
    }

    @Test
    @DisplayName("returns false when file hash does not match")
    void rejectsWrongHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        assertFalse(verifier.verify(file, "0000000000000000000000000000000000000000"));
    }

    @Test
    @DisplayName("returns false when file does not exist")
    void rejectsNonExistentFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nonexistent.txt");
        assertFalse(verifier.verify(file, HELLO_SHA1));
    }

    @Test
    @DisplayName("returns false for null path")
    void rejectsNullPath() {
        assertFalse(verifier.verify(null, HELLO_SHA1));
    }

    @Test
    @DisplayName("returns false for null or blank hash")
    void rejectsNullHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        assertFalse(verifier.verify(file, null));
        assertFalse(verifier.verify(file, ""));
        assertFalse(verifier.verify(file, "   "));
    }

    @Test
    @DisplayName("hash comparison is case-insensitive")
    void caseInsensitiveHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        assertTrue(verifier.verify(file, HELLO_SHA1.toUpperCase()));
    }

    @Test
    @DisplayName("hash comparison trims whitespace")
    void trimsHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        assertTrue(verifier.verify(file, "  " + HELLO_SHA1 + "  "));
    }
}
