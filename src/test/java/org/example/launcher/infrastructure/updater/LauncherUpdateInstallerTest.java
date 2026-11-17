package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LauncherUpdateInstaller")
class LauncherUpdateInstallerTest {

    private static Path writeZip(Path dir, String name, String content)
            throws IOException {
        Path zip = dir.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("app.jar"));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    private static LauncherUpdate update(String hash) {
        return new LauncherUpdate("9.9.9", "notes", "https://example.com/l.zip",
                hash, 10);
    }

    @Test
    @DisplayName("install stages files and writes the pending marker")
    void stages(@TempDir Path dir) throws IOException {
        Path storage = dir.resolve("storage");
        var installer = new LauncherUpdateInstaller(storage);
        Path zip = writeZip(dir, "l.zip", "jar-bytes");

        Path stageDir = installer.install(zip,
                update("00"));

        assertEquals("jar-bytes",
                Files.readString(stageDir.resolve("app.jar")));
        assertFalse(Files.exists(zip), "package is cleaned after staging");
        String pending = Files.readString(
                storage.resolve("updates").resolve("pending.json"));
        assertTrue(pending.contains("9.9.9"));
    }

    @Test
    @DisplayName("downloader reports failure without throwing")
    void downloaderFailure(@TempDir Path dir) {
        var downloader = new LauncherUpdateDownloader(
                new org.example.launcher.infrastructure.http.UrlFetcher());
        var version = new org.example.launcher.domain.model.LauncherVersion(
                "1.2.0", null, "http://127.0.0.1:1/l.zip", 10,
                "ab".repeat(32), "notes");

        UpdateDownloadResult result = downloader.download(version,
                dir.resolve("l.zip"), null);

        assertFalse(result.success());
        assertTrue(result.error() != null && !result.error().isBlank());
        assertFalse(Files.exists(dir.resolve("l.zip")));
    }
}
