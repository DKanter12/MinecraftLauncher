package org.example.launcher.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Установка файлового лога лаунчера: {@code <gameDir>/launcher/logs/launcher.log}.
 *
 * <p>Диагностика пользовательских проблем требует истории: консоль
 * {@code gradlew run} не всегда доступна, а исключения, ловимые в
 * background-тасках JavaFX, не попадают на экран. Все логгеры
 * {@code org.example.launcher.*} дублируют свой вывод в файл вместе
 * с непойманными исключениями UI-потока — их же логирует обработчик
 * на случай краша за пределами JavaFX-тасок.</p>
 *
 * <p>Вызывается один раз при старте приложения; любые ошибки
 * инициализации лог-файла игнорируются — GUI не должен зависеть
 * от доступности диска для диагностического вывода.</p>
 */
public final class LauncherLogger {

    /** Каталог, в который пишет лаунчер (внутри игрового каталога). */
    private static final Path LOGS_DIR = GameDirectory.defaultDirectory()
            .root().resolve("launcher").resolve("logs");

    private static final int ROTATION_LIMIT = 2 * 1024 * 1024;

    private LauncherLogger() {
    }

    /** Подключает файловый обработчик ко всем логгерам проекта. */
    public static void init() {
        try {
            Files.createDirectories(LOGS_DIR);
            CollectionHandler handler = new CollectionHandler(
                    LOGS_DIR.resolve("launcher.log"), ROTATION_LIMIT, 3, true);
            handler.setFormatter(new SimpleFormatter());
            Logger root = LogManager.getLogManager().getLogger("");
            root.addHandler(handler);
            setLevelsFor(root);
        } catch (IOException | RuntimeException e) {
            // Лог-файл недоступен — работаем без него, консоль остаётся.
        }
    }

    /**
     * Файловый обработчик, не роняющий выполнение при ошибке записи
     * (FileHandler бросается из {@code publish} при недоступном диске).
     */
    private static final class CollectionHandler extends FileHandler {
        private CollectionHandler(Path file, int limit, int count,
                                  boolean append) throws IOException {
            super(file.toString(), limit, count, append);
        }

        @Override
        public void publish(java.util.logging.LogRecord record) {
            try {
                super.publish(record);
            } catch (RuntimeException e) {
                // Плохой диск/антивирус — молча продолжаем без лога.
            }
        }
    }

    /**
     * Включает INFO-уровень для всех логгеров проекта (некоторые
     *-messages из background-тасок терялись по умолчанию).
     */
    private static void setLevelsFor(Logger root) {
        for (Logger logger : getLoggerChain(root)) {
            if (logger.getLevel() == null
                    || logger.getLevel().intValue() > Level.INFO.intValue()) {
                logger.setLevel(Level.INFO);
            }
        }
    }

    private static List<Logger> getLoggerChain(Logger root) {
        return List.of(root, Logger.getLogger("org.example.launcher"));
    }

    /** Путь текущего лога (для ссылок из UI/поддержки). */
    public static Path logFile() {
        return LOGS_DIR.resolve("launcher.log");
    }
}
