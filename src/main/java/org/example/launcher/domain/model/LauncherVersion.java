package org.example.launcher.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Версия самого лаунчера из серверного манифеста.
 * Не путать с версией Minecraft.
 *
 * @param version      номер версии, например {@code "1.2.0"}
 * @param releaseDate  дата выпуска, если сервер её отдаёт
 * @param downloadUrl  URL пакета обновления
 * @param fileSize     размер файла в байтах (0, если неизвестен)
 * @param sha256       контрольная сумма пакета
 * @param releaseNotes список изменений человекочитаемым текстом
 */
public record LauncherVersion(
        String version,
        String releaseDate,
        String downloadUrl,
        long fileSize,
        String sha256,
        String releaseNotes) {

    public LauncherVersion {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(downloadUrl, "downloadUrl");
        Objects.requireNonNull(sha256, "sha256");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (releaseNotes == null) {
            releaseNotes = "";
        }
    }

    /** Дата выпуска, если сервер её отдаёт. */
    public Optional<String> releaseDateOpt() {
        return Optional.ofNullable(releaseDate);
    }
}
