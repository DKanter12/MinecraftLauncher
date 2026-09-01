package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.VersionTypeRegistry;

@DisplayName("ModdedVersionService")
class ModdedVersionServiceTest {

    private static final String VANILLA_JSON = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "libraries": []
            }
            """;

    private static final String FABRIC_JSON = """
            {
              "id": "fabric-loader-0.16.9-1.21.4",
              "inheritsFrom": "1.21.4",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": []
            }
            """;

    /** Manifest stub exposing only the vanilla 1.21.4 entry. */
    private static final class StubVersionService extends MojangVersionService {
        StubVersionService() {
            super("http://localhost/manifest", java.net.http.HttpClient.newHttpClient(),
                    new com.google.gson.Gson(), new VersionTypeRegistry());
        }

        @Override
        public VersionManifest fetchVersions() {
            return new VersionManifest("1.21.4", "1.21.4", List.of(
                    new MinecraftVersion("1.21.4",
                            org.example.launcher.version.StandardVersionType.RELEASE,
                            "2024-12-03T10:00:00+00:00", "http://localhost/1.21.4.json")));
        }
    }

    /** Metadata stub returning a fixed vanilla metadata for any version. */
    private static final class StubMetadataService extends MojangVersionMetadataService {
        @Override
        public VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException {
            return parseMetadata(VANILLA_JSON, version.id());
        }
    }

    private final ModdedVersionService service = new ModdedVersionService(
            new StubVersionService(), new StubMetadataService(),
            new ModLoaderMetadataMerger(new StubMetadataService()));

    @Test
    @DisplayName("listInstalled: finds modded versions, skips vanilla and corrupt JSON")
    void listInstalledScansVersionsDir(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);
        writeVersionJson(gameDir, "neoforge-21.4.147", """
                { "id": "neoforge-21.4.147", "inheritsFrom": "1.21.4",
                  "mainClass": "net.neoforged.fml.loading.ImmediateWindowHandler" }
                """);
        // Vanilla install: no inheritsFrom, no loader id pattern
        writeVersionJson(gameDir, "1.21.4", VANILLA_JSON);
        // Corrupt JSON is skipped silently
        writeVersionJson(gameDir, "corrupt-mod", "{ not valid json");

        List<MinecraftVersion> installed = service.listInstalled(gameDir);

        assertEquals(2, installed.size());
        assertTrue(installed.stream().anyMatch(v ->
                v.id().equals("fabric-loader-0.16.9-1.21.4")));
        assertTrue(installed.stream().anyMatch(v ->
                v.id().equals("neoforge-21.4.147")));
        assertTrue(installed.stream().allMatch(v ->
                v.type() instanceof org.example.launcher.version.ModdedVersionType));
    }

    @Test
    @DisplayName("listInstalled: empty result when versions/ does not exist")
    void listInstalledEmpty(@TempDir Path tempDir) throws IOException {
        List<MinecraftVersion> installed =
                service.listInstalled(new GameDirectory(tempDir));
        assertTrue(installed.isEmpty());
    }

    @Test
    @DisplayName("resolveMetadata: merges local loader JSON with vanilla metadata")
    void resolveMetadataMerges(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);

        VersionMetadata merged = service.resolveMetadata(
                "fabric-loader-0.16.9-1.21.4", gameDir);

        assertEquals("fabric-loader-0.16.9-1.21.4", merged.id());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient",
                merged.mainClass().orElseThrow());
    }

    @Test
    @DisplayName("resolveMetadata: missing local JSON fails")
    void resolveMetadataMissingJson(@TempDir Path tempDir) {
        assertThrows(IOException.class, () ->
                service.resolveMetadata("fabric-loader-0.16.9-1.21.4",
                        new GameDirectory(tempDir)));
    }

    @Test
    @DisplayName("resolveMetadata: unknown vanilla base fails")
    void resolveMetadataUnknownVanilla(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);
        writeVersionJson(gameDir, "fabric-loader-0.16.9-9.9.9", """
                { "id": "fabric-loader-0.16.9-9.9.9", "inheritsFrom": "9.9.9" }
                """);

        IOException e = assertThrows(IOException.class, () ->
                service.resolveMetadata("fabric-loader-0.16.9-9.9.9", gameDir));
        assertTrue(e.getMessage().contains("9.9.9"));
    }

    @Test
    @DisplayName("loaderTypeOf: detects loader families by id convention")
    void loaderTypeDetection() {
        assertEquals(Optional.of(ModLoaderType.FABRIC),
                ModdedVersionService.loaderTypeOf("fabric-loader-0.16.9-1.21.4"));
        assertEquals(Optional.of(ModLoaderType.QUILT),
                ModdedVersionService.loaderTypeOf("quilt-loader-0.26.0-1.21.4"));
        assertEquals(Optional.of(ModLoaderType.FORGE),
                ModdedVersionService.loaderTypeOf("1.20.1-forge-47.4.10"));
        assertEquals(Optional.of(ModLoaderType.NEOFORGE),
                ModdedVersionService.loaderTypeOf("neoforge-21.4.147"));
        assertTrue(ModdedVersionService.loaderTypeOf("1.21.4").isEmpty());
        assertTrue(ModdedVersionService.loaderTypeOf(null).isEmpty());
    }

    @Test
    @DisplayName("listInstalledDetailed: parses family, loader version and base")
    void listInstalledDetailedParsesAllFamilies(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", FABRIC_JSON);
        writeVersionJson(gameDir, "quilt-loader-0.26.0-1.21.4", """
                { "id": "quilt-loader-0.26.0-1.21.4", "inheritsFrom": "1.21.4" }
                """);
        writeVersionJson(gameDir, "1.20.1-forge-47.4.23", """
                { "id": "1.20.1-forge-47.4.23", "inheritsFrom": "1.20.1" }
                """);
        writeVersionJson(gameDir, "neoforge-21.4.147", """
                { "id": "neoforge-21.4.147", "inheritsFrom": "1.21.4" }
                """);

        List<ModdedVersionService.InstalledModdedVersion> detailed =
                service.listInstalledDetailed(gameDir);

        assertEquals(4, detailed.size());
        var fabric = detailed.stream()
                .filter(iv -> iv.loaderType() == ModLoaderType.FABRIC).findFirst().orElseThrow();
        assertEquals("0.16.9", fabric.loaderVersion());
        assertEquals("1.21.4", fabric.minecraftVersion());
        var forge = detailed.stream()
                .filter(iv -> iv.loaderType() == ModLoaderType.FORGE).findFirst().orElseThrow();
        assertEquals("47.4.23", forge.loaderVersion());
        assertEquals("1.20.1", forge.minecraftVersion());
        var neoforge = detailed.stream()
                .filter(iv -> iv.loaderType() == ModLoaderType.NEOFORGE).findFirst().orElseThrow();
        // NeoForge ids do not contain the MC version — the base comes
        // from the JSON's inheritsFrom
        assertEquals("21.4.147", neoforge.loaderVersion());
        assertEquals("1.21.4", neoforge.minecraftVersion());

        // Loader-family version types for the unified browser filters
        assertTrue(detailed.stream().allMatch(iv ->
                iv.version().type() instanceof org.example.launcher.version.ModLoaderFamilyType));
        // Round-trip: the parsed info reproduces the installed version id
        assertTrue(detailed.stream().allMatch(iv ->
                iv.toModLoaderVersion().installedVersionId().equals(iv.version().id())));
    }

    @Test
    @DisplayName("listInstalledDetailed: skips vanilla, unparseable and base-less entries")
    void listInstalledDetailedSkipsInvalid(@TempDir Path tempDir) throws IOException {
        GameDirectory gameDir = new GameDirectory(tempDir);

        // Vanilla install: no loader id pattern
        writeVersionJson(gameDir, "1.21.4", VANILLA_JSON);
        // Loader id but no inheritsFrom → base unknown → skipped
        writeVersionJson(gameDir, "fabric-loader-0.16.9-1.21.4", """
                { "id": "fabric-loader-0.16.9-1.21.4" }
                """);
        // Id does not follow the convention for its stated base →
        // loader version not parseable → skipped
        writeVersionJson(gameDir, "fabric-loader-0.16.9", """
                { "id": "fabric-loader-0.16.9", "inheritsFrom": "1.21.4" }
                """);

        assertTrue(service.listInstalledDetailed(gameDir).isEmpty());
    }

    private static void writeVersionJson(GameDirectory gameDir, String id, String json)
            throws IOException {
        Path jsonFile = gameDir.versionMetadata(id);
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
    }
}
