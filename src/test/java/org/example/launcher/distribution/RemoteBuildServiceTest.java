package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.service.BuildService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteBuildServiceTest {

    @TempDir
    Path tempDir;

    private FakeLauncherServerApi api;
    private RemoteBuildService service;
    private ServerSession session;

    @BeforeEach
    void setUp() {
        api = new FakeLauncherServerApi();
        service = new RemoteBuildService(api);
        session = new ServerSession("tester", UserRole.USER, "token", "https://launcher.example", null);
    }

    @Test
    void installPlacesFilesAndManifestByCategory() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .file(BuildFileCategory.RESOURCES, "packs/summer.zip", "pack-bytes")
                .publish();

        BuildDescriptor installed = service.install(session,
                api.builds.get("bettersurvival").summary(), tempDir);

        assertEquals("1.0.0", installed.version());
        Path buildDir = tempDir.resolve("builds").resolve("bettersurvival");
        assertEquals("sodium-bytes", Files.readString(buildDir.resolve("mods/sodium.jar")));
        assertEquals("config-bytes", Files.readString(buildDir.resolve("config/sodium.toml")));
        assertEquals("pack-bytes", Files.readString(buildDir.resolve("resources/packs/summer.zip")));
        assertTrue(Files.isRegularFile(buildDir.resolve(RemoteBuildService.MANIFEST_FILE_NAME)));
    }

    @Test
    void installedServerBuildAppearsInLocalBuildList() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .publish();
        BuildService buildService = new BuildService();

        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);

        List<BuildService.BuildInfo> builds = buildService.listBuilds(tempDir);
        assertEquals(1, builds.size());
        assertEquals("bettersurvival", builds.get(0).name());
        assertTrue(builds.get(0).mods());
        assertTrue(builds.get(0).configs());
    }

    @Test
    void installingSecondBuildLeavesFirstIntact() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .publish();
        api.build("techmagic", "2.0.0")
                .file(BuildFileCategory.MODS, "tech.jar", "tech-bytes")
                .publish();

        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);
        service.install(session, api.builds.get("techmagic").summary(), tempDir);

        Path first = tempDir.resolve("builds").resolve("bettersurvival");
        Path second = tempDir.resolve("builds").resolve("techmagic");
        assertEquals("sodium-bytes", Files.readString(first.resolve("mods/sodium.jar")));
        assertEquals("config-bytes", Files.readString(first.resolve("config/sodium.toml")));
        assertEquals("tech-bytes", Files.readString(second.resolve("mods/tech.jar")));
        assertFalse(Files.exists(second.resolve("config")));
        assertEquals(2, service.installedBuilds(tempDir).size());
    }

    @Test
    void reinstallingSameVersionDownloadsNothing() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .publish();

        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);
        int downloadsFirst = api.downloads.size();

        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);

        assertEquals(downloadsFirst, api.downloads.size());
    }

    @Test
    void updateDownloadsChangedDeletesRemovedKeepsUnchanged() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-1")
                .file(BuildFileCategory.MODS, "old.jar", "old-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .publish();
        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);
        api.downloads.clear();

        api.build("bettersurvival", "1.1.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-2")
                .file(BuildFileCategory.MODS, "new.jar", "new-bytes")
                .file(BuildFileCategory.CONFIGS, "sodium.toml", "config-bytes")
                .publish();
        BuildDescriptor updated = service.update(session, tempDir, "bettersurvival");

        Path buildDir = tempDir.resolve("builds").resolve("bettersurvival");
        assertEquals("1.1.0", updated.version());
        assertEquals("sodium-2", Files.readString(buildDir.resolve("mods/sodium.jar")));
        assertTrue(Files.isRegularFile(buildDir.resolve("mods/new.jar")));
        assertFalse(Files.exists(buildDir.resolve("mods/old.jar")), "removed file must be deleted");
        assertEquals("config-bytes", Files.readString(buildDir.resolve("config/sodium.toml")));

        // changed sodium.jar + new new.jar downloaded; unchanged sodium.toml and nothing else
        assertEquals(2, api.downloads.size());
        assertTrue(api.downloads.contains("bettersurvival/mods/sodium.jar"));
        assertTrue(api.downloads.contains("bettersurvival/mods/new.jar"));
        assertFalse(api.downloads.contains("bettersurvival/config/sodium.toml"));

        Optional<BuildDescriptor> manifest = service.installedBuild(tempDir, "bettersurvival");
        assertTrue(manifest.isPresent());
        assertEquals("1.1.0", manifest.get().version());
    }

    @Test
    void updateAvailableComparesVersionsOfSameBuild() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .publish();
        BuildDescriptor installed = service.install(session,
                api.builds.get("bettersurvival").summary(), tempDir);
        BuildSummary same = api.builds.get("bettersurvival").summary();
        assertFalse(service.updateAvailable(installed, same));

        api.build("bettersurvival", "1.1.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .publish();
        assertTrue(service.updateAvailable(installed, api.builds.get("bettersurvival").summary()));

        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .publish();
        assertFalse(service.updateAvailable(installed, api.builds.get("bettersurvival").summary()));
    }

    @Test
    void tamperedLocalFileIsRedownloaded() throws IOException {
        api.build("bettersurvival", "1.0.0")
                .file(BuildFileCategory.MODS, "sodium.jar", "sodium-bytes")
                .publish();
        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);
        api.downloads.clear();
        Path jar = tempDir.resolve("builds").resolve("bettersurvival").resolve("mods/sodium.jar");
        Files.writeString(jar, "tampered");

        service.install(session, api.builds.get("bettersurvival").summary(), tempDir);

        assertEquals(1, api.downloads.size());
        assertEquals("sodium-bytes", Files.readString(jar));
    }

    @Test
    void fetchFailurePropagatesAsIOException() {
        IOException error = assertThrows(IOException.class,
                () -> service.update(session, tempDir, "unknown-build"));
        assertTrue(error.getMessage().contains("unknown-build"));
    }
}
