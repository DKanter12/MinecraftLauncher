package org.example.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GameDirectory")
class GameDirectoryTest {

    @Test
    @DisplayName("root path is preserved")
    void rootPreserved(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        assertEquals(dir, gd.root());
    }

    @Test
    @DisplayName("version paths follow versions/<id>/<id>.jar layout")
    void versionPaths(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        assertTrue(gd.clientJar("1.21").endsWith("versions" + sep() + "1.21" + sep() + "1.21.jar"));
        assertTrue(gd.versionMetadata("1.21").endsWith("versions" + sep() + "1.21" + sep() + "1.21.json"));
    }

    @Test
    @DisplayName("library paths resolve Maven-style relative path")
    void libraryPaths(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        Path lib = gd.library("com/mojang/logging/1.1.1/logging-1.1.1.jar");
        assertTrue(lib.toString().contains("libraries"));
        assertTrue(lib.toString().contains("logging-1.1.1.jar"));
    }

    @Test
    @DisplayName("asset paths are hash-addressed under assets/objects/")
    void assetPaths(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        Path asset = gd.assetObject("ab", "abcdef1234567890");
        assertTrue(asset.toString().contains("assets"));
        assertTrue(asset.toString().contains("objects"));
        assertTrue(asset.toString().contains("ab"));
        assertTrue(asset.toString().endsWith("abcdef1234567890"));
    }

    @Test
    @DisplayName("asset index file is under assets/indexes/")
    void assetIndexFile(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        Path idx = gd.assetIndexFile("21");
        assertTrue(idx.toString().contains("assets"));
        assertTrue(idx.toString().contains("indexes"));
        assertTrue(idx.toString().endsWith("21.json"));
    }

    @Test
    @DisplayName("natives directory is version-specific")
    void nativesDir(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        Path nat = gd.nativeDir("1.21");
        assertTrue(nat.toString().contains("natives"));
        assertTrue(nat.toString().endsWith("1.21"));
    }

    @Test
    @DisplayName("profiles file is at root")
    void profilesFile(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        assertTrue(gd.profilesFile().endsWith("launcher_profiles.json"));
    }

    @Test
    @DisplayName("java-runtimes directory is under root")
    void javaRuntimesDir(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        assertTrue(gd.javaRuntimesDir().toString().contains("java-runtimes"));
    }

    @Test
    @DisplayName("javaRuntimeDir resolves a component under java-runtimes/")
    void javaRuntimeDir(@TempDir Path dir) {
        GameDirectory gd = new GameDirectory(dir);
        Path rt = gd.javaRuntimeDir("java-runtime-gamma");
        assertTrue(rt.toString().contains("java-runtimes"));
        assertTrue(rt.toString().endsWith("java-runtime-gamma"));
    }

    @Test
    @DisplayName("createDirectories creates all expected directories")
    void createDirectories(@TempDir Path dir) throws IOException {
        GameDirectory gd = new GameDirectory(dir);
        gd.createDirectories();

        assertTrue(Files.isDirectory(gd.versionsDir()));
        assertTrue(Files.isDirectory(gd.librariesDir()));
        assertTrue(Files.isDirectory(gd.nativesDir()));
        assertTrue(Files.isDirectory(gd.assetIndexesDir()));
        assertTrue(Files.isDirectory(gd.assetObjectsDir()));
        assertTrue(Files.isDirectory(gd.javaRuntimesDir()));
    }

    private String sep() {
        return Path.of("x").getFileSystem().getSeparator();
    }
}
