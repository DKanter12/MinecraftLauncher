package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;

@DisplayName("MinecraftVersionChecker")
class MinecraftVersionCheckerTest {

    private static MinecraftVersionManager noNetworkManager(GameDirectory gameDir) {
        InstallationService neverInstalls = (version, metadata, dir, progress) ->
                new InstallationResult(0, 0, 0, 1, 0, List.of());
        var metadataService =
                (org.example.launcher.infrastructure.mojang.VersionMetadataService)
                        (version) -> {
                            throw new AssertionError("no network in checker test");
                        };
        return new MinecraftVersionManager(metadataService, neverInstalls,
                new ModLoaderRegistry(), gameDir);
    }

    @Test
    @DisplayName("missing version is neither installed nor valid")
    void missing(@TempDir Path dir) {
        var checker = new MinecraftVersionChecker(
                noNetworkManager(new GameDirectory(dir)),
                new GameDirectory(dir));
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        assertFalse(checker.isInstalled(version));
        assertFalse(checker.isValid(version));
    }

    @Test
    @DisplayName("json marker means installed, jar plus json means valid")
    void markers(@TempDir Path dir) throws Exception {
        var gameDir = new GameDirectory(dir);
        var checker = new MinecraftVersionChecker(
                noNetworkManager(gameDir), gameDir);
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        Path json = gameDir.versionMetadata("1.21.11");
        Files.createDirectories(json.getParent());
        Files.writeString(json, "{}");
        assertTrue(checker.isInstalled(version));
        assertFalse(checker.isValid(version));

        Path jar = gameDir.clientJar("1.21.11");
        Files.createDirectories(jar.getParent());
        Files.write(jar, new byte[]{1, 2, 3});
        assertTrue(checker.isValid(version));
    }

    @Test
    @DisplayName("underspecified modded version is not installed")
    void underspecified(@TempDir Path dir) {
        var gameDir = new GameDirectory(dir);
        var checker = new MinecraftVersionChecker(
                noNetworkManager(gameDir), gameDir);
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null, ModLoaderType.FABRIC, "");

        assertFalse(checker.isInstalled(version));
        assertFalse(checker.isValid(version));
    }

    @Test
    @DisplayName("unused progress callback compiles the contract")
    void progressContract() {
        InstallationProgress progress = InstallationProgress.NONE;
        progress.onStart(0, 0);
        progress.onComplete(new InstallationResult(0, 0, 0, 0, 0, List.of()));
    }
}
