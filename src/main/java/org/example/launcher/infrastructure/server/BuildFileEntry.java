package org.example.launcher.infrastructure.server;

/**
 * Один файл распространяемой сборки, адресуемый категорией и путём
 * относительно папки этой категории.
 *
 * <p>Хеши целостности: разные источники дают разные хеши
 * (git-манифесты содержат SHA-1, Яндекс Диск сообщает MD5 и SHA-256).
 * Должен присутствовать хотя бы один — проверка использует самый сильный
 * доступный (см. синхронизатором сборок).</p>
 *
 * @param relativePath путь относительно папки категории, разделитель '/'
 * @param category     к какой части сборки относится файл
 * @param sha1         SHA-1 содержимого файла в нижнем регистре, или {@code null}
 * @param sha256       SHA-256 в hex нижнего регистра, или {@code null}
 * @param md5          MD5 в hex нижнего регистра, или {@code null}
 * @param size         размер файла в байтах
 */
public record BuildFileEntry(
        String relativePath,
        BuildFileCategory category,
        String sha1,
        String sha256,
        String md5,
        long size) {

    public BuildFileEntry {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (isBlank(sha1) && isBlank(sha256) && isBlank(md5)) {
            throw new IllegalArgumentException(
                    "at least one checksum (sha1/sha256/md5) is required");
        }
    }

    /** Запись в стиле SHA-1 (git-манифесты, локальные снапшоты). */
    public BuildFileEntry(String relativePath, BuildFileCategory category,
                          String sha1, long size) {
        this(relativePath, category, sha1, null, null, size);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @return папка категории и относительный путь, соединённые через '/',
     *         идентичность файла внутри сборки для диффов обновлений
     *         (добавленные / изменённые / удалённые).
     */
    public String key() {
        return category.folder() + "/" + relativePath;
    }

    /** @return отображаемая форма, напр. {@code mods/sodium.jar}. */
    @Override
    public String toString() {
        return key();
    }
}
