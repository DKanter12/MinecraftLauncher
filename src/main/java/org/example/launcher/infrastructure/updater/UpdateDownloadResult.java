package org.example.launcher.infrastructure.updater;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Итог загрузки пакета обновления.
 *
 * @param success        файл скачан целиком
 * @param file           скачанный файл (при успехе)
 * @param downloadedSize фактически скачано байт
 * @param expectedSize   ожидалось байт (0, если неизвестно)
 * @param error          текст ошибки при неудаче (пусто при успехе)
 */
public record UpdateDownloadResult(
        boolean success,
        Path file,
        long downloadedSize,
        long expectedSize,
        String error) {

    /** Успешная загрузка. */
    public static UpdateDownloadResult downloaded(Path file, long bytes,
                                                  long expectedSize) {
        Objects.requireNonNull(file, "file");
        return new UpdateDownloadResult(true, file, bytes, expectedSize, "");
    }

    /** Неудачная загрузка. */
    public static UpdateDownloadResult failed(String error) {
        return new UpdateDownloadResult(false, null, 0, 0,
                error == null ? "" : error);
    }
}
