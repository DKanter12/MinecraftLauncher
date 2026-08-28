package org.example.launcher.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;
import org.example.launcher.model.AssetObject;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.Library;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.AssetIndexService;
import org.example.launcher.service.MojangAssetIndexService;
import org.example.launcher.util.OsDetector;

/**
 * {@link InstallationService} implementation for Minecraft.
 * <p>
 * Uses {@link GameDirectory} for all path resolution, ensuring a
 * consistent local storage layout with shared libraries and assets
 * that are reused across versions without re-downloading.
 * <p>
 * Flow:
 * <ol>
 *   <li>Builds the complete list of {@link DownloadTask}s from
 *       version metadata (client JAR, libraries, natives, assets).</li>
 *   <li>For each task: checks if the file exists locally and its SHA-1
 *       hash matches. If so, skips. Otherwise, downloads.</li>
 *   <li>After each download, verifies the SHA-1 hash. If the hash
 *       does not match, the corrupt file is deleted and the download
 *       is retried (up to {@link #maxRetries} times).</li>
 *   <li>Reports progress via {@link InstallationProgress}.</li>
 * </ol>
 * <p>
 * Shared libraries and assets are stored in common directories
 * ({@code libraries/}, {@code assets/objects/}) and are addressed by
 * their Maven path or SHA-1 hash respectively, so the same file is
 * never downloaded twice when multiple versions need it.
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
    private final int maxRetries;

    public MinecraftInstaller(FileDownloader fileDownloader,
                              ChecksumVerifier checksumVerifier,
                              AssetIndexService assetIndexService) {
        this(fileDownloader, checksumVerifier, assetIndexService, 3);
    }

    public MinecraftInstaller(FileDownloader fileDownloader,
                              ChecksumVerifier checksumVerifier,
                              AssetIndexService assetIndexService,
                              int maxRetries) {
        this.fileDownloader = fileDownloader;
        this.checksumVerifier = checksumVerifier;
        this.assetIndexService = assetIndexService;
        this.maxRetries = Math.max(1, maxRetries);
    }

    // ------------------------------------------------------------------
    //  Installation orchestration
    // ------------------------------------------------------------------

    @Override
    public InstallationResult install(MinecraftVersion version,
                                      VersionMetadata metadata,
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
     * Processes a single download task with hash verification and retry:
     * <ol>
     *   <li>If the file exists locally and its SHA-1 matches → SKIP.</li>
     *   <li>If the file exists but hash mismatches → delete corrupt file.</li>
     *   <li>Download the file.</li>
     *   <li>Verify the downloaded file's SHA-1.</li>
     *   <li>If hash mismatches → delete and retry (up to {@link #maxRetries}).</li>
     *   <li>If all retries fail → FAILED.</li>
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

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long bytes = fileDownloader.download(task.url(), localPath);

                if (expectedSha1.isPresent()) {
                    if (!checksumVerifier.verify(localPath, expectedSha1.get())) {
                        deleteFile(localPath);
                        if (attempt < maxRetries) {
                            continue;
                        }
                        return DownloadResult.failed(task,
                                "SHA-1 mismatch after " + maxRetries
                                        + " attempts: expected " + expectedSha1.get()
                                        + " for " + task.name(),
                                attempt);
                    }
                }

                return DownloadResult.downloaded(task, bytes, attempt);

            } catch (IOException e) {
                deleteFile(localPath);
                if (attempt < maxRetries) {
                    continue;
                }
                return DownloadResult.failed(task, e.getMessage(), attempt);
            }
        }

        return DownloadResult.failed(task, "Exhausted all retries", maxRetries);
    }

    // ------------------------------------------------------------------
    //  Task list construction (pure, testable)
    // ------------------------------------------------------------------

    /**
     * Builds the complete list of files that need to be available for
     * the given version metadata, without downloading anything.
     *
     * @param metadata the version metadata
     * @param gameDir  the game directory layout
     * @return an ordered list of download tasks
     * @throws IOException if the asset index cannot be fetched
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
     * Builds client JAR download task (version-specific, not shared).
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
     * Builds library JAR download tasks for all libraries that have
     * an artifact download. Libraries are stored in a shared directory
     * and reused across versions.
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
     * Builds native library download tasks for the current OS.
     * Native JARs are stored alongside other libraries but in a
     * classifier-specific path.
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
     * Builds asset download tasks by first fetching the asset index,
     * then creating a task for every asset object. Assets are
     * hash-addressed and shared across all versions.
     * <p>
     * Also includes the asset index JSON itself as a task so it is
     * cached locally.
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
     * Loads asset index content from the local cache if present and
     * valid, otherwise fetches from the network. This avoids
     * re-downloading the asset index on every install when it is
     * already cached locally.
     */
    private AssetIndexContent loadAssetIndexContent(AssetIndex assetIndex, Path indexFile)
            throws IOException {
        if (Files.isRegularFile(indexFile) && assetIndex.sha1().isPresent()) {
            if (checksumVerifier.verify(indexFile, assetIndex.sha1().get())) {
                try {
                    String json = Files.readString(indexFile);
                    return new MojangAssetIndexService().parseIndex(json);
                } catch (IOException e) {
                    // Fall through to network fetch
                }
            }
        }
        return assetIndexService.fetchIndex(assetIndex);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    /**
     * Resolves a library artifact to its local path using the
     * {@code path} field from the download info when available,
     * otherwise derives it from the URL.
     */
    private static Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        Optional<String> pathOpt = dl.path();
        if (pathOpt.isPresent()) {
            return gameDir.library(pathOpt.get());
        }
        String url = dl.url();
        if (url != null && !url.isBlank()) {
            int idx = url.lastIndexOf('/');
            String fileName = (idx >= 0) ? url.substring(idx + 1) : url;
            return gameDir.librariesDir().resolve(fileName);
        }
        return gameDir.librariesDir().resolve("unknown.jar");
    }
}
