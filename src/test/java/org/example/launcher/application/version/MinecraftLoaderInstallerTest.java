package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModLoaderInstaller;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.loaders.ModLoaderVersionProvider;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;

@DisplayName("MinecraftLoaderInstaller")
class MinecraftLoaderInstallerTest {

    private static VersionMetadata minimalMetadata(String id) {
        return new VersionMetadata(id, "release",
                "net.minecraft.client.main.Main", null, null, null, null,
                List.of(), List.of(), List.of(), null);
    }

    private static MinecraftVersionRepository vanillaRepo() {
        return () -> List.of(MinecraftVersion.of("1.21.11",
                VersionType.RELEASE, null, "https://x/1.21.11.json"));
    }

    private static final class RecordingLoaderInstaller
            implements ModLoaderInstaller {
        String installedLoaderVersion;
        String installedInstallerUrl;

        @Override
        public ModLoaderInstallResult install(
                VersionMetadata vanillaMetadata, ModLoaderVersion loader,
                GameDirectory gameDir, InstallationProgress progress) {
            installedLoaderVersion = loader.loaderVersion();
            installedInstallerUrl = loader.installerUrlOpt().orElse(null);
            return new ModLoaderInstallResult(loader.installedVersionId(),
                    new InstallationResult(1, 1, 0, 0, 10, List.of()));
        }
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

    @Test
    @DisplayName("installFabric uses the chosen loader version")
    void installsFabric(@TempDir Path dir) throws IOException {
        var gameDir = new GameDirectory(dir);
        var loaderInstaller = new RecordingLoaderInstaller();
        var registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC, fabricProvider(),
                loaderInstaller);
        var installer = new MinecraftLoaderInstaller(registry,
                version -> minimalMetadata(version.id()), vanillaRepo(),
                gameDir);
        var vanilla = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        assertEquals("fabric-loader-0.19.5-1.21.11",
                installer.installFabric(vanilla, "0.19.5"));
        assertEquals("0.19.5", loaderInstaller.installedLoaderVersion);
    }

    @Test
    @DisplayName("unknown loader version fails without installing")
    void unknownLoader(@TempDir Path dir) {
        var gameDir = new GameDirectory(dir);
        var loaderInstaller = new RecordingLoaderInstaller();
        var registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC, fabricProvider(),
                loaderInstaller);
        var installer = new MinecraftLoaderInstaller(registry,
                version -> minimalMetadata(version.id()), vanillaRepo(),
                gameDir);
        var vanilla = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        assertThrows(IOException.class,
                () -> installer.installFabric(vanilla, "9.9.9"));
        assertTrue(loaderInstaller.installedLoaderVersion == null);
    }

    @Test
    @DisplayName("blank loader version is rejected immediately")
    void blankLoader(@TempDir Path dir) {
        var installer = new MinecraftLoaderInstaller(new ModLoaderRegistry(),
                version -> minimalMetadata(version.id()), vanillaRepo(),
                new GameDirectory(dir));
        var vanilla = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        assertThrows(IllegalArgumentException.class,
                () -> installer.installFabric(vanilla, "  "));
    }
}
