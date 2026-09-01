package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.example.launcher.model.ModLoaderVersion;

@DisplayName("Fabric / Quilt version providers parsing")
class FabricQuiltVersionProviderTest {

    private static final String META_JSON = """
            [
              {
                "loader": { "version": "0.16.14", "stable": true, "build": 11 },
                "intermediary": { "version": "1.21.4", "stable": true }
              },
              {
                "loader": { "version": "0.16.13-beta.1", "stable": false, "build": 10 },
                "intermediary": { "version": "1.21.4", "stable": true }
              },
              {
                "broken": true
              }
            ]
            """;

    @Test
    @DisplayName("Fabric: parses loader entries, keeps stability flags, skips malformed")
    void fabricParsesEntries() throws IOException {
        FabricVersionProvider provider = new FabricVersionProvider();
        List<ModLoaderVersion> versions = provider.parseVersions(META_JSON, "1.21.4");

        assertEquals(2, versions.size());
        assertEquals(ModLoaderType.FABRIC, versions.get(0).loaderType());
        assertEquals("0.16.14", versions.get(0).loaderVersion());
        assertTrue(versions.get(0).stable());
        assertEquals("1.21.4", versions.get(0).minecraftVersion());
        assertEquals("fabric-loader-0.16.14-1.21.4",
                versions.get(0).installedVersionId());

        assertEquals("0.16.13-beta.1", versions.get(1).loaderVersion());
        assertTrue(!versions.get(1).stable());
    }

    @Test
    @DisplayName("Quilt: same response shape is parsed identically")
    void quiltParsesEntries() throws IOException {
        QuiltVersionProvider provider = new QuiltVersionProvider();
        List<ModLoaderVersion> versions = provider.parseVersions(META_JSON, "1.21.4");

        assertEquals(2, versions.size());
        assertEquals(ModLoaderType.QUILT, versions.get(0).loaderType());
        assertEquals("0.16.14", versions.get(0).loaderVersion());
        assertEquals("quilt-loader-0.16.14-1.21.4",
                versions.get(0).installedVersionId());
    }

    @Test
    @DisplayName("Fabric: non-array JSON is rejected")
    void fabricRejectsNonArray() {
        FabricVersionProvider provider = new FabricVersionProvider();
        assertThrows(IOException.class,
                () -> provider.parseVersions("{\"not\":\"an array\"}", "1.21.4"));
    }

    @Test
    @DisplayName("Fabric: empty list means MC version unsupported")
    void fabricEmptyList() throws IOException {
        FabricVersionProvider provider = new FabricVersionProvider();
        List<ModLoaderVersion> versions = provider.parseVersions("[]", "1.21.4");
        assertTrue(versions.isEmpty());
    }

    private static final String GAME_VERSIONS_JSON = """
            [
              { "version": "1.21.4", "stable": true },
              { "version": "1.14", "stable": true },
              { "version": "24w34a", "stable": false },
              { "broken": true }
            ]
            """;

    @Test
    @DisplayName("Fabric: game version list yields the supported set")
    void fabricParsesGameVersions() throws IOException {
        FabricVersionProvider provider = new FabricVersionProvider();
        java.util.Set<String> supported = provider.parseGameVersions(GAME_VERSIONS_JSON);

        assertEquals(java.util.Set.of("1.21.4", "1.14", "24w34a"), supported);
    }

    @Test
    @DisplayName("Quilt: game version list yields the supported set")
    void quiltParsesGameVersions() throws IOException {
        QuiltVersionProvider provider = new QuiltVersionProvider();
        java.util.Set<String> supported = provider.parseGameVersions(GAME_VERSIONS_JSON);

        assertEquals(java.util.Set.of("1.21.4", "1.14", "24w34a"), supported);
    }

    @Test
    @DisplayName("Fabric: game version list must be an array")
    void fabricGameVersionsRejectsNonArray() {
        FabricVersionProvider provider = new FabricVersionProvider();
        assertThrows(IOException.class,
                () -> provider.parseGameVersions("{\"not\":\"an array\"}"));
    }
}
