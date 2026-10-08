package org.example.launcher.infrastructure.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.example.launcher.infrastructure.mojang.MojangVersionMetadataService;

@DisplayName("FileIntegrityChecker")
class FileIntegrityCheckerTest {

    private final MojangVersionMetadataService metadataService = new MojangVersionMetadataService();

    private static final String VERSION_JSON = """
            {
              "id": "1.21",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "21",
              "assetIndex": {
                "id": "21", "sha1": "abc", "size": 100, "totalSize": 200,
                "url": "https://example.com/21.json"
              },
              "downloads": {
                "client": { "sha1": "c1", "size": 999, "url": "https://example.com/client.jar" }
              },
              "libraries": [
                {
                  "name": "com.mojang:logging:1.1.1",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/logging/1.1.1/logging-1.1.1.jar",
                      "sha1": "l1", "size": 100,
                      "url": "https://example.com/logging-1.1.1.jar"
                    }
                  }
                }
              ],
              "arguments": {
                "game": ["--username", "${auth_name}"],
                "jvm": ["-cp", "${classpath}"]
              }
            }
            """;

    private void createDummyFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content != null ? content : "dummy");
    }

    @Test
    @DisplayName("verifyFiles returns empty list when all files present (no hash check)")
    void verifyFilesAllPresent(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        // Create dummy files (no SHA-1 match needed since hashes are fake)
        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        FileIntegrityChecker checker = new FileIntegrityChecker(
                (path, sha1) -> true);

        List<String> problems = checker.verifyFiles(meta, gameDir);
        assertTrue(problems.isEmpty(), "No problems expected: " + problems);
    }

    @Test
    @DisplayName("verifyFiles reports missing client JAR")
    void verifyFilesMissingJar(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        FileIntegrityChecker checker = new FileIntegrityChecker(
                (path, sha1) -> true);

        List<String> problems = checker.verifyFiles(meta, gameDir);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("client JAR"));
    }

    @Test
    @DisplayName("verifyFiles reports hash mismatch")
    void verifyFilesHashMismatch(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        // Verifier always fails
        FileIntegrityChecker checker = new FileIntegrityChecker(
                (path, sha1) -> false);

        List<String> problems = checker.verifyFiles(meta, gameDir);
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("hash mismatch")));
    }

    @Test
    @DisplayName("verifyFiles reports missing library")
    void verifyFilesMissingLib(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.clientJar("1.21"), "jar");
        // Library not created
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        FileIntegrityChecker checker = new FileIntegrityChecker(
                (path, sha1) -> true);

        List<String> problems = checker.verifyFiles(meta, gameDir);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("library"));
    }

    @Test
    @DisplayName("exists and isValid follow presence and hash")
    void primitives(@TempDir Path dir) throws IOException {
        FileIntegrityChecker checker = new FileIntegrityChecker(
                (path, sha1) -> "good".equals(sha1));
        Path file = dir.resolve("a.jar");
        Files.writeString(file, "data");

        assertTrue(checker.exists(file));
        assertFalse(checker.exists(dir.resolve("missing.jar")));
        assertTrue(checker.isValid(file, "good"));
        assertFalse(checker.isValid(file, "bad"));
        assertTrue(checker.isValid(file, null));
        assertFalse(checker.isValid(dir.resolve("missing.jar"), "good"));
        assertTrue(checker.checksumMatches(file, "good"));
    }
}
