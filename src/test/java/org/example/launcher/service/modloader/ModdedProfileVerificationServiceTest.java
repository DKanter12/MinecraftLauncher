package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.Sha1ChecksumVerifier;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.MinecraftLaunchArgumentBuilder;
import org.example.launcher.service.MinecraftLauncher;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.StandardVersionType;
import org.example.launcher.version.VersionTypeRegistry;

@DisplayName("ModdedProfileVerificationService")
class ModdedProfileVerificationServiceTest {

    // --- Stubs (offline vanilla chain) ---

    private static final class StubVersionService extends MojangVersionService {
        StubVersionService() {
            super("http://localhost/manifest", java.net.http.HttpClient.newHttpClient(),
                    new com.google.gson.Gson(), new VersionTypeRegistry());
        }

        @Override
        public VersionManifest fetchVersions() {
            return new VersionManifest("1.21.4", "1.21.4", List.of(
                    new MinecraftVersion("1.21.4", StandardVersionType.RELEASE,
                            "2024-12-03T10:00:00+00:00", "http://localhost/1.21.4.json")));
        }
    }

    /** Vanilla metadata: one library whose file does not exist. */
    private static final class StubMetadataService extends MojangVersionMetadataService {
        private final String vanillaJson;

        StubMetadataService(String vanillaJson) {
            this.vanillaJson = vanillaJson;
        }

        @Override
        public VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException {
            return parseMetadata(vanillaJson, version.id());
        }
    }

    private static final JavaResolutionService ALWAYS_JAVA = new JavaResolutionService() {
        @Override
        public JavaResolutionResult resolve(VersionMetadata metadata) {
            return JavaResolutionResult.found(new JavaRuntime(
                    Path.of("java"), 21, JavaRuntime.Source.CUSTOM));
        }

        @Override
        public JavaResolutionResult resolve(int requiredMajor) {
            return JavaResolutionResult.found(new JavaRuntime(
                    Path.of("java"), 21, JavaRuntime.Source.CUSTOM));
        }
    };

    private static final JavaResolutionService NEVER_JAVA = new JavaResolutionService() {
        @Override
        public JavaResolutionResult resolve(VersionMetadata metadata) {
            return JavaResolutionResult.notFound("stub: no java");
        }

        @Override
        public JavaResolutionResult resolve(int requiredMajor) {
            return JavaResolutionResult.notFound("stub: no java");
        }
    };

    /** Records installation calls (vanilla repair path). */
    private static final class RecordingInstaller
            implements org.example.launcher.install.InstallationService {
        int installCalls;
        MinecraftVersion installedVersion;
        GameDirectory installedGameDir;

        @Override
        public org.example.launcher.install.InstallationResult install(
                MinecraftVersion version,
                VersionMetadata metadata,
                GameDirectory gameDir,
                org.example.launcher.install.InstallationProgress progress) {
            installCalls++;
            installedVersion = version;
            installedGameDir = gameDir;
            return new org.example.launcher.install.InstallationResult(
                    0, 0, 0, 0, 0, List.of());
        }
    }

    private static final String MINIMAL_VANILLA_JSON = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "libraries": [],
              "arguments": { "game": ["--username", "${auth_player_name}"] }
            }
            """;

    private static final String VANILLA_WITH_LIBRARY_JSON = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "libraries": [
                { "name": "com.example:missing:1.0",
                  "downloads": { "artifact": { "path": "com/example/missing/1.0/missing-1.0.jar",
                    "sha1": "abc", "size": 10,
                    "url": "https://example.com/missing-1.0.jar" } } }
              ],
              "arguments": { "game": ["--username", "${auth_player_name}"] }
            }
            """;

    private static ModdedProfile fabricProfile() {
        return new ModdedProfile("pack", "Pack", ModLoaderType.FABRIC, "0.16.9",
                "1.21.4", "fabric-loader-0.16.9-1.21.4", "profiles/pack",
                List.of("minecraft:1.21.4", "fabric:0.16.9"), List.of(),
                "2024-12-03T10:00:00+00:00", null);
    }

    private static ModdedProfile vanillaProfile() {
        return new ModdedProfile("clean", "Clean", ModLoaderType.VANILLA, "",
                "1.21.4", "1.21.4", "profiles/clean",
                List.of("minecraft:1.21.4"), List.of(),
                "2024-12-03T10:00:00+00:00", null);
    }

    private static void writeVersionJson(GameDirectory gameDir, String id, String json)
            throws IOException {
        Path jsonFile = gameDir.versionMetadata(id);
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
    }

    private static final String FABRIC_JSON = """
            {
              "id": "fabric-loader-0.16.9-1.21.4",
              "inheritsFrom": "1.21.4",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [],
              "arguments": { "game": ["--fabric"], "jvm": [] }
            }
            """;

    private ModdedProfileVerificationService service(String vanillaJson,
                                                     JavaResolutionService javaService) {
        return service(vanillaJson, javaService, new RecordingInstaller());
    }

    private ModdedProfileVerificationService service(String vanillaJson,
                                                     JavaResolutionService javaService,
                                                     RecordingInstaller installer) {
        StubVersionService versionService = new StubVersionService();
        StubMetadataService metadataService = new StubMetadataService(vanillaJson);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                versionService, metadataService,
                new ModLoaderMetadataMerger(metadataService));
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(), javaService,
                new Sha1ChecksumVerifier());
        ModLoaderRegistry registry = new ModLoaderRegistry();
        return new ModdedProfileVerificationService(moddedVersionService,
                versionService, metadataService, registry, launcher,
                javaService, installer);
    }

    // --- Tests ---

    @Test
    @DisplayName("verify: complete installation passes")
    void verifyHappyPath(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "pack");

        ModdedProfileVerificationService svc =
                service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA);
        var report = svc.verify(fabricProfile(), gameDir);

        assertTrue(report.ok(), () -> "errors: " + report.errors());
        assertTrue(report.errors().isEmpty());
        assertTrue(report.metadata().isPresent());
        assertEquals("fabric-loader-0.16.9-1.21.4",
                report.metadata().get().id());
    }

    @Test
    @DisplayName("verify: missing version JSON is reported with a cause")
    void verifyMissingVersionJson(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        ModdedProfileVerificationService svc =
                service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA);
        var report = svc.verify(fabricProfile(), gameDir);

        assertFalse(report.ok());
        assertTrue(report.metadata().isEmpty());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("configuration file is missing")));
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("reinstalled")));
    }

    @Test
    @DisplayName("verify: Minecraft version mismatch is detected")
    void verifyMinecraftVersionMismatch(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.20.1", """
                { "id": "fabric-loader-0.16.9-1.20.1", "inheritsFrom": "1.20.1",
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "libraries": [] }
                """);

        ModdedProfile profile = new ModdedProfile("pack", "Pack",
                ModLoaderType.FABRIC, "0.16.9", "1.21.4",
                "fabric-loader-0.16.9-1.20.1", "profiles/pack",
                List.of(), List.of(), null, null);

        var report = service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA)
                .verify(profile, gameDir);

        assertFalse(report.ok());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("Minecraft version mismatch")));
    }

    @Test
    @DisplayName("verify: loader version mismatch is detected")
    void verifyLoaderVersionMismatch(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        // JSON id does not follow the profile's loader version convention
        writeVersionJson(gameDir, "fabric-loader-0.15.0-1.21.4", """
                { "id": "fabric-loader-0.15.0-1.21.4", "inheritsFrom": "1.21.4",
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "libraries": [] }
                """);

        ModdedProfile profile = new ModdedProfile("pack", "Pack",
                ModLoaderType.FABRIC, "0.16.9", "1.21.4",
                "fabric-loader-0.15.0-1.21.4", "profiles/pack",
                List.of(), List.of(), null, null);

        var report = service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA)
                .verify(profile, gameDir);

        assertFalse(report.ok());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("Loader version mismatch")));
    }

    @Test
    @DisplayName("verify: missing dependencies are reported individually")
    void verifyMissingDependencies(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "pack");

        // Vanilla metadata references a library file that does not exist
        var report = service(VANILLA_WITH_LIBRARY_JSON, ALWAYS_JAVA)
                .verify(fabricProfile(), gameDir);

        assertFalse(report.ok());
        assertTrue(report.metadata().isPresent(), "chain still resolves");
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("Missing or corrupt dependency")
                        && e.contains("missing-1.0.jar")));
    }

    @Test
    @DisplayName("verify: unresolvable Java runtime blocks the launch")
    void verifyJavaMissing(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "pack");

        var report = service(MINIMAL_VANILLA_JSON, NEVER_JAVA)
                .verify(fabricProfile(), gameDir);

        assertFalse(report.ok());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("No suitable Java runtime")));
    }

    @Test
    @DisplayName("verify: missing profile directory is a warning, not an error")
    void verifyProfileDirMissingIsWarning(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);

        var report = service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA)
                .verify(fabricProfile(), gameDir);

        assertTrue(report.ok(), () -> "errors: " + report.errors());
        assertTrue(report.warnings().stream().anyMatch(w ->
                w.contains("will be created on launch")));
    }

    @Test
    @DisplayName("repair: missing loader JSON → full reinstall with the installer URL from the provider")
    void repairRunsInstaller(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        StubVersionService versionService = new StubVersionService();
        StubMetadataService metadataService =
                new StubMetadataService(MINIMAL_VANILLA_JSON);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                versionService, metadataService,
                new ModLoaderMetadataMerger(metadataService));
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(), ALWAYS_JAVA,
                new Sha1ChecksumVerifier());

        AtomicInteger installCalls = new AtomicInteger();
        ModLoaderInstaller fakeInstaller = (vanillaVersion, vanillaMetadata,
                loader, dir, progress) -> {
            installCalls.incrementAndGet();
            assertEquals("1.21.4", vanillaVersion.id());
            assertEquals("0.16.9", loader.loaderVersion());
            assertTrue(loader.installerUrlOpt().isPresent(),
                    "installer URL resolved from the provider");
            assertEquals(gameDir, dir);
            return new ModLoaderInstaller.ModLoaderInstallResult(
                    loader.installedVersionId(), null);
        };

        ModLoaderRegistry registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC,
                mc -> List.of(new ModLoaderVersion(ModLoaderType.FABRIC,
                        "0.16.9", "1.21.4", true,
                        "https://example.com/fabric-installer.jar")),
                fakeInstaller);

        ModdedProfileVerificationService svc = new ModdedProfileVerificationService(
                moddedVersionService, versionService, metadataService, registry,
                launcher, ALWAYS_JAVA, new RecordingInstaller());

        var result = svc.repair(fabricProfile(), gameDir, InstallationProgress.NONE);

        assertEquals(1, installCalls.get());
        assertEquals("fabric-loader-0.16.9-1.21.4", result.versionId());
    }

    @Test
    @DisplayName("repair: intact loader, corrupt shared file → re-download via installation service only")
    void repairRedownloadsSharedFilesWithoutLoaderReinstall(
            @TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        // The loader JSON is present and resolves — only shared files
        // (e.g. a corrupt asset index) need re-downloading
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);

        StubVersionService versionService = new StubVersionService();
        StubMetadataService metadataService =
                new StubMetadataService(MINIMAL_VANILLA_JSON);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                versionService, metadataService,
                new ModLoaderMetadataMerger(metadataService));
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(), ALWAYS_JAVA,
                new Sha1ChecksumVerifier());

        AtomicInteger installerCalls = new AtomicInteger();
        ModLoaderInstaller neverInstaller = (vanillaVersion, vanillaMetadata,
                loader, dir, progress) -> {
            installerCalls.incrementAndGet();
            return null;
        };
        ModLoaderRegistry registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC,
                mc -> List.of(), neverInstaller);

        RecordingInstaller installationService = new RecordingInstaller();
        ModdedProfileVerificationService svc = new ModdedProfileVerificationService(
                moddedVersionService, versionService, metadataService,
                registry, launcher, ALWAYS_JAVA, installationService);

        var result = svc.repair(fabricProfile(), gameDir, InstallationProgress.NONE);

        // The standard installation ran over the merged metadata…
        assertEquals(1, installationService.installCalls);
        assertEquals("fabric-loader-0.16.9-1.21.4",
                installationService.installedVersion.id());
        // …and the loader itself was NOT reinstalled
        assertEquals(0, installerCalls.get());
        assertEquals("fabric-loader-0.16.9-1.21.4", result.versionId());
    }

    @Test
    @DisplayName("repair: provider unavailable → loader reconstructed from the profile data")
    void repairFallsBackToProfileLoaderData(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        StubVersionService versionService = new StubVersionService();
        StubMetadataService metadataService =
                new StubMetadataService(MINIMAL_VANILLA_JSON);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                versionService, metadataService,
                new ModLoaderMetadataMerger(metadataService));
        MinecraftLauncher launcher = new MinecraftLauncher(
                new MinecraftLaunchArgumentBuilder(), ALWAYS_JAVA,
                new Sha1ChecksumVerifier());

        AtomicInteger installCalls = new AtomicInteger();
        ModLoaderInstaller fakeInstaller = (vanillaVersion, vanillaMetadata,
                loader, dir, progress) -> {
            installCalls.incrementAndGet();
            assertEquals("0.16.9", loader.loaderVersion());
            assertTrue(loader.installerUrlOpt().isEmpty(),
                    "no URL available offline — profile data only");
            return new ModLoaderInstaller.ModLoaderInstallResult(
                    loader.installedVersionId(), null);
        };

        ModLoaderRegistry registry = new ModLoaderRegistry();
        registry.register(ModLoaderType.FABRIC,
                mc -> { throw new IOException("offline"); },
                fakeInstaller);

        ModdedProfileVerificationService svc = new ModdedProfileVerificationService(
                moddedVersionService, versionService, metadataService, registry,
                launcher, ALWAYS_JAVA, new RecordingInstaller());

        var result = svc.repair(fabricProfile(), gameDir, InstallationProgress.NONE);

        assertEquals(1, installCalls.get());
        assertEquals("fabric-loader-0.16.9-1.21.4", result.versionId());
    }

    // --- Vanilla instances ---

    @Test
    @DisplayName("verify: complete vanilla instance passes (no loader JSON needed)")
    void verifyVanillaHappyPath(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        // Local vanilla version JSON — the game is installed
        writeVersionJson(gameDir, "1.21.4", """
                { "id": "1.21.4", "type": "release",
                  "mainClass": "net.minecraft.client.main.Main",
                  "libraries": [] }
                """);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "clean");

        var report = service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA)
                .verify(vanillaProfile(), gameDir);

        assertTrue(report.ok(), () -> "errors: " + report.errors());
        assertTrue(report.metadata().isPresent());
        assertEquals("1.21.4", report.metadata().get().id());
    }

    @Test
    @DisplayName("verify: not-yet-installed vanilla instance reports missing files and stays repairable")
    void verifyVanillaNotInstalled(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "clean");

        // Vanilla metadata references a client JAR that does not exist
        String withClientJar = """
                {
                  "id": "1.21.4",
                  "type": "release",
                  "mainClass": "net.minecraft.client.main.Main",
                  "libraries": [],
                  "downloads": { "client": { "url": "https://example.com/client.jar",
                    "sha1": "abc", "size": 100 } },
                  "arguments": { "game": ["--username", "${auth_player_name}"] }
                }
                """;

        var report = service(withClientJar, ALWAYS_JAVA)
                .verify(vanillaProfile(), gameDir);

        assertFalse(report.ok());
        // Metadata still resolves → repair (install) is possible
        assertTrue(report.metadata().isPresent());
        assertTrue(report.isRepairableByInstall());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("Missing or corrupt dependency")
                        && e.contains("client JAR")));
    }

    @Test
    @DisplayName("verify: vanilla instance missing dependencies")
    void verifyVanillaMissingDependencies(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "1.21.4", """
                { "id": "1.21.4", "type": "release",
                  "mainClass": "net.minecraft.client.main.Main",
                  "libraries": [] }
                """);
        ModdedProfileServiceTestHelper.ensureFolders(gameDir, "clean");

        var report = service(VANILLA_WITH_LIBRARY_JSON, ALWAYS_JAVA)
                .verify(vanillaProfile(), gameDir);

        assertFalse(report.ok());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("Missing or corrupt dependency")
                        && e.contains("missing-1.0.jar")));
    }

    @Test
    @DisplayName("verify: unknown MC version in a vanilla instance")
    void verifyVanillaUnknownVersion(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        ModdedProfile ghost = new ModdedProfile("ghost", "Ghost",
                ModLoaderType.VANILLA, "", "1.99.99", "1.99.99",
                "profiles/ghost", List.of(), List.of(), null, null);

        var report = service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA)
                .verify(ghost, gameDir);

        assertFalse(report.ok());
        assertTrue(report.metadata().isEmpty());
        assertTrue(report.errors().stream().anyMatch(e ->
                e.contains("was not found in the Mojang manifest")));
    }

    @Test
    @DisplayName("repair: vanilla instance installs via the installation service")
    void repairVanillaInstallsGame(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        RecordingInstaller installer = new RecordingInstaller();

        ModdedProfileVerificationService svc =
                service(MINIMAL_VANILLA_JSON, ALWAYS_JAVA, installer);
        var result = svc.repair(vanillaProfile(), gameDir, InstallationProgress.NONE);

        assertEquals("1.21.4", result.versionId());
        assertEquals(1, installer.installCalls);
        assertEquals("1.21.4", installer.installedVersion.id());
        assertEquals(gameDir.root(), installer.installedGameDir.root());
    }

    /** Small helper so tests share the folder layout logic. */
    static final class ModdedProfileServiceTestHelper {
        static void ensureFolders(GameDirectory gameDir, String profileId)
                throws IOException {
            org.example.launcher.service.ModdedProfileService.ensureProfileFolders(
                    gameDir.moddedProfileDir(profileId));
        }
    }
}
