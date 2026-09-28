package org.example.launcher.install;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Описывает один файл для загрузки и проверки
 * при установке Minecraft.
 * <p>
 * Каждая задача несёт:
 * <ul>
 *   <li>удалённый URL для загрузки</li>
 *   <li>локальный целевой путь</li>
 *   <li>необязательный ожидаемый SHA1-хэш для проверки</li>
 *   <li>ожидаемый размер файла (для отчёта о прогрессе)</li>
 *   <li>человекочитаемую категорию (клиент, библиотека, нативный файл, ресурс)</li>
 * </ul>
 */
public final class DownloadTask {

    private final String url;
    private final Path targetPath;
    private final String expectedSha1;
    private final long expectedSize;
    private final String category;
    private final String name;

    public DownloadTask(String url, Path targetPath, String expectedSha1,
                        long expectedSize, String category, String name) {
        this.url = Objects.requireNonNull(url, "url");
        this.targetPath = Objects.requireNonNull(targetPath, "targetPath");
        this.expectedSha1 = expectedSha1;
        this.expectedSize = expectedSize;
        this.category = Objects.requireNonNull(category, "category");
        this.name = name != null ? name : targetPath.getFileName().toString();
    }

    public String url() {
        return url;
    }

    public Path targetPath() {
        return targetPath;
    }

    public Optional<String> expectedSha1() {
        return Optional.ofNullable(expectedSha1);
    }

    public long expectedSize() {
        return expectedSize;
    }

    public String category() {
        return category;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "DownloadTask{category='" + category + "', name='" + name
                + "', url='" + url + "', sha1=" + expectedSha1 + '}';
    }
}
