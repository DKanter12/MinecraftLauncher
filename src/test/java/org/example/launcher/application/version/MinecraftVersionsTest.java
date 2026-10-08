package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.loaders.ModLoaderInstaller;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.loaders.ModLoaderVersionProvider;
import org.example.launcher.domain.model.ModLoaderVersion;

@DisplayName("MinecraftVersions")
class MinecraftVersionsTest {

    private static MinecraftVersionRepository fakeVanilla() {
        return () -> List.of(
                MinecraftVersion.of("1.21.10", VersionType.RELEASE,
                        "2025-06-17T10:00:00+00:00", "https://x/1.21.10.json"),
                MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                        "2025-10-07T10:00:00+00:00", "https://x/1.21.11.json"));
    }

    private static ModLoaderVersionProvider fakeProvider(
            java.util.Set<String> supported) {
        return new ModLoaderVersionProvider() {
            @Override
            public List<ModLoaderVersion> fetchVersions(String mc) {
                return List.of();
            }

            @Override
            public java.util.Set<String> fetchSupportedMinecraftVersions() {
                return supported;
            }
        };
    }

    private static ModLoaderInstaller fakeInstaller() {
        return (vanillaMetadata, loader, gameDir, progress) ->
                new ModLoaderInstaller.ModLoaderInstallResult(
                        loader.installedVersionId(), null);
    }

    @Test
    @DisplayName("vanilla list comes sorted from Mojang")
    void vanilla() throws IOException {
        var versions = new MinecraftVersions(
                fakeVanilla(), new ModLoaderRegistry());

        List<MinecraftVersion> list = versions.getMinecraftVanillaVersions();

        assertEquals(List.of("1.21.11", "1.21.10"),
                list.stream().map(MinecraftVersion::id).toList());
        assertTrue(list.stream().allMatch(MinecraftVersion::isVanilla));
    }

    @Test
    @DisplayName("modded list carries loader cores, failing loaders skipped")
    void modded() {
        var registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC,
                fakeProvider(java.util.Set.of("1.21.11", "1.21.10")),
                fakeInstaller());
        registry.register(ModLoaderType.QUILT,
                new ModLoaderVersionProvider() {
                    @Override
                    public List<ModLoaderVersion> fetchVersions(String mc)
                            throws IOException {
                        throw new IOException("meta is down");
                    }

                    @Override
                    public java.util.Set<String> fetchSupportedMinecraftVersions()
                            throws IOException {
                        throw new IOException("meta is down");
                    }
                }, fakeInstaller());
        var versions = new MinecraftVersions(fakeVanilla(), registry);

        List<MinecraftVersion> list = versions.getMinecraftModdedVersions();

        assertEquals(2, list.size());
        assertTrue(list.stream().allMatch(
                v -> v.core() == ModLoaderType.FABRIC));
        assertEquals(List.of("1.21.11", "1.21.10"),
                list.stream().map(MinecraftVersion::id).toList());
        assertEquals(VersionType.RELEASE, list.get(0).type());
    }

    @Test
    @DisplayName("network failure propagates for vanilla list")
    void vanillaFailure() {
        MinecraftVersionRepository failing = () -> {
            throw new IOException("offline");
        };
        var versions = new MinecraftVersions(
                failing, new ModLoaderRegistry());

        assertThrows(IOException.class, versions::getMinecraftVanillaVersions);
    }
}
