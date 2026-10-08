package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.example.launcher.infrastructure.server.BuildDescriptor;
import org.example.launcher.infrastructure.server.BuildSummary;
import org.example.launcher.infrastructure.server.BuildFileEntry;
import org.example.launcher.infrastructure.server.BuildFileCategory;
import org.example.launcher.infrastructure.server.BuildOrigin;
import org.example.launcher.infrastructure.server.ServerSession;
import org.example.launcher.infrastructure.server.RemoteBuildService;
import org.example.launcher.infrastructure.server.ModpackProvisioner;
import org.example.launcher.infrastructure.server.LoaderMismatchDetector;
import org.example.launcher.infrastructure.server.LoaderFallbackPolicy;
import org.example.launcher.infrastructure.server.UserRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.domain.model.ModLoaderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.example.launcher.application.build.Build;

class ModpackProvisionerTest {

    @TempDir
    Path tempDir;

    private static BuildDescriptor descriptor(String mc, ModLoaderType loader) {
        return new BuildDescriptor(
                new BuildSummary("pack", "1.0.0", "Pack", "d", loader, mc,
                        "47.4.23"),
                List.of(
                        new BuildFileEntry("a.jar", BuildFileCategory.MODS,
                                "s1", 1),
                        new BuildFileEntry("x.toml", BuildFileCategory.CONFIGS,
                                "s2", 2)),
                BuildOrigin.SERVER);
    }

    private static List<ModLoaderVersion> forgeVersions() {
        return List.of(
                new ModLoaderVersion(ModLoaderType.FORGE, "47.4.23", "1.20.1",
                        true, null),
                new ModLoaderVersion(ModLoaderType.FORGE, "47.2.0", "1.20.1",
                        true, null));
    }

    @Test
    void planPicksNewestLoader() throws IOException {
        ModpackProvisioner.ProvisionPlan plan = ModpackProvisioner.plan(
                descriptor("1.20.1", ModLoaderType.FORGE), null,
                forgeVersions());

        assertEquals("Pack", plan.instanceName());
        assertEquals(ModLoaderType.FORGE, plan.loaderType());
        assertEquals("1.20.1", plan.minecraftVersion());
        assertEquals("47.4.23", plan.loaderVersion());
        assertEquals(1, plan.modFiles().size());
        assertEquals(1, plan.configFiles().size());
    }

    @Test
    void planDefaultsToForgeWhenModsButNoLoader() throws IOException {
        ModpackProvisioner.ProvisionPlan plan = ModpackProvisioner.plan(
                descriptor("1.20.1", ModLoaderType.VANILLA), null,
                forgeVersions());

        assertEquals(ModLoaderType.FORGE, plan.loaderType());
        assertEquals("47.4.23", plan.loaderVersion());
    }

    @Test
    void planRejectsUnknownMc() {
        BuildDescriptor noMc = new BuildDescriptor(
                new BuildSummary("pack", "1.0.0", "Pack", "d",
                        ModLoaderType.VANILLA, "unknown", ""),
                List.of(new BuildFileEntry("a.jar", BuildFileCategory.MODS,
                        "s1", 1)),
                BuildOrigin.SERVER);

        assertThrows(IOException.class, () -> ModpackProvisioner.plan(
                noMc, null, forgeVersions()));
    }

    @Test
    void planRejectsEmptyInputs() {
        assertThrows(IOException.class, () -> ModpackProvisioner.plan(
                descriptor("1.20.1", ModLoaderType.FORGE), null, List.of()));
    }

    @Test
    void instantiateCreatesInstanceAndCopiesFiles() throws IOException {
        GameDirectory storage = new GameDirectory(tempDir.resolve("storage"));
        FileSystemBuildRepository profiles = new FileSystemBuildRepository(storage);
        Path staged = tempDir.resolve("staged").resolve("pack");
        Files.createDirectories(staged.resolve("mods"));
        Files.createDirectories(staged.resolve("config"));
        Files.writeString(staged.resolve("mods").resolve("a.jar"), "mod");
        Files.writeString(staged.resolve("config").resolve("x.toml"), "cfg");

        ModpackProvisioner.ProvisionPlan plan = ModpackProvisioner.plan(
                descriptor("1.20.1", ModLoaderType.FORGE), null,
                forgeVersions());
        ModdedProfile profile =
                ModpackProvisioner.instantiate(plan, profiles, staged);

        assertEquals("Pack", profile.name());
        assertEquals(ModLoaderType.FORGE, profile.loaderType());
        Path gameDir = profiles.resolveGameDir(profile);
        assertEquals("mod",
                Files.readString(gameDir.resolve("mods").resolve("a.jar")));
        assertEquals("cfg",
                Files.readString(gameDir.resolve("config").resolve("x.toml")));
    }

    @Test
    void fallbackStepsToOlder() {
        Optional<ModLoaderVersion> next =
                LoaderFallbackPolicy.nextOlder(forgeVersions(), "47.4.23");

        assertTrue(next.isPresent());
        assertEquals("47.2.0", next.get().loaderVersion());
        assertTrue(LoaderFallbackPolicy.nextOlder(forgeVersions(), "47.2.0")
                .isEmpty());
        assertTrue(LoaderFallbackPolicy.nextOlder(forgeVersions(), "0.0.0")
                .isEmpty());
        assertTrue(LoaderFallbackPolicy.nextOlder(List.of(), "47.4.23")
                .isEmpty());
    }

    @Test
    void detectorFindsMismatch() {
        String log = """
                [main/ERROR]: Forge Mod Loader has failed to load correctly.
                Missing or unsupported mandatory dependencies: jei
                """;

        assertTrue(LoaderMismatchDetector.detect(log).isPresent());
        assertTrue(LoaderMismatchDetector
                .detect("Mixin apply failed for modid.mixins.json").isPresent());
    }

    @Test
    void detectorIgnoresPlainCrashes() {
        assertTrue(LoaderMismatchDetector.detect(
                "java.lang.OutOfMemoryError: Java heap space").isEmpty());
        assertTrue(LoaderMismatchDetector.detect("").isEmpty());
        assertTrue(LoaderMismatchDetector.detect(null).isEmpty());
    }

    @Test
    void entryRequiresAChecksum() {
        assertThrows(IllegalArgumentException.class, () -> new BuildFileEntry(
                "a.jar", BuildFileCategory.MODS, null, null, null, 1));
    }

    @Test
    void newBuildsListsMissingOnes() throws IOException {
        FakeLauncherServerApi api = new FakeLauncherServerApi();
        api.build("one", "1.0.0")
                .file(BuildFileCategory.MODS, "a.jar", "a").publish();
        api.build("two", "1.0.0")
                .file(BuildFileCategory.MODS, "b.jar", "b").publish();
        RemoteBuildService builds = new RemoteBuildService(api);
        ServerSession session = new ServerSession("t", UserRole.USER, "x",
                "https://example", null);
        Path gameDir = tempDir.resolve("game");
        builds.install(session, api.builds.get("one").summary(), gameDir);

        List<BuildSummary> fresh = builds.newBuilds(session, gameDir);

        assertEquals(List.of("two"),
                fresh.stream().map(BuildSummary::id).toList());
    }
}


