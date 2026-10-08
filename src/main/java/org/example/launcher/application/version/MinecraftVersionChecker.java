package org.example.launcher.application.version;

import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import org.example.launcher.application.build.Build;
import org.example.launcher.application.build.BuildRequest;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
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
        BuildRequest request = requestOf(version);
        if (request == null) {
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
        BuildRequest request = requestOf(version);
        if (request == null) {
            return false;
        }
        return versions.isVersionDownloaded(request);
    }

    /**
     * Запрос для проверки файлов. Модовой версии нужна указанная
     * версия ядра (иначе id merged-версии неизвестен) — без неё
     * возвращается {@code null}.
     */
    static BuildRequest requestOf(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        if (!version.isVanilla() && version.loaderVersion().isBlank()) {
            return null;
        }
        ModLoaderVersion loader = version.isVanilla() ? null
                : new ModLoaderVersion(version.core(), version.loaderVersion(),
                        version.id(), false, null);
        Build build = new Build("", version.id(), version.core(),
                version.loaderVersion());
        return new BuildRequest(build, version, loader, List.of());
    }
}
