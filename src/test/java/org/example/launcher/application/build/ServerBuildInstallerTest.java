package org.example.launcher.application.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.example.launcher.infrastructure.server.BuildFileCategory;
import org.example.launcher.infrastructure.server.BuildFileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Проверки хешей и дайджестов {@link ServerBuildInstaller}: файлы серверных
 * сборок обязаны проверяться по первому доступному хешу и отвергаться при
 * повреждении содержимого.
 */
class ServerBuildInstallerTest {

    @TempDir
    Path temp;

    private static String sha1Hex(byte[] content) throws IOException {
        return ServerBuildInstaller.digestOf(write(content), "SHA-1");
    }

    private static Path write(byte[] content) {
        try {
            Path file = Files.createTempFile("digest", ".bin");
            Files.write(file, content);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(MessageDigest digest, byte[] content) {
        StringBuilder sb = new StringBuilder(digest.digest(content).length * 2);
        for (byte b : digest.digest(content)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static MessageDigest md(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void digestOfComputesSha1() throws IOException {
        byte[] content = "hello build".getBytes(StandardCharsets.UTF_8);
        String sha1 = sha1Hex(content);
        String expected = hex(md("SHA-1"), content);
        assertEquals(expected, sha1);
    }

    @Test
    void checksumMatchesSha1() throws IOException {
        byte[] content = "mods zip ".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(temp.resolve("mod.jar"), content);
        String sha1 = hex(md("SHA-1"), content);
        assertTrue(ServerBuildInstaller.checksumMatches(file,
                new BuildFileEntry("mod.jar", BuildFileCategory.MODS,
                        sha1, content.length)));
        assertFalse(ServerBuildInstaller.checksumMatches(file,
                new BuildFileEntry("mod.jar", BuildFileCategory.MODS,
                        "deadbeef", content.length)));
    }

    @Test
    void checksumFallsBackToSha256ThenMd5() throws IOException {
        byte[] content = "config yml".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(temp.resolve("conf.cfg"), content);
        String sha256 = hex(md("SHA-256"), content);
        String md5 = hex(md("MD5"), content);
        assertTrue(ServerBuildInstaller.checksumMatches(file,
                new BuildFileEntry("conf.cfg", BuildFileCategory.CONFIGS,
                        null, sha256, null, content.length)));
        assertTrue(ServerBuildInstaller.checksumMatches(file,
                new BuildFileEntry("conf.cfg", BuildFileCategory.CONFIGS,
                        null, null, md5, content.length)));
        assertFalse(ServerBuildInstaller.checksumMatches(file,
                new BuildFileEntry("conf.cfg", BuildFileCategory.CONFIGS,
                        null, null, "badbad", content.length)));
    }

    @Test
    void sha1WinsOverOtherHashesWhenPresent() throws IOException {
        byte[] content = "both hashes".getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(temp.resolve("both.bin"), content);
        String sha1 = hex(md("SHA-1"), content);
        String md5 = hex(md("MD5"), content);
        // Correct sha1 + broken md5 → verification via sha1 passes
        assertTrue(ServerBuildInstaller.checksumMatches(
                Files.write(temp.resolve("both2.bin"), content),
                new BuildFileEntry("both2.bin", BuildFileCategory.RESOURCES,
                        sha1, null, md5, content.length)));
        // Broken sha1 + correct md5 → sha1 has priority, file fails
        assertFalse(ServerBuildInstaller.checksumMatches(
                Files.write(temp.resolve("both3.bin"), content),
                new BuildFileEntry("both3.bin", BuildFileCategory.RESOURCES,
                        "nope", null, md5, content.length)));
    }
}
