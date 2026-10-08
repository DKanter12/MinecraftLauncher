package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;

/**
 * Проверка пакета обновления: существование, полнота, размер, хэш.
 * Отдельно от скачивания и распаковки: одна ответственность.
 * Повреждённый файл удаляется, обновление не применяется.
 */
public final class UpdateVerifier {

    private UpdateVerifier() {
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

    /**
     * Проверяет SHA-256 файла против ожидаемого.
     *
     * @throws IOException при несовпадении, отсутствии файла
     *                     или недоступности SHA-256
     */
    public static void verifySha256(Path file, String expected) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            if (!hex.toString().equalsIgnoreCase(expected)) {
                throw new IOException(
                        "Update package checksum mismatch — download again");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    /**
     * Полная проверка пакета против версии: размер и хэш.
     * При провале файл удаляется.
     */
    public static void verify(Path file, LauncherVersion version)
            throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(version, "version");
        try {
            verifySize(file, version.fileSize());
            verifySha256(file, version.sha256());
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
