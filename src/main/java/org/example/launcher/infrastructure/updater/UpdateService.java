package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.Gson;

import org.example.launcher.domain.model.LauncherVersion;
import org.example.launcher.infrastructure.http.UrlFetcher;

/**
 * Точка входа системы обновления для интерфейса: проверка, скачивание,
 * подготовка. Вся работа — в специализированных классах
 * ({@link LauncherUpdateManager}, {@link LauncherUpdateDownloader},
 * {@link LauncherUpdateInstaller}), здесь только связь.
 * Затрагиваются только собственные файлы лаунчера, никогда данные
 * Minecraft или файлы пользователя.
 */
public class UpdateService {

    /** Маркер скачанного обновления, ожидающего перезапуска. */
    public static final String PENDING_FILE_NAME = "pending.json";
    /** Корень подготовки скачанных обновлений. */
    public static final String UPDATES_DIR = "updates";

    public enum Status {
        UP_TO_DATE, AVAILABLE, STAGED, NO_INTERNET, FAILED
    }

    /** Результат проверки обновлений. */
    public record CheckResult(Status status, LauncherUpdate update,
                              String detail) {
    }

    private final LauncherUpdateManager updateManager;
    private final LauncherUpdateDownloader updateDownloader;
    private final LauncherUpdateInstaller updateInstaller;
    private final Path storageRoot;

    public UpdateService(UrlFetcher fetcher, String manifestUrl,
                         Path storageRoot) {
        this(fetcher, manifestUrl, storageRoot, new Gson());
    }

    public UpdateService(UrlFetcher fetcher, String manifestUrl,
                         Path storageRoot, Gson gson) {
        this.storageRoot = storageRoot;
        this.updateManager = new LauncherUpdateManager(
                new LauncherVersionProvider(fetcher, manifestUrl),
                new LauncherVersionChecker(), storageRoot, gson);
        this.updateDownloader = new LauncherUpdateDownloader(fetcher);
        this.updateInstaller = new LauncherUpdateInstaller(storageRoot, gson);
    }

    /**
     * Спрашивает у ветки main, какая версия лаунчера текущая, и
     * сравнивает её с запущенной. Никогда не бросает исключений — сетевые
     * проблемы возвращаются как {@link Status#NO_INTERNET} или
     * {@link Status#FAILED}, чтобы лаунчер просто продолжал работать.
     */
    public CheckResult check() {
        return updateManager.checkForUpdates();
    }

    /**
     * Скачивает пакет обновления во временный файл и проверяет его
     * размер и SHA-256 до начала подготовки.
     */
    public Path download(LauncherUpdate update,
                         UrlFetcher.ProgressListener progress) throws IOException {
        LauncherVersion version = new LauncherVersion(update.version(), null,
                update.packageUrl(), update.size(), update.sha256(),
                update.notes());
        Path tmp = Files.createTempFile("launcher-update-", ".zip");
        UpdateDownloadResult result =
                updateDownloader.download(version, tmp, progress);
        if (!result.success()) {
            Files.deleteIfExists(tmp);
            throw new IOException(result.error().isEmpty()
                    ? "Update download failed" : result.error());
        }
        try {
            DownloadVerifier.verify(tmp, version);
            return tmp;
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /**
     * Распаковывает проверенный пакет в {@code updates/{version}/}
     * и помечает его как ожидающий.
     *
     * @return каталог подготовки
     */
    public Path stage(Path packageFile, LauncherUpdate update) throws IOException {
        return updateInstaller.install(packageFile, update);
    }

    /** @return скачанное обновление, ожидающее перезапуска, если есть. */
    public Optional<LauncherUpdateManager.PendingUpdate> stagedUpdate() {
        return updateManager.stagedUpdate();
    }

    /**
     * Отбрасывает все подготовленные обновления, кроме указанного каталога
     * подготовки (плюс сам скрипт обновляльщика и резервные копии).
     */
    public void clearStagedExcept(Path keepStage) throws IOException {
        Path updates = storageRoot.resolve(UPDATES_DIR);
        if (!Files.isDirectory(updates)) {
            return;
        }
        try (var stream = Files.list(updates)) {
            for (Path child : stream.toList()) {
                String name = child.getFileName().toString();
                if (child.equals(keepStage)
                        || name.equals(UpdateApplier.UPDATER_NAME)
                        || name.startsWith("backup-")) {
                    continue;
                }
                LauncherUpdateInstaller.deleteTree(child);
            }
        }
    }
}
