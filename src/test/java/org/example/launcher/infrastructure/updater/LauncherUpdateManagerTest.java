package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

import org.example.launcher.infrastructure.http.UrlFetcher;

@DisplayName("LauncherUpdateManager")
class LauncherUpdateManagerTest {

    private static String manifest(String version) {
        return """
                {"version": "%s", "notes": "notes",
                 "package": {"url": "https://example.com/l.zip",
                             "sha256": "%s", "size": 10}}
                """.formatted(version, "ab".repeat(32));
    }

    private LauncherUpdateManager manager(Path manifestFile, Path storage) {
        return new LauncherUpdateManager(
                new LauncherVersionProvider(new UrlFetcher(),
                        manifestFile.toUri().toString()),
                new LauncherVersionChecker(), storage, new Gson());
    }

    @Test
    @DisplayName("newer version is AVAILABLE, same is UP_TO_DATE")
    void statuses(@TempDir Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Path storage = dir.resolve("storage");

        Files.writeString(manifest, manifest("9.9.9"));
        assertEquals(UpdateService.Status.AVAILABLE,
                manager(manifest, storage).checkForUpdates().status());

        Files.writeString(manifest, manifest(AppVersion.current()));
        assertEquals(UpdateService.Status.UP_TO_DATE,
                manager(manifest, storage).checkForUpdates().status());
    }

    @Test
    @DisplayName("broken manifest is FAILED, dead server is NO_INTERNET")
    void failures(@TempDir Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Path storage = dir.resolve("storage");

        Files.writeString(manifest, "{ broken");
        assertEquals(UpdateService.Status.FAILED,
                manager(manifest, storage).checkForUpdates().status());

        var offline = new LauncherUpdateManager(
                new LauncherVersionProvider(new UrlFetcher(),
                        "http://127.0.0.1:1/manifest.json"),
                new LauncherVersionChecker(), storage, new Gson());
        assertEquals(UpdateService.Status.NO_INTERNET,
                offline.checkForUpdates().status());
    }

    @Test
    @DisplayName("no staged update without a pending marker")
    void noStaged(@TempDir Path dir) {
        Path manifest = dir.resolve("manifest.json");

        assertTrue(manager(manifest, dir.resolve("storage"))
                .stagedUpdate().isEmpty());
    }

    @Test
    @DisplayName("restart without a plan means manual copy")
    void restartManual(@TempDir Path dir) throws IOException {
        var restart = new LauncherRestartManager();
        var service = new UpdateService(new UrlFetcher(),
                dir.resolve("manifest.json").toUri().toString(), dir);

        assertTrue(restart.restartWithUpdate(service,
                dir.resolve("updates").resolve("9.9.9"), "9.9.9",
                ProcessHandle.current().pid()).isEmpty());
    }
}
