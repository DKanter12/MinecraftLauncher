package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

        assertEquals("my-performance-pack", saved.name());
        assertTrue(saved.mods());
        assertFalse(saved.configs());
        assertEquals("mods", saved.description());

        Path buildDir = gameDir.resolve("builds").resolve("my-performance-pack");
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
                .resolve("full-setup").resolve("mods").resolve("a.jar")));
        assertTrue(Files.isRegularFile(gameDir.resolve("builds")
                .resolve("full-setup").resolve("config").resolve("example.toml")));
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
                () -> service.saveBuild(gameDir, "!!!", true, false));
        assertThrows(IOException.class,
                () -> service.saveBuild(gameDir, "Nothing", false, false));
        // mods folder does not exist in the empty game dir
        assertThrows(IOException.class,
                () -> service.saveBuild(emptyDir, "Ghost", true, false));
    }

    @Test
    @DisplayName("listBuilds: sorted by name with contents flags; empty before any save")
    void listBuildsSorted(@TempDir Path gameDir) throws IOException {
        assertEquals(List.of(), service.listBuilds(gameDir));

        pack(gameDir, "a.jar");
        service.saveBuild(gameDir, "zeta", true, false);
        service.saveBuild(gameDir, "Alpha", true, true);

        List<BuildService.BuildInfo> builds = service.listBuilds(gameDir);
        assertEquals(2, builds.size());
        assertEquals("alpha", builds.get(0).name());
        assertTrue(builds.get(0).configs());
        assertEquals("zeta", builds.get(1).name());
        assertFalse(builds.get(1).configs());
    }

    @Test
    @DisplayName("selectBuild + applyBuild: selected build replaces mods and config at launch")
    void selectAndApplyReplacesFolders(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "old.jar");
        service.saveBuild(gameDir, "lite", true, true);

        // The user changes the live folders after saving the build
        Files.delete(gameDir.resolve("mods").resolve("old.jar"));
        Files.writeString(gameDir.resolve("mods").resolve("new.jar"), "jar");
        Files.writeString(gameDir.resolve("config").resolve("local.toml"),
                "local=1");

        service.selectBuild(gameDir, "lite");
        assertEquals(Optional.of("lite"), service.selectedBuild(gameDir));

        service.applyBuild(gameDir, "lite");

        // mods fully replaced with the build's snapshot
        Path mods = gameDir.resolve("mods");
        assertTrue(Files.isRegularFile(mods.resolve("old.jar")));
        assertFalse(Files.exists(mods.resolve("new.jar")));
        // config replaced too (the build includes configs)
        Path config = gameDir.resolve("config");
        assertTrue(Files.isRegularFile(config.resolve("example.toml")));
        assertFalse(Files.exists(config.resolve("local.toml")));

        // Clearing the selection: launch keeps the current folders
        service.selectBuild(gameDir, null);
        assertEquals(Optional.empty(), service.selectedBuild(gameDir));
        assertTrue(Files.exists(mods.resolve("old.jar"))); // untouched since
    }

    @Test
    @DisplayName("applyBuild: a mods-only build replaces mods but leaves config alone")
    void applyModsOnlyLeavesConfig(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "old.jar");
        service.saveBuild(gameDir, "modsonly", true, false);

        Files.writeString(gameDir.resolve("config").resolve("local.toml"),
                "local=1");
        service.applyBuild(gameDir, "modsonly");

        assertTrue(Files.isRegularFile(gameDir.resolve("mods")
                .resolve("old.jar")));
        assertTrue(Files.exists(gameDir.resolve("config").resolve("local.toml")));
    }

    @Test
    @DisplayName("deleteBuild: removes the build and clears a selection pointing at it")
    void deleteBuildClearsSelection(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "a.jar");
        service.saveBuild(gameDir, "gone", true, false);
        service.selectBuild(gameDir, "gone");

        service.deleteBuild(gameDir, "gone");

        assertFalse(Files.exists(gameDir.resolve("builds").resolve("gone")));
        assertEquals(Optional.empty(), service.selectedBuild(gameDir));
        assertEquals(List.of(), service.listBuilds(gameDir));
    }

    @Test
    @DisplayName("selectedBuild: a marker for a deleted build reads as no selection")
    void staleMarkerReadsAsEmpty(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "a.jar");
        service.saveBuild(gameDir, "vanish", true, false);
        service.selectBuild(gameDir, "vanish");

        // The build directory disappears behind the launcher's back
        deleteRecursively(gameDir.resolve("builds").resolve("vanish"));

        assertEquals(Optional.empty(), service.selectedBuild(gameDir));
    }

    @Test
    @DisplayName("switching builds: the new one becomes active, both stay saved untouched")
    void switchingBuildsKeepsSavedOnesIntact(@TempDir Path gameDir) throws IOException {
        pack(gameDir, "old.jar");
        service.saveBuild(gameDir, "first", true, true);

        // The live folders change after the first build was saved
        Files.delete(gameDir.resolve("mods").resolve("old.jar"));
        Files.writeString(gameDir.resolve("mods").resolve("new.jar"), "jar");
        service.saveBuild(gameDir, "second", true, true);

        // Build "first" is active and applied at launch
        service.selectBuild(gameDir, "first");
        service.applyBuild(gameDir, "first");
        assertTrue(Files.isRegularFile(gameDir.resolve("mods").resolve("old.jar")));

        // Replacing it with the completely new "second": no build is
        // deleted or re-created, only the selection marker moves
        service.selectBuild(gameDir, "second");
        assertEquals(Optional.of("second"), service.selectedBuild(gameDir));
        service.applyBuild(gameDir, "second");

        // Only the live folders of the installed game changed
        assertTrue(Files.isRegularFile(
                gameDir.resolve("mods").resolve("new.jar")));
        assertFalse(Files.exists(gameDir.resolve("mods").resolve("old.jar")));

        // Both builds remain, each with its own untouched snapshot
        assertEquals(List.of("first", "second"),
                service.listBuilds(gameDir).stream()
                        .map(BuildService.BuildInfo::name).toList());
        Path first = gameDir.resolve("builds").resolve("first");
        assertTrue(Files.isRegularFile(first.resolve("mods").resolve("old.jar")));
        assertFalse(Files.exists(first.resolve("mods").resolve("new.jar")));
        assertTrue(Files.isRegularFile(
                first.resolve("config").resolve("example.toml")));
        Path second = gameDir.resolve("builds").resolve("second");
        assertTrue(Files.isRegularFile(
                second.resolve("mods").resolve("new.jar")));
        assertFalse(Files.exists(second.resolve("mods").resolve("old.jar")));

        // Switching back needs no re-creation: "first" is still there
        service.selectBuild(gameDir, "first");
        assertEquals(Optional.of("first"), service.selectedBuild(gameDir));
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
