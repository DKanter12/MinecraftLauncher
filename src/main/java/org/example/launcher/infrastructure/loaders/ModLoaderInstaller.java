package org.example.launcher.infrastructure.loaders;

import java.io.IOException;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Устанавливает мод-загрузчик под конкретную ванильную версию Minecraft,
 * полностью автоматически (ручной запуск установщика не требуется).
 * <p>
 * Реализации обязаны:
 * <ol>
 *   <li>скачать необходимые файлы загрузчика (и зависимости);</li>
 *   <li>создать конфигурацию запуска (локальный JSON версии в раскладке
 *       {@code versions/{id}/{id}.json});</li>
 *   <li>обеспечить установку ванильной базы;</li>
 *   <li>проверить установку (проверки хэшей).</li>
 * </ol>
 */
public interface ModLoaderInstaller {

    /**
     * Устанавливает заданную версию мод-загрузчика.
     *
     * @param vanillaVersion   ванильная запись манифеста целевой версии
     *                         Minecraft
     * @param vanillaMetadata  предзагруженные ванильные метаданные этой версии
     * @param loader           устанавливаемая версия загрузчика
     * @param gameDir          раскладка игрового каталога
     * @param progress         колбэк прогресса
     * @return результат установки, включая созданный id версии
     * @throws IOException при критических сбоях (сеть, ввод-вывод, ошибки
     *                     процесса установщика)
     */
    ModLoaderInstallResult install(MinecraftVersion vanillaVersion,
                                   VersionMetadata vanillaMetadata,
                                   ModLoaderVersion loader,
                                   GameDirectory gameDir,
                                   InstallationProgress progress) throws IOException;

    /**
     * Результат установки мод-загрузчика.
     *
     * @param versionId   id установленной модовой версии
     *                    (например, {@code fabric-loader-0.16.9-1.21.4})
     * @param fileResult  сводный результат установки файлов
     *                    (скачивания + проверка хэшей) либо {@code null},
     *                    когда загрузчик ведёт собственное управление файлами
     */
    record ModLoaderInstallResult(String versionId, InstallationResult fileResult) {

        public boolean isSuccessful() {
            return fileResult == null || fileResult.isSuccess();
        }

        public String summary() {
            return versionId + ": " + (fileResult != null ? fileResult.summary() : "OK");
        }
    }
}
