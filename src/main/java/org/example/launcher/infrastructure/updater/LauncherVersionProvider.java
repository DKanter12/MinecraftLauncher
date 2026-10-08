package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;
import org.example.launcher.infrastructure.http.UrlFetcher;

/**
 * Получает информацию о последней версии лаунчера с сервера обновлений.
 * Сам лаунчер не скачивает — только метаданные.
 */
public class LauncherVersionProvider {

    private static final Duration MANIFEST_TIMEOUT = Duration.ofSeconds(10);

    private final UrlFetcher fetcher;
    private final String manifestUrl;

    public LauncherVersionProvider(UrlFetcher fetcher, String manifestUrl) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
    }

    /**
     * Подключается к серверу обновлений и преобразует ответ
     * в {@link LauncherVersion}.
     */
    public LauncherVersion getLatestVersion() throws IOException {
        String json = fetcher.fetchText(manifestUrl, MANIFEST_TIMEOUT);
        LauncherUpdate update;
        try {
            update = LauncherUpdate.parse(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid update manifest: " + e.getMessage(), e);
        }
        return new LauncherVersion(update.version(), null,
                update.packageUrl(), update.size(), update.sha256(),
                update.notes());
    }
}
