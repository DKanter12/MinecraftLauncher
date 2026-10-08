package org.example.launcher.service;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.LaunchArguments;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Строит полную командную строку запуска версии Minecraft.
 * <p>
 * Принимает метаданные версии, раскладку локального игрового каталога,
 * профиль игрока и подобранный рантайм Java и выдаёт готовый к
 * выполнению {@link LaunchArguments}.
 * <p>
 * Билдер поддерживает как современный структурированный формат аргументов
 * (1.13+), так и старую строку {@code minecraftArguments} (до 1.13),
 * заменяя все плейсхолдеры Mojang конкретными значениями.
 */
public interface LaunchArgumentBuilder {

    /**
     * Строит аргументы запуска для заданного контекста, запуская игру
     * прямо внутри корня хранилища.
     *
     * @param metadata  метаданные версии
     * @param gameDir   раскладка игрового каталога
     * @param profile   профиль игрока
     * @param javaRuntime подобранный рантайм Java
     * @return полностью разрешённые аргументы запуска
     */
    LaunchArguments build(VersionMetadata metadata,
                          GameDirectory gameDir,
                          GameProfile profile,
                          JavaRuntime javaRuntime);

    /**
     * Строит аргументы запуска с отдельным рабочим каталогом и
     * дополнительными JVM-аргументами.
     * <p>
     * Используется для модовых профилей: общие файлы (клиентский JAR, библиотеки,
     * ассеты, нативы) разрешаются из {@code gameDir} (корня хранилища),
     * а игровой процесс работает внутри {@code runtimeDirectory} — собственного
     * каталога профиля с его {@code mods/}, {@code config/}, {@code saves/} и т.д.
     * Плейсхолдер {@code ${game_directory}} и рабочий каталог процесса указывают
     * на рабочий каталог; специфичные для профиля JVM-аргументы
     * (например, {@code -Xmx4G}) добавляются в конец.
     *
     * @param metadata        метаданные версии
     * @param gameDir         раскладка игрового каталога хранилища
     * @param profile         профиль игрока
     * @param javaRuntime     подобранный рантайм Java
     * @param runtimeDirectory каталог, в котором работает игра (моды,
     *                        сохранения, конфиги находятся здесь)
     * @param extraJvmArgs    JVM-аргументы профиля (могут быть пустыми)
     * @return полностью разрешённые аргументы запуска
     */
    default LaunchArguments build(VersionMetadata metadata,
                                  GameDirectory gameDir,
                                  GameProfile profile,
                                  JavaRuntime javaRuntime,
                                  Path runtimeDirectory,
                                  List<String> extraJvmArgs) {
        // По умолчанию расширенный контекст игнорируется (обратная совместимость)
        return build(metadata, gameDir, profile, javaRuntime);
    }
}
