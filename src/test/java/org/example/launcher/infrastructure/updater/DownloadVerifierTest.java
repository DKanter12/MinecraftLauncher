package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.LauncherVersion;

@DisplayName("DownloadVerifier")
class DownloadVerifierTest {

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(data)) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static LauncherVersion version(String hash, long size) {
        return new LauncherVersion("1.2.0", null, "https://example.com/l.zip",
                size, hash, "notes");
    }

    @Test
    @DisplayName("correct file passes size and hash")
    void correct(@TempDir Path dir) throws Exception {
        byte[] data = "update-bytes".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("l.zip");
        Files.write(file, data);

        DownloadVerifier.verifySize(file, data.length);
        DownloadVerifier.verifyHash(file, sha256(data));
        DownloadVerifier.verify(file, version(sha256(data), data.length));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("unknown size is skipped")
    void unknownSize(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("l.zip");
        Files.writeString(file, "data");

        DownloadVerifier.verifySize(file, 0);
    }

    @Test
    @DisplayName("wrong size fails")
    void wrongSize(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("l.zip");
        Files.writeString(file, "data");

        assertThrows(IOException.class,
                () -> DownloadVerifier.verifySize(file, 999));
    }

    @Test
    @DisplayName("wrong hash fails and deletes the file")
    void wrongHash(@TempDir Path dir) throws Exception {
        byte[] data = "update-bytes".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("l.zip");
        Files.write(file, data);

        assertThrows(IOException.class, () -> DownloadVerifier.verify(
                file, version("0".repeat(64), data.length)));
        assertFalse(Files.exists(file),
                "corrupt package must be deleted");
    }

    @Test
    @DisplayName("missing file fails")
    void missing(@TempDir Path dir) {
        assertThrows(IOException.class, () -> DownloadVerifier.verify(
                dir.resolve("nope.zip"), version("0".repeat(64), 10)));
    }
}
