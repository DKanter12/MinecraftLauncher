package org.example.launcher.build;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.VersionMetadataService;
import org.example.launcher.service.modloader.ModLoaderRegistry;

/**
 * Отвечает только за файлы версии в общих каталогах
 * ({@code versions/, libraries/, assets/}).
 * Про сборки/профили ничего не знает.
 */
public class MinecraftVersionManager {

    private final VersionMetadataService metadataService;
    private final InstallationService installationService;
    private final ModLoaderRegistry modLoaderRegistry;
    private final GameDirectory storage;

    public MinecraftVersionManager(
            VersionMetadataService metadataService,
            InstallationService installationService,
            ModLoaderRegistry modLoaderRegistry,
            GameDirectory storage) {
        this.metadataService = Objects.requireNonNull(metadataService);
        this.installationService = Objects.requireNonNull(installationService);
        this.modLoaderRegistry = Objects.requireNonNull(modLoaderRegistry);
        this.storage = Objects.requireNonNull(storage);
    }

    /** Id версии, которая должна лежать в {@code versions/}. */
    public String resolveVersionId(BuildRequest request) {
        return request.versionId();
    }

    /**
     * Проверка, скачана ли нужная версия.
     * Ванилла: есть и {@code <id>.jar}, и {@code <id>.json}.
     * Модовая: есть merged {@code <loaderId>.json} + merged {@code <loaderId>.jar}
     * (установщик кладёт ванильный client jar под merged id, см.
     * {@code MinecraftInstaller.buildClientJarTasks}).
     */
    public boolean isVersionDownloaded(BuildRequest request) {
        String versionId = resolveVersionId(request);
        if (request.isVanilla()) {
            return Files.isRegularFile(storage.clientJar(versionId))
                    && Files.isRegularFile(storage.versionMetadata(versionId));
        }
        return Files.isRegularFile(storage.versionMetadata(versionId))
                && Files.isRegularFile(storage.clientJar(versionId));
    }

    /** Прямая проверка файлов уже известного id версии (после установки). */
    public boolean isVersionFilesPresent(String versionId) {
        return Files.isRegularFile(storage.versionMetadata(versionId))
                && Files.isRegularFile(storage.clientJar(versionId));
    }

    /**
     * Скачивает нужную версию (ванилла напрямую, модовая через
     * установщик загрузчика, который сам тянет ванильную базу).
     *
     * @return id установленной версии
     */
    public String downloadVersion(BuildRequest request, InstallationProgress progress)
            throws IOException {
        VersionMetadata vanillaMetadata = metadataService.fetchMetadata(request.mcVersion());
        InstallationProgress active = progress == null ? InstallationProgress.NONE : progress;
        if (request.isVanilla()) {
            installationService.install(request.mcVersion(), vanillaMetadata, storage, active);
            return request.mcVersion().id();
        }
        var entry = modLoaderRegistry.get(request.type()).orElseThrow(
                () -> new IOException(request.type().displayName() + " support is not registered"));
        var installResult = entry.installer().install(
                request.mcVersion(), vanillaMetadata, request.loader(), storage, active);
        if (installResult.fileResult() != null && installResult.fileResult().hasFailures()) {
            throw new IOException("Loader install failed: " + installResult.summary());
        }
        return installResult.versionId();
    }
}
