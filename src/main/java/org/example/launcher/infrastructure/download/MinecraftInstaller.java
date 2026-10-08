package org.example.launcher.infrastructure.download;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.domain.model.AssetIndex;
import org.example.launcher.domain.model.AssetIndexContent;
import org.example.launcher.domain.model.AssetObject;
import org.example.launcher.domain.model.DownloadInfo;
import org.example.launcher.domain.model.Library;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.mojang.AssetIndexService;
import org.example.launcher.infrastructure.mojang.MojangAssetIndexService;
import org.example.launcher.infrastructure.common.LibraryPaths;
import org.example.launcher.infrastructure.common.OsDetector;
import org.example.launcher.domain.port.RetryPolicy;
import org.example.launcher.infrastructure.download.FixedRetryPolicy;

/**
 * Реализация {@link InstallationService} для Minecraft.
 * <p>
 * Использует {@link GameDirectory} для всего разрешения путей, обеспечивая
 * единообразную раскладку локального хранилища с общими библиотеками и ресурсами,
 * переиспользуемыми версиями без повторной загрузки.
 * <p>
 * Порядок работы:
 * <ol>
 *   <li>Строит полный список {@link DownloadTask} из
 *       метаданных версии (клиентский JAR, библиотеки, нативные файлы, ресурсы).</li>
 *   <li>Для каждой задачи: проверяет наличие файла локально и совпадение его SHA-1
 *       хэша. При совпадении пропускает. Иначе загружает.</li>
 *   <li>После каждой загрузки проверяет SHA-1 хэш. При несовпадении
 *       повреждённый файл удаляется и загрузка
 *       повторяется (до {@link #maxRetries} попыток).</li>
 *   <li>Сообщает прогресс через {@link InstallationProgress}.</li>
 * </ol>
 * <p>
 * Общие библиотеки и ресурсы хранятся в общих каталогах
 * ({@code libraries/}, {@code assets/objects/}) и адресуются
 * по Maven-пути или SHA-1 хэшу соответственно, поэтому один и тот же файл
 * никогда не загружается дважды для разных версий.
 */
public class MinecraftInstaller implements InstallationService {

    public static final String CATEGORY_CLIENT = "client";
    public static final String CATEGORY_LIBRARY = "library";
    public static final String CATEGORY_NATIVE = "native";
    public static final String CATEGORY_ASSET = "asset";
    public static final String CATEGORY_ASSET_INDEX = "asset-index";

    private static final String ASSET_OBJECTS_URL = "https://resources.download.minecraft.net/";

    private final FileDownloader fileDownloader;
    private final ChecksumVerifier checksumVerifier;
    private final AssetIndexService assetIndexService;
    private final RetryPolicy retryPolicy;

    public MinecraftInstaller(FileDownloader fileDownloader,
                              ChecksumVerifier checksumVerifier,
                              AssetIndexService assetIndexService) {
        this(fileDownloader, checksumVerifier, assetIndexService,
                new FixedRetryPolicy());
    }

    public MinecraftInstaller(FileDownloader fileDownloader,
                              ChecksumVerifier checksumVerifier,
                              AssetIndexService assetIndexService,
                              int maxRetries) {
        this(fileDownloader, checksumVerifier, assetIndexService,
                new FixedRetryPolicy(maxRetries));
    }

    public MinecraftInstaller(FileDownloader fileDownloader,
                              ChecksumVerifier checksumVerifier,
                              AssetIndexService assetIndexService,
                              RetryPolicy retryPolicy) {
        this.fileDownloader = Objects.requireNonNull(fileDownloader, "fileDownloader");
        this.checksumVerifier = Objects.requireNonNull(checksumVerifier, "checksumVerifier");
        this.assetIndexService = Objects.requireNonNull(assetIndexService, "assetIndexService");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    // ------------------------------------------------------------------
    //  Оркестрация установки
    // ------------------------------------------------------------------

    @Override
    public InstallationResult install(VersionMetadata metadata,
                                      GameDirectory gameDir,
                                      InstallationProgress progress) throws IOException {
        gameDir.createDirectories();

        List<DownloadTask> tasks = buildDownloadTasks(metadata, gameDir);
        long totalBytes = tasks.stream().mapToLong(DownloadTask::expectedSize).sum();

        progress.onStart(tasks.size(), totalBytes);

        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        long totalBytesDownloaded = 0;
        List<DownloadResult> failedTasks = new ArrayList<>();

        for (int i = 0; i < tasks.size(); i++) {
            DownloadTask task = tasks.get(i);
            progress.onFileStart(i, task);

            DownloadResult result = processTask(task);
            progress.onFileComplete(i, result);

            if (result.isDownloaded()) {
                downloaded++;
                totalBytesDownloaded += result.bytesDownloaded();
            } else if (result.isSkipped()) {
                skipped++;
            } else {
                failed++;
                failedTasks.add(result);
            }
        }

        InstallationResult installationResult = new InstallationResult(
                tasks.size(), downloaded, skipped, failed,
                totalBytesDownloaded, failedTasks);
        progress.onComplete(installationResult);
        return installationResult;
    }

    /**
     * Обрабатывает одну задачу загрузки с проверкой хэша и повторами:
     * <ol>
     *   <li>Если файл есть локально и его SHA-1 совпадает → ПРОПУСК.</li>
     *   <li>Если файл есть, но хэш не совпал → удалить повреждённый файл.</li>
     *   <li>Загрузить файл.</li>
     *   <li>Проверить SHA-1 загруженного файла.</li>
     *   <li>При несовпадении хэша → удалить и повторить
     *       (число попыток задаёт {@link RetryPolicy}).</li>
     *   <li>Если все попытки исчерпаны → ОШИБКА.</li>
     * </ol>
     */
    private DownloadResult processTask(DownloadTask task) {
        Path localPath = task.targetPath();
        Optional<String> expectedSha1 = task.expectedSha1();

        if (Files.isRegularFile(localPath)) {
            if (expectedSha1.isPresent()) {
                if (checksumVerifier.verify(localPath, expectedSha1.get())) {
                    return DownloadResult.skipped(task);
                }
                deleteFile(localPath);
            } else {
                return DownloadResult.skipped(task);
            }
        }

        int maxAttempts = retryPolicy.maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                long bytes = fileDownloader.download(task.url(), localPath);

                if (expectedSha1.isPresent()) {
                    if (!checksumVerifier.verify(localPath, expectedSha1.get())) {
                        deleteFile(localPath);
                        if (attempt < maxAttempts) {
                            continue;
                        }
                        return DownloadResult.failed(task,
                                "SHA-1 mismatch after " + maxAttempts
                                        + " attempts: expected " + expectedSha1.get()
                                        + " for " + task.name(),
                                attempt);
                    }
                }

                return DownloadResult.downloaded(task, bytes, attempt);

            } catch (IOException e) {
                deleteFile(localPath);
                if (attempt < maxAttempts) {
                    continue;
                }
                return DownloadResult.failed(task, e.getMessage(), attempt);
            }
        }

        return DownloadResult.failed(task, "Exhausted all retries", maxAttempts);
    }

    // ------------------------------------------------------------------
    //  Построение списка задач (чистое, тестируемое)
    // ------------------------------------------------------------------

    /**
     * Строит полный список файлов, необходимых для
     * заданных метаданных версии, ничего не загружая.
     *
     * @param metadata метаданные версии
     * @param gameDir  раскладка игрового каталога
     * @return упорядоченный список задач загрузки
     * @throws IOException если индекс ресурсов не удаётся получить
     */
    public List<DownloadTask> buildDownloadTasks(VersionMetadata metadata, GameDirectory gameDir)
            throws IOException {

        List<DownloadTask> tasks = new ArrayList<>();

        tasks.addAll(buildClientJarTasks(metadata, gameDir));
        tasks.addAll(buildLibraryTasks(metadata, gameDir));
        tasks.addAll(buildNativeTasks(metadata, gameDir));
        tasks.addAll(buildAssetTasks(metadata, gameDir));

        return tasks;
    }

    /**
     * Строит задачу загрузки клиентского JAR (специфичен для версии, не общий).
     */
    public List<DownloadTask> buildClientJarTasks(VersionMetadata metadata, GameDirectory gameDir) {
        List<DownloadTask> tasks = new ArrayList<>();
        metadata.clientDownload().ifPresent(dl -> {
            Path target = gameDir.clientJar(metadata.id());
            tasks.add(new DownloadTask(
                    dl.url(), target, dl.sha1().orElse(null), dl.size(),
                    CATEGORY_CLIENT, metadata.id() + ".jar"));
        });
        return tasks;
    }

    /**
     * Строит задачи загрузки JAR библиотек для всех библиотек с
     * загрузкой артефакта. Библиотеки хранятся в общем каталоге
     * и переиспользуются версиями.
     */
    public List<DownloadTask> buildLibraryTasks(VersionMetadata metadata, GameDirectory gameDir) {
        List<DownloadTask> tasks = new ArrayList<>();

        for (Library lib : metadata.libraries()) {
            lib.artifact().ifPresent(artifact -> {
                Path target = resolveLibraryPath(gameDir, artifact);
                tasks.add(new DownloadTask(
                        artifact.url(), target,
                        artifact.sha1().orElse(null), artifact.size(),
                        CATEGORY_LIBRARY, lib.name()));
            });
        }
        return tasks;
    }

    /**
     * Строит задачи загрузки нативных библиотек для текущей ОС.
     * Нативные JAR хранятся рядом с прочими библиотеками, но в
     * пути, специфичном для классификатора.
     */
    public List<DownloadTask> buildNativeTasks(VersionMetadata metadata, GameDirectory gameDir) {
        List<DownloadTask> tasks = new ArrayList<>();
        String osName = OsDetector.mojangName();

        for (Library lib : metadata.libraries()) {
            Optional<DownloadInfo> nativeDl = lib.nativeDownload(osName);
            if (nativeDl.isEmpty()) continue;

            DownloadInfo dl = nativeDl.get();
            Path target = resolveLibraryPath(gameDir, dl);
            tasks.add(new DownloadTask(
                    dl.url(), target,
                    dl.sha1().orElse(null), dl.size(),
                    CATEGORY_NATIVE, lib.name() + " (" + osName + ")"));
        }
        return tasks;
    }

    /**
     * Строит задачи загрузки ресурсов: сначала получает индекс ресурсов,
     * затем создаёт задачу для каждого объекта ресурсов. Ресурсы
     * адресуются по хэшу и общие для всех версий.
     * <p>
     * Также включает сам JSON индекса ресурсов как задачу для его
     * локального кэширования.
     */
    public List<DownloadTask> buildAssetTasks(VersionMetadata metadata, GameDirectory gameDir)
            throws IOException {
        List<DownloadTask> tasks = new ArrayList<>();

        Optional<AssetIndex> ai = metadata.assetIndex();
        if (ai.isEmpty()) return tasks;

        AssetIndex assetIndex = ai.get();
        Path indexFile = gameDir.assetIndexFile(assetIndex.id());

        tasks.add(new DownloadTask(
                assetIndex.url(), indexFile,
                assetIndex.sha1().orElse(null), assetIndex.size(),
                CATEGORY_ASSET_INDEX, assetIndex.id() + ".json"));

        AssetIndexContent content = loadAssetIndexContent(assetIndex, indexFile);

        for (var entry : content.objects().entrySet()) {
            AssetObject obj = entry.getValue();
            if (obj.hash() == null) continue;

            Path target = gameDir.assetObject(obj.hashPrefix(), obj.hash());
            String url = ASSET_OBJECTS_URL + obj.hashPrefix() + "/" + obj.hash();

            tasks.add(new DownloadTask(
                    url, target, obj.hash(), obj.size(),
                    CATEGORY_ASSET, entry.getKey()));
        }

        return tasks;
    }

    /**
     * Загружает содержимое индекса ресурсов из локального кэша, если он есть и
     * валиден, иначе получает из сети. Это избавляет от
     * повторной загрузки индекса ресурсов при каждой установке, если он
     * уже закэширован локально.
     */
    private AssetIndexContent loadAssetIndexContent(AssetIndex assetIndex, Path indexFile)
            throws IOException {
        if (Files.isRegularFile(indexFile) && assetIndex.sha1().isPresent()) {
            if (checksumVerifier.verify(indexFile, assetIndex.sha1().get())) {
                try {
                    String json = Files.readString(indexFile);
                    return new MojangAssetIndexService().parseIndex(json);
                } catch (IOException e) {
                    // Перейти к получению из сети
                }
            }
        }
        return assetIndexService.fetchIndex(assetIndex);
    }

    // ------------------------------------------------------------------
    //  Помощники
    // ------------------------------------------------------------------

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    /**
     * Разрешает артефакт библиотеки в локальный путь.
     *
     * @see LibraryPaths#resolve(GameDirectory, DownloadInfo)
     */
    private static Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        return LibraryPaths.resolve(gameDir, dl);
    }
}
