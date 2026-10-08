package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModLoaderInstaller;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.loaders.ModLoaderVersionProvider;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;

@DisplayName("MinecraftVersionDownloader")
class MinecraftVersionDownloaderTest {

    private static VersionMetadata minimalMetadata(String id) {
        return new VersionMetadata(id, "release",
                "net.minecraft.client.main.Main", null, null, null, null,
                List.of(), List.of(), List.of(), null);
    }

    private static final class RecordingInstaller implements InstallationService {
        int calls;

        @Override
        public InstallationResult install(
                org.example.launcher.model.MinecraftVersion version,
                VersionMetadata metadata, GameDirectory gameDir,
                InstallationProgress progress) {
            calls++;
            return new InstallationResult(1, 1, 0, 0, 10, List.of());
        }
    }

    private static MinecraftVersionRepository vanillaRepo() {
        return () -> List.of(MinecraftVersion.of("1.21.11",
                VersionType.RELEASE, null, "https://x/1.21.11.json"));
    }

    private static VersionMetadataService metadataService() {
        return version -> minimalMetadata(version.id());
    }

    private static ModLoaderVersionProvider fabricProvider() {
        return new ModLoaderVersionProvider() {
            @Override
            public List<ModLoaderVersion> fetchVersions(String mc) {
                return List.of(new ModLoaderVersion(ModLoaderType.FABRIC,
                        "0.19.5", mc, true, null));
            }
        };
    }

    private static ModLoaderInstaller recordingInstaller() {
        return (vanillaVersion, vanillaMetadata, loader, gameDir, progress) ->
                new ModLoaderInstaller.ModLoaderInstallResult(
                        loader.installedVersionId(),
                        new InstallationResult(1, 1, 0, 0, 10, List.of()));
    }

    @Test
    @DisplayName("vanilla download installs and returns version id")
    void vanilla(@TempDir Path dir) throws IOException {
        var gameDir = new GameDirectory(dir);
        var installer = new RecordingInstaller();
        var manager = new MinecraftVersionManager(metadataService(),
                installer, new ModLoaderRegistry(), gameDir);
        var downloader = new MinecraftVersionDownloader(manager,
                vanillaRepo(), new ModLoaderRegistry());
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        assertEquals("1.21.11", downloader.download(version));
        assertEquals(1, installer.calls);
    }

    @Test
    @DisplayName("modded download resolves loader and returns merged id")
    void modded(@TempDir Path dir) throws IOException {
        var gameDir = new GameDirectory(dir);
        var registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC, fabricProvider(),
                recordingInstaller());
        var manager = new MinecraftVersionManager(metadataService(),
                new RecordingInstaller(), registry, gameDir);
        var downloader = new MinecraftVersionDownloader(manager,
                vanillaRepo(), registry);
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null, ModLoaderType.FABRIC, "0.19.5");

        assertEquals("fabric-loader-0.19.5-1.21.11",
                downloader.download(version));
    }

    @Test
    @DisplayName("modded download without loader version is rejected")
    void moddedWithoutLoader(@TempDir Path dir) {
        var gameDir = new GameDirectory(dir);
        var manager = new MinecraftVersionManager(metadataService(),
                new RecordingInstaller(), new ModLoaderRegistry(), gameDir);
        var downloader = new MinecraftVersionDownloader(manager,
                vanillaRepo(), new ModLoaderRegistry());
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null, ModLoaderType.FABRIC, "");

        assertThrows(IllegalArgumentException.class,
                () -> downloader.download(version));
    }

    @Test
    @DisplayName("unknown loader version fails with IOException")
    void unknownLoader(@TempDir Path dir) {
        var gameDir = new GameDirectory(dir);
        var registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC, fabricProvider(),
                recordingInstaller());
        var manager = new MinecraftVersionManager(metadataService(),
                new RecordingInstaller(), registry, gameDir);
        var downloader = new MinecraftVersionDownloader(manager,
                vanillaRepo(), registry);
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null, ModLoaderType.FABRIC, "9.9.9");

        assertThrows(IOException.class, () -> downloader.download(version));
    }
}
