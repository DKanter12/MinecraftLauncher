package org.example.launcher.infrastructure.download;

import java.nio.file.Path;

/**
 * Проверяет целостность файла по контрольной сумме (SHA1).
 * <p>
 * Используется для определения соответствия локального файла ожидаемому хэшу,
 * обеспечивая логику пропуска существующих валидных файлов при установке.
 */
public interface ChecksumVerifier {

    /**
     * Проверяет, что файл по пути {@code path} имеет ожидаемый SHA1-хэш.
     *
     * @param path         проверяемый локальный файл
     * @param expectedSha1 ожидаемая hex-строка SHA1 (в нижнем регистре)
     * @return {@code true}, если файл существует и хэш совпадает;
     *         иначе {@code false}
     */
    boolean verify(Path path, String expectedSha1);
}
