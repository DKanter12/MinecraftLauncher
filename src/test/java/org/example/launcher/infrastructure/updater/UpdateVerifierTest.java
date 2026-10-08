package org.example.launcher.infrastructure.updater;

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

@DisplayName("UpdateVerifier")
class UpdateVerifierTest {

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest(data)) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    @Test
    @DisplayName("matching checksum passes")
    void matching(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("update.zip");
        byte[] data = "fake-update".getBytes(StandardCharsets.UTF_8);
        Files.write(file, data);

        UpdateVerifier.verifySha256(file, sha256(data));
    }

    @Test
    @DisplayName("mismatching checksum fails")
    void mismatch(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("update.zip");
        Files.writeString(file, "fake-update");

        assertThrows(IOException.class,
                () -> UpdateVerifier.verifySha256(file, "deadbeef"));
    }

    @Test
    @DisplayName("missing file fails")
    void missing(@TempDir Path dir) {
        assertThrows(IOException.class, () -> UpdateVerifier.verifySha256(
                dir.resolve("nope.zip"), "deadbeef"));
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

        UpdateVerifier.verifySize(file, data.length);
        UpdateVerifier.verify(file, version(sha256(data), data.length));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @DisplayName("unknown size is skipped")
    void unknownSize(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("l.zip");
        Files.writeString(file, "data");

        UpdateVerifier.verifySize(file, 0);
    }

    @Test
    @DisplayName("wrong size fails")
    void wrongSize(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("l.zip");
        Files.writeString(file, "data");

        assertThrows(IOException.class,
                () -> UpdateVerifier.verifySize(file, 999));
    }

    @Test
    @DisplayName("wrong hash fails and deletes the file")
    void wrongHash(@TempDir Path dir) throws Exception {
        byte[] data = "update-bytes".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("l.zip");
        Files.write(file, data);

        assertThrows(IOException.class, () -> UpdateVerifier.verify(
                file, version("0".repeat(64), data.length)));
        assertFalse(Files.exists(file),
                "corrupt package must be deleted");
    }
}
