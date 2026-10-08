package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.example.launcher.domain.model.LauncherVersion;
import org.example.launcher.i18n.Lang;
import org.example.launcher.infrastructure.http.UrlFetcher;

/**
 * Главный класс системы обновления лаунчера: проверка при старте
 * и учёт подготовленных обновлений. Сама загрузка/проверка/подготовка —
 * в специализированных классах. Проверка идёт отдельно от запуска,
 * недоступность сервера обновлений ему не мешает.
 */
public class LauncherUpdateManager {

    /** Подготовленное обновление, ожидающее применения. */
    public record PendingUpdate(String version, Path stageDir) {
    }

    private final LauncherVersionProvider versionProvider;
    private final LauncherVersionChecker versionChecker;
    private final Path storageRoot;
    private final Gson gson;

    public LauncherUpdateManager(UrlFetcher fetcher, String manifestUrl,
                                 Path storageRoot) {
        this(new LauncherVersionProvider(fetcher, manifestUrl),
                new LauncherVersionChecker(), storageRoot, new Gson());
    }

    public LauncherUpdateManager(LauncherVersionProvider versionProvider,
                                 LauncherVersionChecker versionChecker,
                                 Path storageRoot, Gson gson) {
        this.versionProvider = Objects.requireNonNull(versionProvider, "versionProvider");
        this.versionChecker = Objects.requireNonNull(versionChecker, "versionChecker");
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    /**
     * Спрашивает сервер о текущей версии и сравнивает с запущенной.
     * Молчит исключением наружу никогда: сетевые проблемы возвращаются
     * статусами, лаунчер просто продолжает работать.
     */
    public UpdateService.CheckResult checkForUpdates() {
        String running = AppVersion.current();
        LauncherVersion remote;
        try {
            remote = versionProvider.getLatestVersion();
        } catch (UnknownHostException | ConnectException
                 | SocketTimeoutException e) {
            return new UpdateService.CheckResult(
                    UpdateService.Status.NO_INTERNET, null,
                    Lang.tr("update.nointernet"));
        } catch (IOException e) {
            if (e.getMessage() != null
                    && e.getMessage().startsWith("Invalid update manifest")) {
                return new UpdateService.CheckResult(
                        UpdateService.Status.FAILED, null,
                        Lang.tr("update.badmanifest", e.getMessage()));
            }
            return new UpdateService.CheckResult(
                    UpdateService.Status.FAILED, null, e.getMessage());
        }
        LauncherUpdate update = new LauncherUpdate(remote.version(),
                remote.releaseNotes(), remote.downloadUrl(),
                remote.sha256(), remote.fileSize());
        if (!versionChecker.isUpdateAvailable(remote)) {
            return new UpdateService.CheckResult(UpdateService.Status.UP_TO_DATE,
                    update, Lang.tr("update.uptodate", running));
        }
        Optional<PendingUpdate> staged = stagedUpdate();
        if (staged.isPresent()
                && AppVersion.compare(staged.get().version(),
                        remote.version()) >= 0) {
            return new UpdateService.CheckResult(UpdateService.Status.STAGED,
                    update, Lang.tr("update.staged", staged.get().version()));
        }
        return new UpdateService.CheckResult(UpdateService.Status.AVAILABLE,
                update, Lang.tr("update.available.status", remote.version()));
    }

    /** Скачанное обновление, ожидающее перезапуска, если есть. */
    public Optional<PendingUpdate> stagedUpdate() {
        Path pendingFile = storageRoot.resolve(UpdateService.UPDATES_DIR)
                .resolve(UpdateService.PENDING_FILE_NAME);
        if (!Files.isRegularFile(pendingFile)) {
            return Optional.empty();
        }
        try {
            JsonObject pending = gson.fromJson(
                    Files.readString(pendingFile), JsonObject.class);
            if (pending == null || !pending.has("version")) {
                return Optional.empty();
            }
            String version = pending.get("version").getAsString();
            Path stageDir = storageRoot.resolve(UpdateService.UPDATES_DIR)
                    .resolve(LauncherUpdateInstaller.sanitizeVersion(version));
            if (!Files.isDirectory(stageDir)) {
                return Optional.empty();
            }
            return Optional.of(new PendingUpdate(version, stageDir));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
