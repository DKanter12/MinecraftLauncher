package org.example.launcher.infrastructure.updater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.infrastructure.http.UrlFetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.example.launcher.infrastructure.updater.AppVersion;
import org.example.launcher.infrastructure.updater.LauncherUpdate;
import org.example.launcher.infrastructure.updater.UpdateApplier;
import org.example.launcher.infrastructure.updater.UpdateService;

class UpdateServiceTest {

    @TempDir
    Path tempDir;

    private Path storageRoot;
    private UpdateService service;
    private Path manifestFile;

    @BeforeEach
    void setUp() {
        storageRoot = tempDir.resolve("storage");
        manifestFile = tempDir.resolve("manifest.json");
        service = new UpdateService(
                new UrlFetcher(),
                manifestFile.toUri().toString(), storageRoot);
    }

    @Test
    void checkUpToDate() throws IOException {
        writeManifest(AppVersion.current(), packageUrl("none"), "00");

        UpdateService.CheckResult result = service.check();

        assertEquals(UpdateService.Status.UP_TO_DATE, result.status());
    }

    @Test
    void checkAvailable() throws IOException {
        writeManifest("9.9.9", packageUrl("none"), "00");

        UpdateService.CheckResult result = service.check();

        assertEquals(UpdateService.Status.AVAILABLE, result.status());
        assertEquals("9.9.9", result.update().version());
    }

    @Test
    void checkNoInternet() {
        UpdateService offline = new UpdateService(
                new UrlFetcher(),
                "http://127.0.0.1:1/manifest.json", storageRoot);

        UpdateService.CheckResult result = offline.check();

        assertEquals(UpdateService.Status.NO_INTERNET, result.status());
    }

    @Test
    void checkBadManifest() throws IOException {
        Files.writeString(manifestFile, "{ broken");

        UpdateService.CheckResult result = service.check();

        assertEquals(UpdateService.Status.FAILED, result.status());
    }

    @Test
    void downloadAndStage() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("lib/app.jar", "jar-bytes");
        entries.put("version.txt", "old");
        Path zip = writeZip(entries);
        writeManifest("9.9.9", zip.toUri().toString(), sha256(zip));
        LauncherUpdate update = service.check().update();

        Path downloaded = service.download(update, null);
        Path stageDir = service.stage(downloaded, update);

        assertEquals("jar-bytes",
                Files.readString(stageDir.resolve("lib/app.jar")));
        assertFalse(Files.exists(downloaded),
                "package temp file is cleaned after staging");
        Optional<UpdateService.PendingUpdate> staged = service.stagedUpdate();
        assertTrue(staged.isPresent());
        assertEquals("9.9.9", staged.get().version());
        assertEquals(stageDir, staged.get().stageDir());
    }

    @Test
    void downloadRejectsBadChecksum() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("lib/app.jar", "jar-bytes");
        Path zip = writeZip(entries);
        writeManifest("9.9.9", zip.toUri().toString(), "0".repeat(64));
        LauncherUpdate update = service.check().update();

        assertThrows(IOException.class, () -> service.download(update, null));
    }

    @Test
    void stageRejectsZipSlip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("../evil.txt", "evil");
        Path zip = writeZip(entries);
        writeManifest("9.9.9", zip.toUri().toString(), sha256(zip));
        LauncherUpdate update = service.check().update();
        Path downloaded = service.download(update, null);

        try {
            assertThrows(IOException.class,
                    () -> service.stage(downloaded, update));
        } finally {
            Files.deleteIfExists(downloaded);
        }
    }

    @Test
    void clearStagedExceptKeepsOne() throws IOException {
        Path keep = storageRoot.resolve("updates").resolve("9.9.9");
        Path drop = storageRoot.resolve("updates").resolve("9.9.8");
        Files.createDirectories(keep);
        Files.createDirectories(drop);

        service.clearStagedExcept(keep);

        assertTrue(Files.isDirectory(keep));
        assertFalse(Files.exists(drop));
    }

    private void writeManifest(String version, String packageUrl, String sha)
            throws IOException {
        String json = "{\"version\": \"" + version + "\","
                + "\"notes\": \"notes\","
                + "\"package\": {\"url\": \"" + packageUrl + "\","
                + "\"sha256\": \"" + sha + "\", \"size\": 1}}";
        Files.writeString(manifestFile, json);
    }

    private String packageUrl(String name) {
        return tempDir.resolve(name + ".zip").toUri().toString();
    }

    private Path writeZip(Map<String, String> entries) throws IOException {
        Path zip = Files.createTempFile(tempDir, "pack-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(zip))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}

class UpdateApplierTest {

    @TempDir
    Path tempDir;

    @Test
    void planEmptyForUnknownLayout() {
        assertTrue(UpdateApplier
                .plan(tempDir.resolve("nope"), "9.9.9").isEmpty());
    }

    @Test
    void writeUpdaterRendersScript() throws IOException {
        Path storage = tempDir.resolve("storage");
        UpdateService service = new UpdateService(
                new UrlFetcher(),
                "file:///none", storage);
        Path stage = storage.resolve("updates").resolve("9.9.9");
        Files.createDirectories(stage.resolve("lib"));
        Files.writeString(stage.resolve("lib").resolve("app.jar"), "new");
        Path appHome = tempDir.resolve("app");
        Files.createDirectories(appHome.resolve("bin"));
        Files.writeString(appHome.resolve("bin").resolve("run.bat"), "run");

        UpdateApplier.ApplyPlan plan = new UpdateApplier.ApplyPlan(
                stage, appHome, "9.9.9", java.util.List.of("java", "-jar",
                        appHome.resolve("app.jar").toString()));
        Path updater = UpdateApplier.writeUpdater(service, plan);

        assertTrue(Files.isRegularFile(updater));
        String script = Files.readString(updater);
        assertTrue(script.contains("tasklist"));
        assertTrue(script.contains("xcopy"));
        assertTrue(script.contains("version.txt"));
        assertTrue(script.contains("9.9.9"));
        assertTrue(script.contains("app.jar"));
        // other staged versions are cleared, this stage survives
        assertTrue(Files.isDirectory(stage));
    }
}

