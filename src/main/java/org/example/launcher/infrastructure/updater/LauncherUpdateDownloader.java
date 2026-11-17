package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;
import org.example.launcher.infrastructure.http.UrlFetcher;

/**
 * Скачивает пакет обновления во временную директорию.
 * Текущий лаунчер при этом не изменяется. Проверкой занимается
 * {@link DownloadVerifier}, исключением не бросается —
 * всё в {@link UpdateDownloadResult}.
 */
public class LauncherUpdateDownloader {

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final UrlFetcher fetcher;

    public LauncherUpdateDownloader(UrlFetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    /**
     * Скачивает новую версию по {@code downloadUrl}.
     */
    public UpdateDownloadResult download(LauncherVersion version,
                                         Path destination,
                                         UrlFetcher.ProgressListener progress) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(destination, "destination");
        try {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            fetcher.download(version.downloadUrl(), destination,
                    DOWNLOAD_TIMEOUT, progress);
            long bytes = Files.size(destination);
            return UpdateDownloadResult.downloaded(destination, bytes,
                    version.fileSize());
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // исходная ошибка важнее
            }
            return UpdateDownloadResult.failed(e.getMessage() != null
                    ? e.getMessage() : e.toString());
        }
    }
}
