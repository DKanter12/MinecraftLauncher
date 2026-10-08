package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;

/**
 * Проверка скачанного обновления: существование, полнота, размер, хэш.
 * Повреждённый файл удаляется, обновление не применяется,
 * пользователь видит ошибку.
 */
public final class DownloadVerifier {

    private DownloadVerifier() {
    }

    /** Размер скачанного файла соответствует ожидаемому (0 — пропуск). */
    public static void verifySize(Path file, long expectedSize)
            throws IOException {
        Objects.requireNonNull(file, "file");
        if (expectedSize <= 0) {
            return;
        }
        long actual = Files.size(file);
        if (actual != expectedSize) {
            throw new IOException("Update package size mismatch: expected "
                    + expectedSize + " bytes, got " + actual);
        }
    }

    /** Контрольная сумма файла соответствует ожидаемой. */
    public static void verifyHash(Path file, String expectedHash)
            throws IOException {
        UpdateVerifier.verifySha256(file, expectedHash);
    }

    /**
     * Полная проверка: файл существует и полностью скачан, размер
     * и хэш соответствуют версии. При провале файл удаляется.
     */
    public static void verify(Path file, LauncherVersion version)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(version, "version");
        try {
            verifySize(file, version.fileSize());
            UpdateVerifier.verifySha256(file, version.sha256());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // исходная ошибка важнее
            }
            throw e;
        }
    }
}
