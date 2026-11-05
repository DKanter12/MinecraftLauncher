package org.example.launcher.application.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.example.launcher.domain.model.MinecraftVersion;import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.infrastructure.download.DownloadResult;
import org.example.launcher.infrastructure.download.DownloadTask;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.server.BuildDescriptor;
import org.example.launcher.infrastructure.server.BuildFileCategory;
import org.example.launcher.infrastructure.server.BuildFileEntry;
import org.example.launcher.infrastructure.server.BuildSummary;
import org.example.launcher.infrastructure.server.ServerSession;
import org.example.launcher.infrastructure.server.api.LauncherServerApi;

/**
 * Установка «сборки от администратора»: полный цикл от краткого описания
 * на сервере до готового игрового инстанса.
 *
 * <p>Пайплайн: {@code fetchBuild} → подбор версии Minecraft в манифесте
 * Mojang и версии загрузчика в реестре → создание инстанса через
 * {@link CreateBuildUseCase} (версия игры + загрузчик + каталог + реестр)
 * → синхронизация файлов сборки по категориям ({@code mods}, {@code config},
 * {@code resources}) с проверкой хешей → сохранение манифеста
 * {@code build.json} внутри каталога инстанса для будущих обновлений.</p>
 *
 * <p>Класс ничего не знает про UI: сетевые вызовы блокирующие и должны
 * запускаться из фоновой задачи; прогресс уходит в переданный
 * {@link InstallationProgress}.</p>
 */
public class ServerBuildInstaller {

    /** Итог установки: созданный профиль плюс сводка по файлам. */
    public record Outcome(ModdedProfile profile,
                          int downloaded,
                          int skipped,
                          int failed,
                          long totalBytes) {
    }

    private static final String MANIFEST_NAME = "build.json";

    private final LauncherServerApi api;
    private final ModLoaderRegistry loaders;
    private final CreateBuildUseCase createBuild;
    private final FileSystemBuildRepository builds;
    private final Gson gson;

    public ServerBuildInstaller(LauncherServerApi api,
                                ModLoaderRegistry loaders,
                                CreateBuildUseCase createBuild,
                                FileSystemBuildRepository builds) {
        this.api = Objects.requireNonNull(api, "api");
        this.loaders = Objects.requireNonNull(loaders, "loaders");
        this.createBuild = Objects.requireNonNull(createBuild, "createBuild");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    /**
     * Полная установка серверной сборки.
     *
     * @param session    сессия (может быть {@code null} для публичных источников)
     * @param summary    краткое описание сборки из списка сервера
     * @param name       имя инстанса; пустое — берётся displayName сборки
     * @param mcVersion  запись манифеста Mojang под версию сборки
     * @param progress   получение обновлений прогресса
     * @return итог: созданный инстанс и сводка по файлам
     * @throws IOException при сетевых ошибках, отсутствии версии в манифесте
     *                     или ошибках образа сборки
     */
    public Outcome provision(ServerSession session,
                             BuildSummary summary,
                             String name,
                             MinecraftVersion mcVersion,
                             InstallationProgress progress)
            throws IOException {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(mcVersion, "mcVersion");
        InstallationProgress progressSink =
                progress == null ? InstallationProgress.NONE : progress;

        BuildDescriptor descriptor = api.fetchBuild(session, summary.id());
        verifyDescriptorMatches(descriptor, summary, mcVersion);

        org.example.launcher.domain.model.ModLoaderVersion resolved =
                resolveLoader(summary);
        Build build = new Build(
                name == null || name.isBlank() ? summary.displayName() : name.trim(),
                summary.minecraftVersion(),
                summary.loaderType(),
                summary.loaderType() == ModLoaderType.VANILLA ? "" : summary.loaderVersion());
        BuildRequest request = new BuildRequest(
                build, mcVersion,
                resolved == null ? null : resolved,
                List.of());

        ModdedProfile profile = createBuild.createBuild(request, progressSink);
        SyncOutcome sync = syncFiles(session, descriptor, profile, progressSink);
        writeInstanceManifest(descriptor, profile);

        return new Outcome(profile, sync.downloaded, sync.skipped,
                sync.failed, sync.bytes);
    }

    /** Находит версию загрузчика реестром; {@code null} для ваниллы. */
    private org.example.launcher.domain.model.ModLoaderVersion resolveLoader(
            BuildSummary summary) throws IOException {
        if (summary.loaderType() == ModLoaderType.VANILLA) {
            return null;
        }
        ModLoaderRegistry.Entry entry = loaders.get(summary.loaderType())
                .orElseThrow(() -> new IOException(
                        "Loader is not supported: " + summary.loaderType()));
        List<org.example.launcher.domain.model.ModLoaderVersion> versions =
                entry.provider().fetchVersions(summary.minecraftVersion());
        String wanted = summary.loaderVersion() == null ? "" : summary.loaderVersion();
        for (org.example.launcher.domain.model.ModLoaderVersion v : versions) {
            if (wanted.equals(v.loaderVersion())) {
                return v;
            }
        }
        throw new IOException("Loader version is not available: "
                + summary.loaderType().displayName() + " " + wanted
                + " for Minecraft " + summary.minecraftVersion());
    }

    /**
     * Сверяет дескриптор с тем, что показал список: защита от расхождений
     * метаданных сборки (например, отличный загрузчик или версия игры).
     */
    private static void verifyDescriptorMatches(BuildDescriptor descriptor,
                                                BuildSummary summary,
                                                MinecraftVersion mcVersion)
            throws IOException {
        BuildSummary fetched = descriptor.summary();
        if (!fetched.minecraftVersion().equals(summary.minecraftVersion())) {
            throw new IOException("Build metadata mismatch: server reports "
                    + fetched.minecraftVersion() + ", expected "
                    + summary.minecraftVersion());
        }
        if (!mcVersion.id().equals(summary.minecraftVersion())) {
            throw new IOException("Minecraft version " + mcVersion.id()
                    + " does not belong to build " + summary.displayName());
        }
        if (fetched.loaderType() != summary.loaderType()) {
            throw new IOException("Build metadata mismatch: loader changed from "
                    + summary.loaderType() + " to " + fetched.loaderType());
        }
    }

    // ------------------------------------------------------------------
    //  Синхронизация файлов
    // ------------------------------------------------------------------

    private record SyncOutcome(int downloaded, int skipped, int failed,
                               long bytes) {
    }

    /**
     * Скачивает все файловые записи сборки в каталог инстанса.
     * Категория {@code MINECRAFT} пропускается (описательные метаданные);
     * хеш проверяется по первому доступному (SHA-1 → SHA-256 → MD5).
     */
    private SyncOutcome syncFiles(ServerSession session,
                                  BuildDescriptor descriptor,
                                  ModdedProfile profile,
                                  InstallationProgress progress) throws IOException {
        Path gameDir = builds.resolveGameDir(profile);
        List<BuildFileEntry> files = new ArrayList<>();
        for (BuildFileEntry entry : descriptor.files()) {
            if (entry.category() != BuildFileCategory.MINECRAFT) {
                files.add(entry);
            }
        }
        progress.onStart(files.size(), files.stream()
                .mapToLong(BuildFileEntry::size).sum());

        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        long bytes = 0;
        int index = 0;
        List<DownloadResult> failures = new ArrayList<>();
        for (BuildFileEntry entry : files) {
            DownloadTask task = taskOf(entry);
            progress.onFileStart(index, task);
            DownloadResult result = downloadOne(session, descriptor, entry, gameDir);
            progress.onFileComplete(index, result);
            if (result.isDownloaded()) {
                downloaded++;
                bytes += result.bytesDownloaded();
            } else if (result.isSkipped()) {
                skipped++;
            } else {
                failed++;
                failures.add(result);
            }
            index++;
        }
        InstallationResult summaryResult = new InstallationResult(
                files.size(), downloaded, skipped, failed, bytes, failures);
        progress.onComplete(summaryResult);
        return new SyncOutcome(downloaded, skipped, failed, bytes);
    }

    private DownloadTask taskOf(BuildFileEntry entry) {
        return new DownloadTask(entry.key(),
                Path.of(entry.category().folder(), entry.relativePath()),
                entry.sha1(), entry.size(),
                entry.category().name().toLowerCase(Locale.ROOT),
                entry.relativePath());
    }

    private DownloadResult downloadOne(ServerSession session,
                                       BuildDescriptor descriptor,
                                       BuildFileEntry entry,
                                       Path gameDir) {
        DownloadTask task = taskOf(entry);
        try {
            Path target = safeTarget(gameDir, entry);
            if (Files.isRegularFile(target) && checksumMatches(target, entry)) {
                return DownloadResult.skipped(task);
            }
            Files.createDirectories(target.getParent());
            api.downloadFile(session, descriptor, entry, target);
            if (!checksumMatches(target, entry)) {
                Files.deleteIfExists(target);
                return DownloadResult.failed(task,
                        "Checksum mismatch after download", 1);
            }
            return DownloadResult.downloaded(task,
                    Files.size(target), 1);
        } catch (IOException e) {
            return DownloadResult.failed(task, e.getMessage(), 1);
        }
    }

    /** Каталог категории + относительный путь; переходы наружу запрещены. */
    private static Path safeTarget(Path gameDir, BuildFileEntry entry)
            throws IOException {
        String relative = entry.relativePath();
        if (relative.startsWith("/") || relative.contains("\\")
                || relative.contains("..") || relative.contains(":")) {
            throw new IOException("Unsafe path in build: " + relative);
        }
        return gameDir.resolve(entry.category().folder())
                .resolve(relative).normalize();
    }

    /**
     * Проверяет файл по первому доступному хешу.
     * Возвращает {@code true}, когда хеш совпал или проверить нечего
     * (не бывает: {@link BuildFileEntry} требует хотя бы один).
     */
    static boolean checksumMatches(Path file, BuildFileEntry entry) throws IOException {
        if (entry.sha1() != null && !entry.sha1().isBlank()) {
            return digestMatches(file, "SHA-1", entry.sha1());
        }
        if (entry.sha256() != null && !entry.sha256().isBlank()) {
            return digestMatches(file, "SHA-256", entry.sha256());
        }
        if (entry.md5() != null && !entry.md5().isBlank()) {
            return digestMatches(file, "MD5", entry.md5());
        }
        return true;
    }

    private static boolean digestMatches(Path file, String algorithm,
                                         String expected) throws IOException {
        String actual = digestOf(file, algorithm);
        return actual.equalsIgnoreCase(expected.trim());
    }

    static String digestOf(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Digest unavailable: " + algorithm, e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Пишет манифест {@code build.json} в каталог инстанса: id, версия,
     * метаданные и полный список файлов — основа для будущих обновлений
     * сравнением с серверным дескриптором.
     */
    private void writeInstanceManifest(BuildDescriptor descriptor,
                                       ModdedProfile profile) throws IOException {
        Path gameDir = builds.resolveGameDir(profile);
        Path manifest = gameDir.resolve(MANIFEST_NAME);
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("id", descriptor.id());
        root.addProperty("version", descriptor.version());
        root.addProperty("displayName", descriptor.summary().displayName());
        if (descriptor.summary().description() != null) {
            root.addProperty("description",
                    descriptor.summary().description());
        }
        root.addProperty("loaderType", descriptor.summary().loaderType().name());
        root.addProperty("minecraftVersion", descriptor.summary().minecraftVersion());
        root.addProperty("loaderVersion", descriptor.summary().loaderVersion());
        root.addProperty("fetchTime", OffsetDateTime.now().toString());
        root.addProperty("origin", descriptor.origin().name());
        JsonArray fileArray = new JsonArray();
        for (BuildFileEntry entry : descriptor.files()) {
            JsonObject file = new JsonObject();
            file.addProperty("key", entry.key());
            if (entry.sha1() != null) file.addProperty("sha1", entry.sha1());
            if (entry.sha256() != null) file.addProperty("sha256", entry.sha256());
            if (entry.md5() != null) file.addProperty("md5", entry.md5());
            file.addProperty("size", entry.size());
            fileArray.add(file);
        }
        root.add("files", fileArray);
        Files.writeString(manifest, gson.toJson(root));
    }
}
