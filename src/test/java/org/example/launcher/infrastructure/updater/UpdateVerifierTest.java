package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
