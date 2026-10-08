package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

@DisplayName("MinecraftVersionStorage")
class MinecraftVersionStorageTest {

    @Test
    @DisplayName("paths follow the shared versions layout")
    void layout(@TempDir Path dir) {
        var storage = new MinecraftVersionStorage(new GameDirectory(dir));

        Path json = storage.versionJson("1.21.11");
        assertTrue(json.endsWith(Path.of("versions", "1.21.11", "1.21.11.json")));
        assertTrue(storage.clientJar("1.21.11").endsWith(
                Path.of("versions", "1.21.11", "1.21.11.jar")));
        assertTrue(storage.versionDir("1.21.11").endsWith(
                Path.of("versions", "1.21.11")));
        assertTrue(storage.librariesDir().endsWith(Path.of("libraries")));
        assertTrue(storage.assetsDir().endsWith(Path.of("assets")));
        assertTrue(storage.nativesDir("1.21.11").endsWith(
                Path.of("natives", "1.21.11")));
    }
}
