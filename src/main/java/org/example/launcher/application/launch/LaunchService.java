package org.example.launcher.application.launch;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.LaunchResult;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Оркестрирует полную последовательность запуска Minecraft:
 * <ol>
 *   <li>Проверка наличия и целостности всех требуемых файлов</li>
 *   <li>Подбор подходящего рантайма Java</li>
 *   <li>Построение аргументов запуска из метаданных версии</li>
 *   <li>Старт Java-процесса</li>
 *   <li>Оборачивание процесса для наблюдения</li>
 * </ol>
 * <p>
 * Вызывающий получает {@link LaunchResult} с либо работающим
 * {@link org.example.launcher.domain.model.MinecraftProcess}, либо ошибкой
 * с объяснением причины.
 */
public interface LaunchService {

    /**
     * Запускает заданную версию Minecraft, работая внутри корня хранилища.
     *
     * @param metadata метаданные версии
     * @param gameDir  раскладка игрового каталога
     * @param profile  профиль игрока
     * @return результат запуска (успех либо отказ с деталями)
     */
    LaunchResult launch(VersionMetadata metadata,
                        GameDirectory gameDir,
                        GameProfile profile);

    /**
     * Запускает заданную версию Minecraft внутри отдельного рабочего
     * каталога (модовые профили): общие файлы разрешаются из
     * {@code gameDir}, а процесс работает в {@code runtimeDirectory},
     * где находятся {@code mods/}, {@code config/}, {@code saves/} и т.д. профиля.
     *
     * @param metadata         метаданные версии
     * @param gameDir          раскладка игрового каталога хранилища
     * @param profile          профиль игрока
     * @param runtimeDirectory каталог, в котором работает игра
     * @param extraJvmArgs     JVM-аргументы профиля (могут быть пустыми)
     * @return результат запуска (успех либо отказ с деталями)
     */
    default LaunchResult launch(VersionMetadata metadata,
                                GameDirectory gameDir,
                                GameProfile profile,
                                Path runtimeDirectory,
                                List<String> extraJvmArgs) {
        return launch(metadata, gameDir, profile);
    }
}
