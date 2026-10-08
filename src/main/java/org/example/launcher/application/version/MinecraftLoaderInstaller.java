package org.example.launcher.application.version;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;

/**
 * Установка конкретного ядра, выбранного пользователем.
 * Версия ядра не подбирается сама — ставится именно указанная.
 * Уже установленная комбинация используется повторно.
 */
public class MinecraftLoaderInstaller {

    private final ModLoaderRegistry loaders;
    private final VersionMetadataService metadataService;
    private final MinecraftVersionRepository vanillaRepo;
    private final GameDirectory storage;

    public MinecraftLoaderInstaller(ModLoaderRegistry loaders,
                                    VersionMetadataService metadataService,
                                    MinecraftVersionRepository vanillaRepo,
                                    GameDirectory storage) {
        this.loaders = Objects.requireNonNull(loaders, "loaders");
        this.metadataService =
                Objects.requireNonNull(metadataService, "metadataService");
        this.vanillaRepo = Objects.requireNonNull(vanillaRepo, "vanillaRepo");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /**
     * Ставит ядро на ванильную версию.
     *
     * @param vanillaVersion ванильная версия с URL (из списка ванильных)
     * @param loaderVersion  версия ядра, выбранная пользователем
     * @return id установленной версии в {@code versions/}
     */
    public String installFabric(MinecraftVersion vanillaVersion,
                                String loaderVersion) throws IOException {
        return install(ModLoaderType.FABRIC, vanillaVersion, loaderVersion);
    }

    /** Ставит Quilt на ванильную версию. */
    public String installQuilt(MinecraftVersion vanillaVersion,
                               String loaderVersion) throws IOException {
        return install(ModLoaderType.QUILT, vanillaVersion, loaderVersion);
    }

    /** Ставит NeoForge на ванильную версию. */
    public String installNeoForge(MinecraftVersion vanillaVersion,
                                  String loaderVersion) throws IOException {
        return install(ModLoaderType.NEOFORGE, vanillaVersion, loaderVersion);
    }

    /** Ставит Forge на ванильную версию. */
    public String installForge(MinecraftVersion vanillaVersion,
                               String loaderVersion) throws IOException {
        return install(ModLoaderType.FORGE, vanillaVersion, loaderVersion);
    }

    private String install(ModLoaderType core, MinecraftVersion vanillaVersion,
                           String loaderVersion) throws IOException {
        Objects.requireNonNull(vanillaVersion, "vanillaVersion");
        if (loaderVersion == null || loaderVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Loader version must be chosen by the user");
        }
        MinecraftVersion base = vanillaVersion.metadataUrl() != null
                && !vanillaVersion.metadataUrl().isBlank()
                ? vanillaVersion
                : vanillaEntry(vanillaVersion.id());
        ModLoaderVersion loader = resolveLoader(core,
                vanillaVersion.id(), loaderVersion);
        var vanillaMetadata = metadataService.fetchMetadata(base);
        var entry = loaders.get(core)
                .orElseThrow(() -> new IOException(
                        "Loader is not registered: " + core));
        var result = entry.installer().install(vanillaMetadata,
                loader, storage, InstallationProgress.NONE);
        if (result.fileResult() != null && result.fileResult().hasFailures()) {
            throw new IOException(
                    "Loader install failed: " + result.summary());
        }
        return result.versionId();
    }

    private MinecraftVersion vanillaEntry(String minecraftVersion)
            throws IOException {
        List<MinecraftVersion> all = vanillaRepo.fetchVersions();
        return vanillaRepo.findById(minecraftVersion, all)
                .filter(v -> v.metadataUrl() != null
                        && !v.metadataUrl().isBlank())
                .orElseThrow(() -> new IOException(
                        "Minecraft version not found: " + minecraftVersion));
    }

    private ModLoaderVersion resolveLoader(ModLoaderType core,
                                           String minecraftVersion,
                                           String loaderVersion)
            throws IOException {
        var entry = loaders.get(core)
                .orElseThrow(() -> new IOException(
                        "Loader is not registered: " + core));
        List<ModLoaderVersion> available =
                entry.provider().fetchVersions(minecraftVersion);
        return available.stream()
                .filter(v -> v.loaderVersion().equals(loaderVersion))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Loader " + loaderVersion
                                + " is not available for Minecraft "
                                + minecraftVersion));
    }
}
