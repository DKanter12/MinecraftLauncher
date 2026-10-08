package org.example.launcher.application.version;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.example.launcher.application.build.Build;
import org.example.launcher.application.build.BuildRequest;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;

/**
 * Установка версии в общее хранилище.
 * Уже установленная корректная версия используется повторно
 * и не скачивается. Версию ядра лаунчер не выбирает сам —
 * ставится именно указанная (для модовой она обязательна).
 */
public class MinecraftVersionDownloader {

    private final MinecraftVersionManager versions;
    private final MinecraftVersionRepository vanillaRepo;
    private final ModLoaderRegistry loaders;

    public MinecraftVersionDownloader(MinecraftVersionManager versions,
                                      MinecraftVersionRepository vanillaRepo,
                                      ModLoaderRegistry loaders) {
        this.versions = Objects.requireNonNull(versions, "versions");
        this.vanillaRepo = Objects.requireNonNull(vanillaRepo, "vanillaRepo");
        this.loaders = Objects.requireNonNull(loaders, "loaders");
    }

    /**
     * Устанавливает версию (ваниллу или связку с ядром).
     *
     * @return id установленной версии в {@code versions/}
     * @throws IllegalArgumentException если модовая версия без версии ядра
     * @throws IOException если скачать не удалось
     */
    public String download(MinecraftVersion version) throws IOException {
        Objects.requireNonNull(version, "version");
        if (version.isVanilla()) {
            MinecraftVersion entry = version.metadataUrl() != null
                    && !version.metadataUrl().isBlank()
                    ? version : vanillaEntry(version.id());
            BuildRequest request = new BuildRequest(
                    new Build("", version.id(), version.core(),
                            version.loaderVersion()),
                    entry, null, List.of());
            return versions.downloadVersion(request, InstallationProgress.NONE);
        }
        if (version.loaderVersion().isBlank()) {
            throw new IllegalArgumentException(
                    "Modded download requires a loader version");
        }
        MinecraftVersion base = vanillaEntry(version.id());
        ModLoaderVersion loader = resolveLoader(version);
        BuildRequest request = new BuildRequest(
                new Build("", version.id(), version.core(),
                        version.loaderVersion()),
                base, loader, List.of());
        return versions.downloadVersion(request, InstallationProgress.NONE);
    }

    /**
     * Ванильная запись с URL для скачивания: сначала URL самой версии,
     * иначе поиск по id в репозитории.
     */
    MinecraftVersion vanillaEntry(String minecraftVersion) throws IOException {
        List<MinecraftVersion> all = vanillaRepo.fetchVersions();
        return vanillaRepo.findById(minecraftVersion, all)
                .filter(v -> v.metadataUrl() != null
                        && !v.metadataUrl().isBlank())
                .orElseThrow(() -> new IOException(
                        "Minecraft version not found: " + minecraftVersion));
    }

    /**
     * Версия загрузчика с URL установщика (нужен Forge/NeoForge).
     * Для meta-загрузчиков URL не используется.
     */
    ModLoaderVersion resolveLoader(MinecraftVersion version)
            throws IOException {
        var entry = loaders.get(version.core())
                .orElseThrow(() -> new IOException(
                        "Loader is not registered: " + version.core()));
        List<ModLoaderVersion> available =
                entry.provider().fetchVersions(version.id());
        return available.stream()
                .filter(v -> v.loaderVersion().equals(version.loaderVersion()))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Loader " + version.loaderVersion()
                                + " is not available for Minecraft "
                                + version.id()));
    }
}
