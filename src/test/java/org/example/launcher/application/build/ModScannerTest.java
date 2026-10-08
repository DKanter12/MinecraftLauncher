package org.example.launcher.application.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ModScanner")
class ModScannerTest {

    @Test
    @DisplayName("counts only jars in mods")
    void countsJars(@TempDir Path dir) throws IOException {
        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("sodium.jar"), "fake");
        Files.writeString(mods.resolve("lithium.jar"), "fake");
        Files.writeString(mods.resolve("readme.txt"), "fake");

        assertEquals(2, ModScanner.countMods(dir, false));
    }

    @Test
    @DisplayName("missing mods dir is -1 for vanilla and 0 for modded")
    void missingDir(@TempDir Path dir) {
        assertEquals(-1, ModScanner.countMods(dir, true));
        assertEquals(0, ModScanner.countMods(dir, false));
    }

    @Test
    @DisplayName("empty mods dir is zero")
    void emptyDir(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("mods"));

        assertEquals(0, ModScanner.countMods(dir, false));
    }
}
