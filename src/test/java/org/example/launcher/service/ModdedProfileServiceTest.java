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

    private ModdedProfile createFabricProfile(GameDirectory gameDir)
            throws IOException {
        return service(gameDir).createProfile(ModLoaderType.FABRIC,
                "0.16.9", "1.21.4", "fabric-loader-0.16.9-1.21.4",
                List.of("-Xmx4G"), null, 0);
    }

    @Test
    @DisplayName("createProfile: automatic name, unique directory with standard folders")
    void createProfileCreatesFolders(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = createFabricProfile(gameDir);

        // The name is derived automatically and never editable:
        // "Fabric 1.21.4" sanitized (dots/spaces collapse to dashes)
        assertEquals("Fabric 1.21.4", profile.name());
        assertEquals("fabric-1-21-4", profile.id());
        assertEquals("profiles/fabric-1-21-4", profile.gameDirPath());
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
    @DisplayName("createProfile: same version twice yields unique directories")
    void createProfileUniqueDirectories(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile first = createFabricProfile(gameDir);
        ModdedProfile second = createFabricProfile(gameDir);

        assertEquals("fabric-1-21-4", first.id());
        assertEquals("fabric-1-21-4-2", second.id());
        // Same automatic display name, different directories
        assertEquals(first.name(), second.name());
        assertNotEqualsDirs(gameDir, first, second);
    }

    private void assertNotEqualsDirs(GameDirectory gameDir, ModdedProfile a,
                                     ModdedProfile b) {
        assertFalse(gameDir.moddedProfileDir(a.id())
                .equals(gameDir.moddedProfileDir(b.id())));
    }

    @Test
    @DisplayName("createProfile: the name is always loader + MC version (Forge)")
    void createProfileAutomaticName(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = service(gameDir).createProfile(
                ModLoaderType.FORGE, "47.4.23", "1.20.1",
                "1.20.1-forge-47.4.23", List.of(), null, 0);

        assertEquals("Forge 1.20.1", profile.name());
        assertEquals("forge-1-20-1", profile.id());
    }

    @Test
    @DisplayName("profiles persist and reload (round-trip)")
    void persistenceRoundTrip(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        createFabricProfile(gameDir);
        svc.createProfile(ModLoaderType.FORGE, "47.4.23", "1.20.1",
                "1.20.1-forge-47.4.23", List.of(), null, 0);

        List<ModdedProfile> loaded = svc.loadProfiles();
        assertEquals(2, loaded.size());
        assertEquals("Fabric 1.21.4", loaded.get(0).name());
        assertEquals("Forge 1.20.1", loaded.get(1).name());
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
        ModdedProfile profile = svc.createProfile(
                ModLoaderType.VANILLA, "", "1.21.4", "1.21.4", List.of(),
                null, 0);

        assertEquals("vanilla-1-21-4", profile.id());
        assertEquals("Vanilla 1.21.4", profile.name());
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
        ModdedProfile profile = createFabricProfile(gameDir);
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
        ModdedProfile profile = createFabricProfile(gameDir);

        assertTrue(svc.loadProfiles().get(0).lastPlayedTime().isEmpty());

        svc.touchLastPlayed(profile.id());

        assertTrue(svc.loadProfiles().get(0).lastPlayedTime().isPresent());
    }

    @Test
    @DisplayName("updateProfile: renames (folder moves along), swaps JVM args and memory")
    void updateProfileKeepsIdentity(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir);
        svc.touchLastPlayed(profile.id());
        String lastPlayed = svc.loadProfiles().get(0).lastPlayedTimeRaw();
        String created = profile.createdTimeRaw();
        Path oldDir = gameDir.moddedProfileDir(profile.id());
        Files.writeString(oldDir.resolve("mods").resolve("mymod.jar"), "fake");

        Optional<ModdedProfile> updated = svc.updateProfile(profile.id(),
                List.of("-Dfml.earlyprogresswindow=false"),
                "My Renamed Pack", 6144);

        assertTrue(updated.isPresent());
        assertEquals("My Renamed Pack", updated.get().name());
        assertEquals(6144, updated.get().memoryMb());
        assertEquals(List.of("-Dfml.earlyprogresswindow=false"),
                updated.get().extraJvmArgs());

        // The folder follows the launcher name; the id follows the folder
        assertEquals("my-renamed-pack", updated.get().id());
        assertEquals("profiles/my-renamed-pack", updated.get().gameDirPath());
        Path newDir = gameDir.moddedProfileDir("my-renamed-pack");
        assertFalse(Files.exists(oldDir));
        assertEquals("fake",
                Files.readString(newDir.resolve("mods").resolve("mymod.jar")));

        // Versions, components and timestamps are untouched
        assertEquals(profile.versionId(), updated.get().versionId());
        assertEquals(created, updated.get().createdTimeRaw());
        assertEquals(lastPlayed, updated.get().lastPlayedTimeRaw());

        // Persisted for the next launch
        List<ModdedProfile> reloaded = svc.loadProfiles();
        assertEquals(1, reloaded.size());
        assertEquals("My Renamed Pack", reloaded.get(0).name());
        assertEquals("my-renamed-pack", reloaded.get(0).id());
        assertEquals(6144, reloaded.get(0).memoryMb());
        assertEquals(1, reloaded.get(0).extraJvmArgs().size());
    }

    @Test
    @DisplayName("updateProfile: rename conflict gets a numeric suffix")
    void updateProfileRenameConflict(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        createFabricProfile(gameDir);
        ModdedProfile second = svc.createProfile(ModLoaderType.FORGE,
                "47.4.23", "1.20.1", "1.20.1-forge-47.4.23",
                List.of(), null, 0);

        Optional<ModdedProfile> updated = svc.updateProfile(second.id(),
                List.of(), "Fabric 1.21.4", 0);

        assertTrue(updated.isPresent());
        assertEquals("Fabric 1.21.4", updated.get().name());
        assertEquals("fabric-1-21-4-2", updated.get().id());
        assertEquals("profiles/fabric-1-21-4-2", updated.get().gameDirPath());
        assertTrue(Files.isDirectory(
                gameDir.moddedProfileDir("fabric-1-21-4-2")));
    }

    @Test
    @DisplayName("updateProfile: same sanitized name keeps its folder")
    void updateProfileSameFolder(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir);
        Path dir = gameDir.moddedProfileDir(profile.id());
        Files.writeString(dir.resolve("mods").resolve("mymod.jar"), "fake");

        // "Fabric-1.21.4" sanitizes to the same directory — no move
        Optional<ModdedProfile> updated = svc.updateProfile(profile.id(),
                List.of(), "Fabric-1.21.4", 0);

        assertTrue(updated.isPresent());
        assertEquals(profile.id(), updated.get().id());
        assertEquals(profile.gameDirPath(), updated.get().gameDirPath());
        assertEquals("fake",
                Files.readString(dir.resolve("mods").resolve("mymod.jar")));
    }

    @Test
    @DisplayName("updateProfile: blank name keeps the old one")
    void updateProfileBlankNameKeepsOld(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = createFabricProfile(gameDir);

        Optional<ModdedProfile> updated = svc.updateProfile(profile.id(),
                List.of(), "   ", 0);

        assertTrue(updated.isPresent());
        assertEquals("Fabric 1.21.4", updated.get().name());
        assertEquals(0, updated.get().memoryMb());
        assertEquals(profile.id(), updated.get().id());
        assertEquals(profile.gameDirPath(), updated.get().gameDirPath());
    }

    @Test
    @DisplayName("createProfile: custom name drives the directory, persists with memory")
    void createProfileCustomName(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = svc.createProfile(ModLoaderType.NEOFORGE,
                "21.4.10-beta", "1.21.4", "neoforge-21.4.10-beta",
                List.of(), "My NeoForge Pack", 8192);

        assertEquals("My NeoForge Pack", profile.name());
        assertEquals("my-neoforge-pack", profile.id());
        assertEquals("profiles/my-neoforge-pack", profile.gameDirPath());
        assertEquals(8192, profile.memoryMb());

        List<ModdedProfile> loaded = svc.loadProfiles();
        assertEquals(1, loaded.size());
        assertEquals("My NeoForge Pack", loaded.get(0).name());
        assertEquals(8192, loaded.get(0).memoryMb());
    }

    @Test
    @DisplayName("createProfile: blank name falls back to the automatic one")
    void createProfileBlankNameFallsBack(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = service(gameDir).createProfile(
                ModLoaderType.FABRIC, "0.16.9", "1.21.4",
                "fabric-loader-0.16.9-1.21.4", List.of(), "  ", -100);

        assertEquals("Fabric 1.21.4", profile.name());
        assertEquals("fabric-1-21-4", profile.id());
        assertEquals(0, profile.memoryMb());
    }

    @Test
    @DisplayName("effectiveJvmArgs: memory first, conflicting Xmx/Xms dropped")
    void effectiveJvmArgs(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);
        ModdedProfile profile = svc.createProfile(ModLoaderType.FORGE,
                "47.4.23", "1.20.1", "1.20.1-forge-47.4.23",
                List.of("-Xmx2G", "-Xms1G", "-Dfml.queryResult=confirm"),
                null, 4096);

        assertEquals(List.of("-Xmx4096M", "-Dfml.queryResult=confirm"),
                ModdedProfileService.effectiveJvmArgs(profile));

        ModdedProfile auto = svc.createProfile(ModLoaderType.FABRIC,
                "0.16.9", "1.21.4", "fabric-loader-0.16.9-1.21.4",
                List.of("-Xmx4G"), null, 0);
        assertEquals(List.of("-Xmx4G"),
                ModdedProfileService.effectiveJvmArgs(auto));
    }

    @Test
    @DisplayName("formatMemory renders GB, MB and Auto")
    void formatMemory() {
        assertEquals("Auto", ModdedProfileService.formatMemory(0));
        assertEquals("Auto", ModdedProfileService.formatMemory(-5));
        assertEquals("4 GB", ModdedProfileService.formatMemory(4096));
        assertEquals("512 MB", ModdedProfileService.formatMemory(512));
    }

    @Test
    @DisplayName("updateProfile: unknown id returns empty")
    void updateProfileUnknownId(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileService svc = service(gameDir);

        assertTrue(svc.updateProfile("ghost", List.of(), null, 0).isEmpty());
    }

    @Test
    @DisplayName("resolveGameDir resolves against the storage root")
    void resolveGameDir(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile profile = createFabricProfile(gameDir);

        assertEquals(tempDir.resolve("profiles/fabric-1-21-4"),
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
    @DisplayName("sanitize transliterates Cyrillic")
    void sanitizeCyrillic() {
        assertEquals("moya-sborka",
                ModdedProfileService.sanitize("Моя сборка"));
        assertEquals("neoforge-test",
                ModdedProfileService.sanitize("NeoForge тест!"));
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
