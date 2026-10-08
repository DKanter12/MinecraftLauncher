package org.example.launcher.application.version;

import java.nio.file.Files;
import java.util.Objects;

import org.example.launcher.application.build.BuildRequest;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Проверка локального хранилища перед скачиванием.
 * Сети не касается: только файлы на диске.
 */
public class MinecraftVersionChecker {

    private final MinecraftVersionManager versions;
    private final GameDirectory storage;

    public MinecraftVersionChecker(MinecraftVersionManager versions,
                                   GameDirectory storage) {
        this.versions = Objects.requireNonNull(versions, "versions");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /**
     * Установлена ли версия (есть маркер — JSON метаданных).
     * Неполные спецификации (модовая без версии ядра) считаются
     * неустановленными, а не ошибкой.
     */
    public boolean isInstalled(MinecraftVersion version) {
        BuildRequest request;
        try {
            request = ManifestEntries.toRequest(version);
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
        return Files.isRegularFile(
                storage.versionMetadata(request.versionId()));
    }

    /**
     * Корректна ли версия (все необходимые файлы на месте:
     * client.jar и метаданные). Контрольные суммы сверяются при запуске.
     */
    public boolean isValid(MinecraftVersion version) {
        try {
            return versions.isVersionDownloaded(ManifestEntries.toRequest(version));
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
