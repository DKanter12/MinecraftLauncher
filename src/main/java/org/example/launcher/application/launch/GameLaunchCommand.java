package org.example.launcher.application.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Уже подготовленная команда запуска. Только хранит данные,
 * запуск не выполняет.
 *
 * @param fullCommand     полный список токенов для {@code ProcessBuilder}:
 *                        java, JVM-аргументы, главный класс, игровые аргументы
 * @param workingDirectory рабочая директория процесса (папка сборки)
 */
public record GameLaunchCommand(
        List<String> fullCommand,
        Path workingDirectory) {

    public GameLaunchCommand {
        Objects.requireNonNull(fullCommand, "fullCommand");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        fullCommand = List.copyOf(fullCommand);
    }
}
