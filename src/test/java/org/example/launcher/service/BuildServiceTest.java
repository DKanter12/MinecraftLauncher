package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BuildService: mods/configs builds of an instance")
class BuildServiceTest {

    private final BuildService service = new BuildService();

    /** Fills the game dir with a mods/config layout to snapshot. */
    private void pack(Path gameDir, String... modFiles) throws IOException {
        Files.createDirectories(gameDir.resolve("mods"));
        Files.createDirectories(gameDir.resolve("config"));
        for (String mod : modFiles) {
            Files.writeString(gameDir.resolve("mods").resolve(mod), "jar");
        }
        Files.writeString(gameDir.resolve("config").resolve("example.toml"),
                "setting=1");
    }

    @Test
    @DisplayName("saveBuild: mods only — name sanitized, config not copied")
    void saveBuildModsOnly(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "sodium.jar", "lithium.jar");

        BuildService.BuildInfo saved =
                service.saveBuild(gameDir, "My Performance Pack!", true, false);

        assertEquals("My Performance Pack!", saved.name());
        assertTrue(saved.mods());
        assertFalse(saved.configs());
        assertEquals("mods", saved.description());

        Path buildDir = gameDir.resolve("builds").resolve("My Performance Pack!");
        assertEquals(2,
                Files.list(buildDir.resolve("mods")).count());
        assertFalse(Files.exists(buildDir.resolve("config")));
    }

    @Test
    @DisplayName("saveBuild: mods + configs — both folders snapshotted")
    void saveBuildModsAndConfigs(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "a.jar");

        BuildService.BuildInfo saved =
                service.saveBuild(gameDir, "Full Setup", true, true);

        assertTrue(saved.mods());
        assertTrue(saved.configs());
        assertEquals("mods + configs", saved.description());
        assertTrue(Files.isRegularFile(gameDir.resolve("builds")
                .resolve("Full Setup").resolve("mods").resolve("a.jar")));
        assertTrue(Files.isRegularFile(gameDir.resolve("builds")
                .resolve("Full Setup").resolve("config").resolve("example.toml")));
    }

    @Test
    @DisplayName("saveBuild: rejects duplicates, blank names, nothing chosen, missing sources")
    void saveBuildValidatesInput(@TempDir Path gameDir,
                                 @TempDir Path emptyDir) throws IOException {
        pack(gameDir, "a.jar");
        service.saveBuild(gameDir, "Pack", true, false);

        assertThrows(IOException.class,
                () -> service.saveBuild(gameDir, "Pack", true, false));
        assertThrows(IOException.class,
                () -> service.saveBuild(gameDir, "///", true, false));
        assertThrows(IOException.class,
                () -> service.saveBuild(gameDir, "Nothing", false, false));
        // mods folder does not exist in the empty game dir
        assertThrows(IOException.class,
                () -> service.saveBuild(emptyDir, "Ghost", true, false));
    }
}
