package org.example.launcher.application.launch;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Обработка завершения Minecraft: нормальное завершение — молча,
 * ошибка — через {@link CrashAnalyzer} в {@link CrashReport}.
 */
public class GameExitHandler {

    public GameExitHandler() {
    }

    /**
     * @param process    завершённый процесс
     * @param exitCode   код завершения
     * @param gameDir    каталог сборки (для пути полного лога)
     * @return пусто при нормальном завершении, иначе отчёт о краше
     */
    public Optional<CrashReport> handle(GameProcess process, int exitCode,
                                        Path gameDir) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(gameDir, "gameDir");
        if (exitCode == 0) {
            return Optional.empty();
        }
        String fullLog = gameDir.resolve("logs").resolve("latest.log")
                .toString();
        return Optional.of(CrashAnalyzer.analyze(exitCode,
                process.stdout(), process.stderr(), fullLog));
    }
}
