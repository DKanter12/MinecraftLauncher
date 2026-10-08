package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.example.launcher.infrastructure.http.UrlFetcher;
import org.example.launcher.i18n.Lang;

/**
 * Проверяет ветку main на обновления лаунчера, скачивает пакет обновления
 * и подготавливает его для установщика — затрагивая только
 * собственные файлы лаунчера, никогда данные Minecraft или файлы пользователя.
 */
public class UpdateService {

    /** Маркер скачанного обновления, ожидающего перезапуска. */
    public static final String PENDING_FILE_NAME = "pending.json";
    /** Корень подготовки скачанных обновлений. */
    public static final String UPDATES_DIR = "updates";

    private static final Duration MANIFEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    /** Подготовленное обновление, ожидающее применения. */
    public record PendingUpdate(String version, Path stageDir) {
    }

    public enum Status {
        UP_TO_DATE, AVAILABLE, STAGED, NO_INTERNET, FAILED
    }

    /** Результат проверки обновлений. */
    public record CheckResult(Status status, LauncherUpdate update,
                              String detail) {
    }

    private final UrlFetcher fetcher;
    private final Gson gson;
    private final String manifestUrl;
    private final Path storageRoot;

    public UpdateService(UrlFetcher fetcher, String manifestUrl,
                         Path storageRoot) {
        this(fetcher, manifestUrl, storageRoot, new Gson());
    }

    public UpdateService(UrlFetcher fetcher, String manifestUrl,
                         Path storageRoot, Gson gson) {
        this.fetcher = fetcher;
        this.manifestUrl = manifestUrl;
        this.storageRoot = storageRoot;
        this.gson = gson;
    }

    /**
     * Спрашивает у ветки main, какая версия лаунчера текущая, и
     * сравнивает её с запущенной. Никогда не бросает исключений — сетевые
     * проблемы возвращаются как {@link Status#NO_INTERNET} или
     * {@link Status#FAILED}, чтобы лаунчер просто продолжал работать.
     */
    public CheckResult check() {
        String running = AppVersion.current();
        LauncherUpdate remote;
        try {
            String json = fetcher.fetchText(manifestUrl, MANIFEST_TIMEOUT);
            remote = LauncherUpdate.parse(json);
        } catch (IllegalArgumentException e) {
            return new CheckResult(Status.FAILED, null,
                    Lang.tr("update.badmanifest", e.getMessage()));
        } catch (UnknownHostException | ConnectException
                | SocketTimeoutException e) {
            return new CheckResult(Status.NO_INTERNET, null,
                    Lang.tr("update.nointernet"));
        } catch (IOException e) {
            return new CheckResult(Status.FAILED, null, e.getMessage());
        }
        if (!AppVersion.isNewer(remote.version(), running)) {
            return new CheckResult(Status.UP_TO_DATE, remote,
                    Lang.tr("update.uptodate", running));
        }
        Optional<PendingUpdate> staged = stagedUpdate();
        if (staged.isPresent()
                && AppVersion.compare(staged.get().version(),
                        remote.version()) >= 0) {
            return new CheckResult(Status.STAGED, remote,
                    Lang.tr("update.staged", staged.get().version()));
        }
        return new CheckResult(Status.AVAILABLE, remote,
                Lang.tr("update.available.status", remote.version()));
    }

    /**
     * Скачивает пакет обновления во временный файл и проверяет его
     * SHA-256 до начала подготовки.
     */
    public Path download(LauncherUpdate update,
                         UrlFetcher.ProgressListener progress) throws IOException {
        Path tmp = Files.createTempFile("launcher-update-", ".zip");
        try {
            fetcher.download(update.packageUrl(), tmp, DOWNLOAD_TIMEOUT,
                    progress);
            verifySha256(tmp, update.sha256());
            return tmp;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e instanceof IOException io ? io : new IOException(e);
        }
    }

    /**
     * Распаковывает проверенный пакет в {@code updates/{version}/}
     * и помечает его как ожидающий. Записи ZIP относительны
     * домашнего каталога приложения; записи, выходящие за него, отклоняются.
     *
     * @return каталог подготовки
     */
    public Path stage(Path packageFile, LauncherUpdate update) throws IOException {
        Path stageDir = storageRoot.resolve(UPDATES_DIR)
                .resolve(sanitizeVersion(update.version()));
        deleteTree(stageDir);
        Files.createDirectories(stageDir);
        try (InputStream in = Files.newInputStream(packageFile);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = stageDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(stageDir)) {
                    throw new IOException(
                            "Update package escapes its directory: "
                                    + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zip, target);
                }
                zip.closeEntry();
            }
        }
        JsonObject pending = new JsonObject();
        pending.addProperty("version", update.version());
        Files.writeString(stageDir.resolveSibling(PENDING_FILE_NAME),
                gson.toJson(pending));
        Files.deleteIfExists(packageFile);
        return stageDir;
    }

    /** @return скачанное обновление, ожидающее перезапуска, если есть. */
    public Optional<PendingUpdate> stagedUpdate() {
        Path pendingFile = storageRoot.resolve(UPDATES_DIR)
                .resolve(PENDING_FILE_NAME);
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
            Path stageDir = storageRoot.resolve(UPDATES_DIR)
                    .resolve(sanitizeVersion(version));
            if (!Files.isDirectory(stageDir)) {
                return Optional.empty();
            }
            return Optional.of(new PendingUpdate(version, stageDir));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Отбрасывает все подготовленные обновления, кроме указанного каталога
     * подготовки (плюс сам скрипт обновляльщика).
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
                        || name.equals(UpdateApplier.UPDATER_NAME)) {
                    continue;
                }
                deleteTree(child);
            }
        }
    }

    private void verifySha256(Path file, String expected) throws IOException {
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

    private static String sanitizeVersion(String version) {
        String clean = version.replaceAll("[^A-Za-z0-9._-]+", "-");
        return clean.isBlank() ? "unknown" : clean;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder())
                    .toList()) {
                Files.delete(path);
            }
        }
    }
}
