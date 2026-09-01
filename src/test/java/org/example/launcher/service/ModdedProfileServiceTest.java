package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.service.modloader.ModLoaderType;

@DisplayName("ModdedProfileService")
class ModdedProfileServiceTest {

    private ModdedProfileService service(GameDirectory gameDir) {
        return new ModdedProfileService(gameDir);
    }

    private ModdedProfile createFabricProfile(GameDirectory gameDir, String name)
            throws IOException {
        return service(gameDir).createProfile(name, ModLoaderType.FABRIC,
                "0.16.9", "1.21.4", "fabric-loader-0.16.9-1.21.4", List.of("-Xmx4G"));
    }

    @Test
    @DisplayName("createProfile: creates unique directory with standard folders")
    void createProfileCreatesFolders(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = createFabricProfile(gameDir, "My Test Pack!");

        // Unsafe characters sanitized into a filesystem-safe name
        assertEquals("my-test-pack", profile.id());
        assertEquals("My Test Pack!", profile.name());
        assertEquals("profiles/my-test-pack", profile.gameDirPath());
        assertEquals("fabric-loader-0.16.9-1.21.4", profile.versionId());

        // Profile metadata
        assertEquals(ModLoaderType.FABRIC, profile.loaderType());
        assertEquals("0.16.9", profile.loaderVersion());
        assertEquals("1.21.4", profile.minecraftVersion());
        assertEquals(List.of("-Xmx4G"), profile.extraJvmArgs());
        assertTrue(profile.components().contains("minecraft:1.21.4"));
        assertTrue(profile.components().contains("fabric:0.16.9"));
        assertNotNull(profile.createdTimeRaw());

        // The expected version id follows the loader convention
        assertEquals("fabric-loader-0.16.9-1.21.4", profile.expectedVersionId());

        // Standard modded-pack folder layout created
        Path dir = gameDir.moddedProfileDir(profile.id());
        for (String folder : ModdedProfileService.STANDARD_FOLDERS) {
            assertTrue(Files.isDirectory(dir.resolve(folder)),
                    folder + "/ should exist in " + dir);
        }
    }

    @Test
    @DisplayName("createProfile: same name twice yields unique directories")
    void createProfileUniqueDirectories(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile first = createFabricProfile(gameDir, "Pack");
        ModdedProfile second = createFabricProfile(gameDir, "Pack");

        assertEquals("pack", first.id());
        assertEquals("pack-2", second.id());
        assertNotEqualsDirs(gameDir, first, second);
    }

    private void assertNotEqualsDirs(GameDirectory gameDir, ModdedProfile a,
                                     ModdedProfile b) {
        assertFalse(gameDir.moddedProfileDir(a.id())
                .equals(gameDir.moddedProfileDir(b.id())));
    }

    @Test
    @DisplayName("createProfile: blank name falls back to loader + MC version")
    void createProfileDefaultName(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = createFabricProfile(gameDir, "   ");

        // "Fabric 1.21.4" sanitized: dots/spaces collapse to dashes
        assertEquals("fabric-1-21-4", profile.id());
        assertEquals("Fabric 1.21.4", profile.name());
    }

    @Test
    @DisplayName("profiles persist and reload (round-trip)")
    void persistenceRoundTrip(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        createFabricProfile(gameDir, "Alpha");
        svc.createProfile("Beta", ModLoaderType.FORGE, "47.4.23", "1.20.1",
                "1.20.1-forge-47.4.23", List.of());

        List<ModdedProfile> loaded = svc.loadProfiles();
        assertEquals(2, loaded.size());
        assertEquals("Alpha", loaded.get(0).name());
        assertEquals("Beta", loaded.get(1).name());
        assertEquals(ModLoaderType.FORGE, loaded.get(1).loaderType());
        assertEquals(List.of(), loaded.get(1).extraJvmArgs());

        // Storage file location
        assertTrue(Files.isRegularFile(gameDir.instancesFile()));
    }

    @Test
    @DisplayName("vanilla instances: no loader component, vanilla version id")
    void createVanillaProfile(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = svc.createProfile("Clean SMP",
                ModLoaderType.VANILLA, "", "1.21.4", "1.21.4", List.of());

        assertEquals("clean-smp", profile.id());
        assertEquals(ModLoaderType.VANILLA, profile.loaderType());
        assertTrue(profile.isVanilla());
        assertEquals("", profile.loaderVersion());
        // Only the minecraft component — no loader
        assertEquals(List.of("minecraft:1.21.4"), profile.components());
        // Vanilla launches the vanilla version id itself
        assertEquals("1.21.4", profile.expectedVersionId());
        assertEquals("Vanilla \u00b7 MC 1.21.4", profile.summary());
        assertTrue(Files.isDirectory(
                gameDir.moddedProfileDir(profile.id()).resolve("mods")));
    }

    @Test
    @DisplayName("legacy modded_profiles.json is read as a fallback")
    void legacyFileFallback(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        Files.writeString(gameDir.legacyModdedProfilesFile(), """
                { "profiles": [ {
                    "id": "old-pack",
                    "name": "Old Pack",
                    "loaderType": "FABRIC",
                    "loaderVersion": "0.16.9",
                    "minecraftVersion": "1.21.4",
                    "versionId": "fabric-loader-0.16.9-1.21.4",
                    "gameDirPath": "profiles/old-pack",
                    "components": ["minecraft:1.21.4", "fabric:0.16.9"],
                    "extraJvmArgs": [],
                    "createdTime": "2024-12-03T10:00:00+00:00"
                  } ] }
                """, java.nio.charset.StandardCharsets.UTF_8);

        List<ModdedProfile> loaded = service(gameDir).loadProfiles();

        assertEquals(1, loaded.size());
        assertEquals("Old Pack", loaded.get(0).name());

        // Saving migrates the registry to the new instances.json
        service(gameDir).touchLastPlayed("old-pack");
        assertTrue(Files.isRegularFile(gameDir.instancesFile()));
    }

    @Test
    @DisplayName("deleteProfile: removes the entry, keeps the game directory")
    void deleteProfileKeepsFiles(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir, "Doomed");
        Path dir = gameDir.moddedProfileDir(profile.id());

        Optional<Path> kept = svc.deleteProfile(profile.id());

        assertTrue(kept.isPresent());
        assertEquals(dir, kept.get());
        assertTrue(svc.loadProfiles().isEmpty());
        // User data (mods/saves) survives
        assertTrue(Files.isDirectory(dir.resolve("mods")));
    }

    @Test
    @DisplayName("touchLastPlayed records the launch timestamp")
    void touchLastPlayed(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir, "Played");

        assertTrue(svc.loadProfiles().get(0).lastPlayedTime().isEmpty());

        svc.touchLastPlayed(profile.id());

        assertTrue(svc.loadProfiles().get(0).lastPlayedTime().isPresent());
    }

    @Test
    @DisplayName("updateProfile: renames and swaps JVM args, keeps id/dir/timestamps")
    void updateProfileKeepsIdentity(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir, "Old Name");
        svc.touchLastPlayed(profile.id());
        String lastPlayed = svc.loadProfiles().get(0).lastPlayedTimeRaw();
        String created = profile.createdTimeRaw();
        Path dir = gameDir.moddedProfileDir(profile.id());

        Optional<ModdedProfile> updated = svc.updateProfile(profile.id(),
                "New Name", List.of("-Xmx6G", "-Dfml.earlyprogresswindow=false"));

        assertTrue(updated.isPresent());
        assertEquals("New Name", updated.get().name());
        assertEquals(List.of("-Xmx6G", "-Dfml.earlyprogresswindow=false"),
                updated.get().extraJvmArgs());

        // Identity is fixed: directory, versions, components, timestamps
        assertEquals(profile.id(), updated.get().id());
        assertEquals(profile.gameDirPath(), updated.get().gameDirPath());
        assertEquals(profile.versionId(), updated.get().versionId());
        assertEquals(created, updated.get().createdTimeRaw());
        assertEquals(lastPlayed, updated.get().lastPlayedTimeRaw());

        // Persisted for the next launch
        List<ModdedProfile> reloaded = svc.loadProfiles();
        assertEquals(1, reloaded.size());
        assertEquals("New Name", reloaded.get(0).name());
        assertEquals(2, reloaded.get(0).extraJvmArgs().size());
        // The game directory on disk is untouched
        assertTrue(Files.isDirectory(dir.resolve("mods")));
    }

    @Test
    @DisplayName("updateProfile: blank name keeps the current name")
    void updateProfileBlankNameKeepsOld(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir, "Keep Me");

        Optional<ModdedProfile> updated = svc.updateProfile(profile.id(),
                "   ", List.of());

        assertEquals("Keep Me", updated.get().name());
        assertEquals(List.of(), updated.get().extraJvmArgs());
    }

    @Test
    @DisplayName("updateProfile: unknown id returns empty")
    void updateProfileUnknownId(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);

        assertTrue(svc.updateProfile("ghost", "X", List.of()).isEmpty());
    }

    @Test
    @DisplayName("resolveGameDir resolves against the storage root")
    void resolveGameDir(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = createFabricProfile(gameDir, "Resolved");

        assertEquals(tempDir.resolve("profiles/resolved"),
                service(gameDir).resolveGameDir(profile));
    }

    @Test
    @DisplayName("ensureProfileFolders does not touch existing user files")
    void ensureProfileFoldersNonDestructive(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("pack");
        Files.createDirectories(dir.resolve("mods"));
        Files.writeString(dir.resolve("mods").resolve("mymod.jar"), "fake");

        ModdedProfileService.ensureProfileFolders(dir);

        assertEquals("fake",
                Files.readString(dir.resolve("mods").resolve("mymod.jar")));
        assertTrue(Files.isDirectory(dir.resolve("saves")));
        assertTrue(Files.isDirectory(dir.resolve("shaderpacks")));
    }

    @Test
    @DisplayName("sanitize keeps only safe characters")
    void sanitizeNames() {
        assertEquals("my-pack", ModdedProfileService.sanitize("My Pack"));
        assertEquals("a-b-c", ModdedProfileService.sanitize("a?b*c!"));
        assertEquals("", ModdedProfileService.sanitize("///"));
        assertEquals("", ModdedProfileService.sanitize(null));
    }

    @Test
    @DisplayName("GameDirectory exposes the instance paths")
    void gameDirectoryProfilePaths(@TempDir Path tempDir) {
        GameDirectory gameDir = new GameDirectory(tempDir);

        assertEquals(tempDir.resolve("profiles"), gameDir.moddedProfilesRoot());
        assertEquals(tempDir.resolve("profiles").resolve("mypack"),
                gameDir.moddedProfileDir("mypack"));
        assertEquals(tempDir.resolve("instances.json"),
                gameDir.instancesFile());
        assertEquals(tempDir.resolve("modded_profiles.json"),
                gameDir.legacyModdedProfilesFile());
    }
}
