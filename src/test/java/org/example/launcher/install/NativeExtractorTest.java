package org.example.launcher.install;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("NativeExtractor")
class NativeExtractorTest {

    @Test
    @DisplayName("extracts native files from JAR to natives directory")
    void extractsNatives(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = new GameDirectory(dir);

        String nativePath = "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-" 
                + org.example.launcher.util.OsDetector.mojangName() + ".jar";

        Path nativeJar = gameDir.library(nativePath);
        Files.createDirectories(nativeJar.getParent());
        createNativeJar(nativeJar);

        DownloadInfo nativeDl = new DownloadInfo(
                "https://example.com/" + nativePath, "sha", 100, nativePath);

        Library lib = new Library(
                "org.lwjgl:lwjgl:3.3.1",
                null,
                java.util.Map.of(
                        "natives-" + org.example.launcher.util.OsDetector.mojangName(), nativeDl),
                java.util.Map.of(
                        org.example.launcher.util.OsDetector.mojangName(),
                        "natives-" + org.example.launcher.util.OsDetector.mojangName()));

        VersionMetadata meta = new VersionMetadata(
                "1.21", "release", "Main", "21", null, null,
                null, java.util.List.of(lib),
                java.util.List.of(), java.util.List.of(), null);

        int count = NativeExtractor.extractNatives(meta, gameDir);

        assertTrue(count > 0, "Should extract at least one file");
        Path nativesDir = gameDir.nativeDir("1.21");
        assertTrue(Files.isRegularFile(nativesDir.resolve("liblwjlgl.dll")),
                "Native file should be extracted");
        assertTrue(Files.notExists(nativesDir.resolve("META-INF/MANIFEST.MF")),
                "META-INF should be skipped");
    }

    @Test
    @DisplayName("skips non-existent native JARs gracefully")
    void skipsMissingNatives(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = new GameDirectory(dir);

        DownloadInfo nativeDl = new DownloadInfo(
                "https://example.com/missing.jar", null, 0, "missing.jar");

        Library lib = new Library(
                "org.lwjgl:lwjgl:3.3.1",
                null,
                java.util.Map.of("natives-" + org.example.launcher.util.OsDetector.mojangName(), nativeDl),
                java.util.Map.of(org.example.launcher.util.OsDetector.mojangName(),
                        "natives-" + org.example.launcher.util.OsDetector.mojangName()));

        VersionMetadata meta = new VersionMetadata(
                "1.21", "release", "Main", "21", null, null,
                null, java.util.List.of(lib),
                java.util.List.of(), java.util.List.of(), null);

        int count = NativeExtractor.extractNatives(meta, gameDir);
        assertTrue(count == 0, "Should extract nothing from missing JAR");
    }

    @Test
    @DisplayName("skips libraries without natives for current OS")
    void skipsLibrariesWithoutNatives(@TempDir Path dir) throws IOException {
        GameDirectory gameDir = new GameDirectory(dir);

        Library lib = new Library(
                "com.mojang:logging:1.1.1",
                new DownloadInfo("https://example.com/log.jar", "sha", 100,
                        "com/mojang/logging/1.1.1/logging-1.1.1.jar"),
                java.util.Map.of(), java.util.Map.of());

        VersionMetadata meta = new VersionMetadata(
                "1.21", "release", "Main", "21", null, null,
                null, java.util.List.of(lib),
                java.util.List.of(), java.util.List.of(), null);

        int count = NativeExtractor.extractNatives(meta, gameDir);
        assertTrue(count == 0, "Should extract nothing from non-native library");
    }

    private void createNativeJar(Path path) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(path))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("liblwjlgl.dll"));
            jos.write("native".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("liblwjlgl.so"));
            jos.write("native".getBytes());
            jos.closeEntry();
        }
    }
}
