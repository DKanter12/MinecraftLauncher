package org.example.launcher.infrastructure.download;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

import java.io.IOException;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Оркестрирует полную установку версии Minecraft:
 * загрузку клиентского JAR, всех библиотек, нативных библиотек и
 * всех связанных ресурсов с логикой пропуска валидных файлов по хэшу.
 * <p>
 * Общие библиотеки и ресурсы хранятся в общих каталогах,
 * управляемых {@link GameDirectory}, и переиспользуются версиями
 * без повторной загрузки.
 * <p>
 * Реализации должны сообщать прогресс через
 * {@link InstallationProgress} и возвращать
 * {@link InstallationResult} с итогом.
 */
public interface InstallationService {

    /**
     * Устанавливает заданную версию в указанный игровой каталог.
     *
     * @param version   устанавливаемая версия (для URL метаданных)
     * @param metadata  заранее полученные метаданные версии
     * @param gameDir   раскладка игрового каталога
     * @param progress  обратный вызов прогресса (используйте {@link InstallationProgress#NONE},
     *                  если не нужен)
     * @return сводный результат установки
     * @throws IOException при критической ошибке, мешающей установке
     */
    InstallationResult install(MinecraftVersion version,
                               VersionMetadata metadata,
                               GameDirectory gameDir,
                               InstallationProgress progress) throws IOException;
}
