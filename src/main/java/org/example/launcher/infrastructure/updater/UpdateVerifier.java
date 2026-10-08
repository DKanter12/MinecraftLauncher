package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Проверка целостности пакета обновления (SHA-256).
 * Отдельно от скачивания и распаковки: одна ответственность.
 */
public final class UpdateVerifier {

    private UpdateVerifier() {
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
}
