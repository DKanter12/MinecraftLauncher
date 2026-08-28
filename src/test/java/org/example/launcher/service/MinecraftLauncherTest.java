package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.install.ChecksumVerifier;
import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.Sha1ChecksumVerifier;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchResult;
import org.example.launcher.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MinecraftLauncher")
class MinecraftLauncherTest {

    private final MojangVersionMetadataService metadataService = new MojangVersionMetadataService();
    private final ChecksumVerifier verifier = new Sha1ChecksumVerifier();

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

    private JavaRuntime testRuntime(Path javaExe) {
        return new JavaRuntime(javaExe, 21, JavaRuntime.Source.JAVA_HOME);
    }

    private JavaResolutionService foundService(JavaRuntime rt) {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata meta) {
                return JavaResolutionResult.found(rt);
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return JavaResolutionResult.found(rt);
            }
        };
    }

    private JavaResolutionService notFoundService() {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata meta) {
                return JavaResolutionResult.notFound("No Java");
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return JavaResolutionResult.notFound("No Java");
            }
        };
    }

    private void createDummyFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content != null ? content : "dummy");
    }

    // ==================================================================
    //  File verification tests
    // ==================================================================

    @Test
    @DisplayName("verifyFiles returns empty list when all files present (no hash check)")
    void verifyFilesAllPresent(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        // Create dummy files (no SHA-1 match needed since hashes are fake)
        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(testRuntime(Path.of("java"))),
                (path, sha1) -> true);

        List<String> problems = launcher.verifyFiles(meta, gameDir);
        assertTrue(problems.isEmpty(), "No problems expected: " + problems);
    }

    @Test
    @DisplayName("verifyFiles reports missing client JAR")
    void verifyFilesMissingJar(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(testRuntime(Path.of("java"))),
                (path, sha1) -> true);

        List<String> problems = launcher.verifyFiles(meta, gameDir);
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
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(testRuntime(Path.of("java"))),
                (path, sha1) -> false);

        List<String> problems = launcher.verifyFiles(meta, gameDir);
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

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(testRuntime(Path.of("java"))),
                (path, sha1) -> true);

        List<String> problems = launcher.verifyFiles(meta, gameDir);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("library"));
    }

    // ==================================================================
    //  Launch flow tests
    // ==================================================================

    @Test
    @DisplayName("launch returns FILE_CHECK_FAILED when files missing")
    void launchFailsOnMissingFiles(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        // No files created
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(testRuntime(Path.of("java"))),
                (path, sha1) -> true);

        LaunchResult result = launcher.launch(meta, gameDir, GameProfile.offline("Steve"));

        assertEquals(LaunchResult.Status.FILE_CHECK_FAILED, result.status());
        assertTrue(result.message().contains("Missing"));
        assertTrue(result.process().isEmpty());
    }

    @Test
    @DisplayName("launch returns JAVA_NOT_FOUND when no Java available")
    void launchFailsNoJava(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                notFoundService(),
                (path, sha1) -> true);

        LaunchResult result = launcher.launch(meta, gameDir, GameProfile.offline("Steve"));

        assertEquals(LaunchResult.Status.JAVA_NOT_FOUND, result.status());
        assertTrue(result.process().isEmpty());
    }

    @Test
    @DisplayName("launch returns LAUNCH_FAILED for non-existent java executable")
    void launchFailsBadJava(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        // Use a non-existent java path
        JavaRuntime badRuntime = new JavaRuntime(
                dir.resolve("nonexistent-java"), 21, JavaRuntime.Source.CUSTOM);

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(badRuntime),
                (path, sha1) -> true);

        LaunchResult result = launcher.launch(meta, gameDir, GameProfile.offline("Steve"));

        assertEquals(LaunchResult.Status.LAUNCH_FAILED, result.status());
        assertTrue(result.message().contains("Failed to start"));
    }

    @Test
    @DisplayName("launch returns SUCCESS or LAUNCH_FAILED with real java")
    void launchSucceedsWithJava(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(VERSION_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);

        createDummyFile(gameDir.clientJar("1.21"), "jar");
        createDummyFile(gameDir.library("com/mojang/logging/1.1.1/logging-1.1.1.jar"), "lib");
        createDummyFile(gameDir.assetIndexFile("21"), "{}");

        String javaHome = System.getProperty("java.home");
        Path javaExe = Path.of(javaHome, "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");

        if (!Files.isRegularFile(javaExe)) {
            return;
        }

        JavaRuntime realRuntime = new JavaRuntime(javaExe, 21, JavaRuntime.Source.JAVA_HOME);

        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(),
                foundService(realRuntime),
                (path, sha1) -> true);

        LaunchResult result = launcher.launch(meta, gameDir, GameProfile.offline("Steve"));

        assertTrue(result.status() == LaunchResult.Status.SUCCESS
                || result.status() == LaunchResult.Status.LAUNCH_FAILED,
                "Expected SUCCESS or LAUNCH_FAILED, got " + result.status()
                        + ": " + result.message());

        if (result.isSuccess()) {
            result.process().ifPresent(p -> {
                if (p.isAlive()) {
                    p.kill();
                }
            });
        }
    }
}
