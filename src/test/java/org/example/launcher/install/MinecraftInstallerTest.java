package org.example.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;
import org.example.launcher.model.AssetObject;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.Library;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.AssetIndexService;
import org.example.launcher.version.StandardVersionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MinecraftInstaller")
class MinecraftInstallerTest {

    // ----------------------------------------------------------------
    //  Test doubles
    // ----------------------------------------------------------------

    /**
     * Fake downloader that records all download calls and writes dummy
     * content. By default the written content "passes" hash
     * verification because {@link FakeVerifier} auto-validates existing
     * files.
     */
    private static class FakeDownloader implements FileDownloader {
        final List<String> downloadedUrls = new ArrayList<>();
        final Map<Path, Integer> downloadCounts = new HashMap<>();
        IOException exceptionToThrow;
        String corruptContent = null;

        @Override
        public long download(String url, Path targetPath) throws IOException {
            downloadedUrls.add(url);
            downloadCounts.merge(targetPath, 1, Integer::sum);
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            Files.createDirectories(targetPath.getParent());
            String content = (corruptContent != null) ? corruptContent : "content-for-" + url;
            Files.writeString(targetPath, content);
            return content.length();
        }

        int downloadCountFor(Path path) {
            return downloadCounts.getOrDefault(path, 0);
        }
    }

    /**
     * Fake verifier that auto-validates any existing file unless
     * explicitly marked invalid via {@link #markInvalid(Path)}.
     */
    private static class FakeVerifier implements ChecksumVerifier {
        final Map<Path, Boolean> overrides = new HashMap<>();

        @Override
        public boolean verify(Path path, String expectedSha1) {
            if (overrides.containsKey(path)) {
                return overrides.get(path);
            }
            return Files.isRegularFile(path);
        }

        void markValid(Path path) {
            overrides.put(path, true);
        }

        void markInvalid(Path path) {
            overrides.put(path, false);
        }
    }

    /** Fake asset index service returning a fixed content. */
    private static class FakeAssetIndexService implements AssetIndexService {
        AssetIndexContent content;

        @Override
        public AssetIndexContent fetchIndex(AssetIndex assetIndex) {
            return content;
        }
    }

    /** Progress tracker that records all callbacks. */
    private static class TrackingProgress implements InstallationProgress {
        int startCount;
        int totalTasksFromStart;
        long totalBytesFromStart;
        final List<DownloadTask> fileStarts = new ArrayList<>();
        final List<DownloadResult> fileCompletes = new ArrayList<>();
        InstallationResult finalResult;

        @Override
        public void onStart(int totalTasks, long totalBytes) {
            startCount++;
            this.totalTasksFromStart = totalTasks;
            this.totalBytesFromStart = totalBytes;
        }

        @Override
        public void onFileStart(int taskIndex, DownloadTask task) {
            fileStarts.add(task);
        }

        @Override
        public void onFileComplete(int taskIndex, DownloadResult result) {
            fileCompletes.add(result);
        }

        @Override
        public void onComplete(InstallationResult result) {
            this.finalResult = result;
        }
    }

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------

    private GameDirectory gameDir(@TempDir Path dir) {
        return new GameDirectory(dir);
    }

    private VersionMetadata buildTestMetadata() {
        DownloadInfo clientDl = new DownloadInfo(
                "https://example.com/client.jar", "client_sha1", 1000, null);

        DownloadInfo lib1Artifact = new DownloadInfo(
                "https://example.com/lib1.jar", "lib1_sha1", 500,
                "com/example/lib1/1.0/lib1-1.0.jar");

        DownloadInfo lwjglArtifact = new DownloadInfo(
                "https://example.com/lwjgl.jar", "lwjgl_sha1", 800,
                "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar");

        DownloadInfo lwjglNativeWin = new DownloadInfo(
                "https://example.com/lwjgl-natives-windows.jar",
                "natives_win_sha1", 300,
                "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar");

        Map<String, DownloadInfo> classifiers = new HashMap<>();
        classifiers.put("natives-windows", lwjglNativeWin);

        Map<String, String> natives = new HashMap<>();
        natives.put("windows", "natives-windows");

        Library lib1 = new Library("com.example:lib1:1.0", lib1Artifact, Map.of(), Map.of());
        Library lwjgl = new Library("org.lwjgl:lwjgl:3.3.1", lwjglArtifact, classifiers, natives);

        AssetIndex assetIndex = new AssetIndex(
                "21", "index_sha1", 1500, 500000, "https://example.com/index.json");

        return new VersionMetadata(
                "1.21", "release", "net.minecraft.client.main.Main",
                "21", assetIndex, null, clientDl,
                List.of(lib1, lwjgl),
                List.of("--username", "${auth_name}"),
                List.of("-Xmx2G"),
                null);
    }

    private MinecraftVersion buildTestVersion() {
        return new MinecraftVersion(
                "1.21", StandardVersionType.RELEASE,
                "2024-06-13T10:30:00+00:00",
                "https://example.com/1.21.json");
    }

    // ----------------------------------------------------------------
    //  Tests: task list construction
    // ----------------------------------------------------------------

    @Test
    @DisplayName("buildDownloadTasks creates tasks for client, libraries, natives, and assets")
    void buildsAllTaskCategories(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "minecraft/textures/stone.png",
                new AssetObject("abcdef1234567890", 4096),
                "minecraft/sounds/music.ogg",
                new AssetObject("xyz789abcdef0123", 9999)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        assertEquals(MinecraftInstaller.CATEGORY_CLIENT,
                tasks.get(0).category());
        assertEquals(MinecraftInstaller.CATEGORY_LIBRARY,
                tasks.get(1).category());
        assertEquals(MinecraftInstaller.CATEGORY_LIBRARY,
                tasks.get(2).category());
        assertEquals(MinecraftInstaller.CATEGORY_NATIVE,
                tasks.get(3).category());
        assertEquals(MinecraftInstaller.CATEGORY_ASSET_INDEX,
                tasks.get(4).category());
        assertEquals(MinecraftInstaller.CATEGORY_ASSET,
                tasks.get(5).category());
        assertEquals(MinecraftInstaller.CATEGORY_ASSET,
                tasks.get(6).category());

        assertEquals(7, tasks.size());
    }

    @Test
    @DisplayName("asset URLs use correct Mojang format (prefix/hash)")
    void assetUrlFormat(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "test.png", new AssetObject("abcdef1234567890abcd", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        DownloadTask assetTask = tasks.stream()
                .filter(t -> t.category().equals(MinecraftInstaller.CATEGORY_ASSET))
                .findFirst().orElseThrow();

        assertTrue(assetTask.url().contains("/ab/abcdef1234567890abcd"));
        assertEquals("abcdef1234567890abcd", assetTask.expectedSha1().orElse(""));
    }

    @Test
    @DisplayName("asset URLs use resources.download.minecraft.net")
    void assetUrlDomain(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "test.png", new AssetObject("abcdef1234567890abcd", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        DownloadTask assetTask = tasks.stream()
                .filter(t -> t.category().equals(MinecraftInstaller.CATEGORY_ASSET))
                .findFirst().orElseThrow();

        assertTrue(assetTask.url().startsWith("https://resources.download.minecraft.net/"),
                "Asset URL should use resources.download.minecraft.net, got: " + assetTask.url());
    }

    @Test
    @DisplayName("client JAR target path includes version directory")
    void clientJarPath(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        DownloadTask clientTask = tasks.get(0);
        assertTrue(clientTask.targetPath().endsWith("1.21.jar"));
        assertTrue(clientTask.targetPath().toString().contains("versions"));
        assertTrue(clientTask.targetPath().toString().contains("1.21"));
    }

    @Test
    @DisplayName("libraries are stored in shared libraries/ directory")
    void libraryPathsAreShared(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        List<DownloadTask> libTasks = tasks.stream()
                .filter(t -> t.category().equals(MinecraftInstaller.CATEGORY_LIBRARY))
                .toList();

        assertEquals(2, libTasks.size());
        for (DownloadTask t : libTasks) {
            assertTrue(t.targetPath().toString().contains("libraries"),
                    "Library should be in libraries/: " + t.targetPath());
        }
    }

    @Test
    @DisplayName("assets are stored in shared assets/objects/ directory (hash-addressed)")
    void assetPathsAreHashAddressed(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890abcd", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                new FakeDownloader(), new FakeVerifier(), assetService);

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);

        DownloadTask assetTask = tasks.stream()
                .filter(t -> t.category().equals(MinecraftInstaller.CATEGORY_ASSET))
                .findFirst().orElseThrow();

        String pathStr = assetTask.targetPath().toString();
        assertTrue(pathStr.contains("assets"), "Should be in assets/: " + pathStr);
        assertTrue(pathStr.contains("objects"), "Should be in objects/: " + pathStr);
        assertTrue(pathStr.contains("ab"), "Should have hash prefix dir: " + pathStr);
        assertTrue(pathStr.endsWith("abcdef1234567890abcd"));
    }

    // ----------------------------------------------------------------
    //  Tests: installation with hash verification
    // ----------------------------------------------------------------

    @Test
    @DisplayName("install downloads all files when none exist locally and verifies hashes")
    void downloadsAllFiles(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService);
        TrackingProgress progress = new TrackingProgress();

        InstallationResult result = installer.install(
                buildTestVersion(), buildTestMetadata(), gameDir, progress);

        assertEquals(6, result.totalTasks());
        assertEquals(6, result.downloaded());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        assertTrue(result.isSuccess());

        assertEquals(6, downloader.downloadedUrls.size());
        assertEquals(1, progress.startCount);
        assertEquals(6, progress.fileStarts.size());
        assertEquals(6, progress.fileCompletes.size());
        assertEquals(result, progress.finalResult);

        for (DownloadResult dr : progress.fileCompletes) {
            assertEquals(1, dr.attempts());
        }
    }

    @Test
    @DisplayName("install skips files that exist locally with valid hash")
    void skipsValidLocalFiles(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService);

        VersionMetadata meta = buildTestMetadata();
        List<DownloadTask> tasks = installer.buildDownloadTasks(meta, gameDir);

        for (DownloadTask task : tasks) {
            Files.createDirectories(task.targetPath().getParent());
            Files.writeString(task.targetPath(), "existing-content");
            verifier.markValid(task.targetPath());
        }

        TrackingProgress progress = new TrackingProgress();
        InstallationResult result = installer.install(
                buildTestVersion(), meta, gameDir, progress);

        assertEquals(6, result.totalTasks());
        assertEquals(0, result.downloaded());
        assertEquals(6, result.skipped());
        assertEquals(0, result.failed());
        assertTrue(downloader.downloadedUrls.isEmpty());
    }

    @Test
    @DisplayName("install deletes corrupt local file and re-downloads when hash mismatches")
    void reDownloadsMismatchedFiles(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        FakeVerifier contentVerifier = new FakeVerifier() {
            @Override
            public boolean verify(Path path, String expectedSha1) {
                if (overrides.containsKey(path)) {
                    return overrides.get(path);
                }
                try {
                    if (Files.isRegularFile(path)) {
                        String content = Files.readString(path);
                        if (content.equals("stale-content")) {
                            return false;
                        }
                    }
                } catch (IOException e) {
                    return false;
                }
                return Files.isRegularFile(path);
            }
        };

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, contentVerifier, assetService);

        VersionMetadata meta = buildTestMetadata();
        List<DownloadTask> tasks = installer.buildDownloadTasks(meta, gameDir);

        for (DownloadTask task : tasks) {
            Files.createDirectories(task.targetPath().getParent());
            Files.writeString(task.targetPath(), "stale-content");
        }

        TrackingProgress progress = new TrackingProgress();
        InstallationResult result = installer.install(
                buildTestVersion(), meta, gameDir, progress);

        assertEquals(6, result.totalTasks());
        assertEquals(6, result.downloaded());
        assertEquals(0, result.skipped());

        for (DownloadTask task : tasks) {
            assertEquals(1, downloader.downloadCountFor(task.targetPath()));
        }

        for (DownloadTask task : tasks) {
            String content = Files.readString(task.targetPath());
            assertTrue(content.startsWith("content-for-"));
            assertFalse(content.contains("stale-content"));
        }
    }

    @Test
    @DisplayName("install handles download failures gracefully after retries")
    void handlesDownloadFailures(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        downloader.exceptionToThrow = new IOException("Network error");

        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService, 3);

        InstallationResult result = installer.install(
                buildTestVersion(), buildTestMetadata(), gameDir,
                InstallationProgress.NONE);

        assertEquals(6, result.totalTasks());
        assertEquals(0, result.downloaded());
        assertEquals(0, result.skipped());
        assertEquals(6, result.failed());
        assertTrue(result.hasFailures());
        assertEquals(6, result.failedTasks().size());

        for (DownloadResult dr : result.failedTasks()) {
            assertEquals(3, dr.attempts());
        }
    }

    @Test
    @DisplayName("install retries on hash mismatch, then succeeds on correct download")
    void retriesOnHashMismatchThenSucceeds(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        VersionMetadata meta = buildTestMetadata();

        AtomicInteger globalCall = new AtomicInteger();
        FakeDownloader downloader = new FakeDownloader() {
            @Override
            public long download(String url, Path targetPath) throws IOException {
                int call = globalCall.incrementAndGet();
                if (call % 2 == 1) {
                    downloadedUrls.add(url);
                    downloadCounts.merge(targetPath, 1, Integer::sum);
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, "CORRUPT-" + url);
                    return "CORRUPT-".length() + url.length();
                }
                return super.download(url, targetPath);
            }
        };

        FakeVerifier strictVerifier = new FakeVerifier() {
            @Override
            public boolean verify(Path path, String expectedSha1) {
                if (overrides.containsKey(path)) {
                    return overrides.get(path);
                }
                try {
                    if (Files.isRegularFile(path)) {
                        String content = Files.readString(path);
                        if (content.startsWith("CORRUPT-")) {
                            return false;
                        }
                    }
                } catch (IOException e) {
                    return false;
                }
                return Files.isRegularFile(path);
            }
        };

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, strictVerifier, assetService, 3);

        InstallationResult result = installer.install(
                buildTestVersion(), meta, gameDir, InstallationProgress.NONE);

        assertEquals(6, result.totalTasks());
        assertEquals(6, result.downloaded());
        assertEquals(0, result.failed());

        List<DownloadTask> tasks = installer.buildDownloadTasks(meta, gameDir);
        for (DownloadTask task : tasks) {
            assertEquals(2, downloader.downloadCountFor(task.targetPath()));
        }

        for (DownloadTask task : tasks) {
            String content = Files.readString(task.targetPath());
            assertTrue(content.startsWith("content-for-"));
        }
    }

    @Test
    @DisplayName("install fails after max retries on persistent hash mismatch")
    void failsAfterMaxRetriesOnHashMismatch(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        downloader.corruptContent = "always-corrupt";

        FakeVerifier alwaysReject = new FakeVerifier() {
            @Override
            public boolean verify(Path path, String expectedSha1) {
                return false;
            }
        };

        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, alwaysReject, assetService, 3);

        InstallationResult result = installer.install(
                buildTestVersion(), buildTestMetadata(), gameDir,
                InstallationProgress.NONE);

        assertEquals(6, result.totalTasks());
        assertEquals(0, result.downloaded());
        assertEquals(0, result.skipped());
        assertEquals(6, result.failed());

        for (DownloadResult dr : result.failedTasks()) {
            assertEquals(3, dr.attempts());
            assertTrue(dr.errorMessage().contains("SHA-1 mismatch"));
        }

        List<DownloadTask> tasks = installer.buildDownloadTasks(buildTestMetadata(), gameDir);
        for (DownloadTask task : tasks) {
            assertFalse(Files.exists(task.targetPath()),
                    "Corrupt file should be deleted: " + task.targetPath());
        }
    }

    @Test
    @DisplayName("install handles mixed skip/download/fail correctly")
    void mixedResults(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(
                "stone.png", new AssetObject("abcdef1234567890", 4096)
        ), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService);

        VersionMetadata meta = buildTestMetadata();
        List<DownloadTask> tasks = installer.buildDownloadTasks(meta, gameDir);

        Files.createDirectories(tasks.get(0).targetPath().getParent());
        Files.writeString(tasks.get(0).targetPath(), "valid");
        verifier.markValid(tasks.get(0).targetPath());

        FakeDownloader partialDownloader = new FakeDownloader() {
            @Override
            public long download(String url, Path targetPath) throws IOException {
                if (targetPath.equals(tasks.get(1).targetPath())) {
                    throw new IOException("Permanent failure for lib1");
                }
                return super.download(url, targetPath);
            }
        };

        MinecraftInstaller partialInstaller = new MinecraftInstaller(
                partialDownloader, verifier, assetService, 2);

        InstallationResult result = partialInstaller.install(
                buildTestVersion(), meta, gameDir, InstallationProgress.NONE);

        assertEquals(6, result.totalTasks());
        assertEquals(1, result.skipped());
        assertEquals(1, result.failed());
        assertEquals(4, result.downloaded());

        DownloadResult failedResult = result.failedTasks().get(0);
        assertEquals(2, failedResult.attempts());
        assertEquals(tasks.get(1).targetPath(), failedResult.task().targetPath());
    }

    @Test
    @DisplayName("files without expected SHA-1 are accepted without verification")
    void filesWithoutShaAccepted(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(), false);

        DownloadInfo noHashLib = new DownloadInfo(
                "https://example.com/nohash.jar", null, 100,
                "com/example/nohash/1.0/nohash-1.0.jar");
        Library lib = new Library("com.example:nohash:1.0", noHashLib, Map.of(), Map.of());

        VersionMetadata meta = new VersionMetadata(
                "test", "release", "net.minecraft.client.main.Main",
                null, null, null, null,
                List.of(lib), List.of(), List.of(), null);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService);

        InstallationResult result = installer.install(
                buildTestVersion(), meta, gameDir, InstallationProgress.NONE);

        assertEquals(1, result.totalTasks());
        assertEquals(1, result.downloaded());
        assertEquals(0, result.failed());
    }

    @Test
    @DisplayName("shared libraries are not re-downloaded when installing a second version")
    void sharedLibrariesNotRedownloaded(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = gameDir(dir);
        FakeDownloader downloader = new FakeDownloader();
        FakeVerifier verifier = new FakeVerifier();
        FakeAssetIndexService assetService = new FakeAssetIndexService();
        assetService.content = new AssetIndexContent(Map.of(), false);

        MinecraftInstaller installer = new MinecraftInstaller(
                downloader, verifier, assetService);

        VersionMetadata meta = buildTestMetadata();

        // First install — all files downloaded
        installer.install(buildTestVersion(), meta, gameDir, InstallationProgress.NONE);
        int firstDownloadCount = downloader.downloadedUrls.size();
        assertEquals(5, firstDownloadCount); // client + 2 libs + 1 native + asset index

        // Second install of same version — all files should be skipped
        downloader.downloadedUrls.clear();
        InstallationResult result2 = installer.install(
                buildTestVersion(), meta, gameDir, InstallationProgress.NONE);

        assertEquals(5, result2.skipped());
        assertEquals(0, result2.downloaded());
        assertTrue(downloader.downloadedUrls.isEmpty(),
                "No re-downloads for already-installed shared files");
    }
}
