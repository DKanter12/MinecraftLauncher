package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.example.launcher.model.ModLoaderVersion;

@DisplayName("Forge / NeoForge version providers parsing")
class ForgeNeoForgeVersionProviderTest {

    private static final String FORGE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata>
              <groupId>net.minecraftforge</groupId>
              <artifactId>forge</artifactId>
              <versioning>
                <versions>
                  <version>1.20.1-47.3.0</version>
                  <version>1.20.1-47.3.22</version>
                  <version>1.20.1-47.4.0</version>
                  <version>1.20.1-47.4.10</version>
                  <version>1.21.4-54.0.0</version>
                  <version>1.19.4-45.2.0</version>
                  <version>1.7.10-10.13.4.1614</version>
                  <version>not-a-pair</version>
                </versions>
              </versioning>
            </metadata>
            """;

    private static final String NEOFORGE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata>
              <groupId>net.neoforged</groupId>
              <artifactId>neoforge</artifactId>
              <versioning>
                <versions>
                  <version>20.4.237</version>
                  <version>21.0.167</version>
                  <version>21.4.99</version>
                  <version>21.4.147</version>
                  <version>47.1.104</version>
                  <version>21.5.11-beta</version>
                </versions>
              </versioning>
            </metadata>
            """;

    @Test
    @DisplayName("Forge: filters by MC version, newest first, newest stable, installer URL resolved")
    void forgeParsesAndFilters() {
        ForgeVersionProvider provider = new ForgeVersionProvider(
                ForgeVersionProvider.DEFAULT_METADATA_URL,
                ForgeVersionProvider.INSTALLER_URL_TEMPLATE);

        List<ModLoaderVersion> versions = provider.parseVersions(FORGE_XML, "1.20.1");

        assertEquals(4, versions.size());
        // newest first (metadata is ascending)
        assertEquals("47.4.10", versions.get(0).loaderVersion());
        assertTrue(versions.get(0).stable());
        assertTrue(!versions.get(1).stable());
        assertEquals("47.3.0", versions.get(3).loaderVersion());

        // installer URL from template
        assertEquals("https://maven.minecraftforge.net/net/minecraftforge/forge/"
                        + "1.20.1-47.4.10/forge-1.20.1-47.4.10-installer.jar",
                versions.get(0).installerUrl());

        // version id convention
        assertEquals("1.20.1-forge-47.4.10", versions.get(0).installedVersionId());
    }

    @Test
    @DisplayName("NeoForge: prefix mapping encodes the MC version")
    void neoforgePrefixMapping() {
        assertEquals("47.", NeoForgeVersionProvider.neoforgePrefix("1.20.1"));
        assertEquals("21.0.", NeoForgeVersionProvider.neoforgePrefix("1.21"));
        assertEquals("21.4.", NeoForgeVersionProvider.neoforgePrefix("1.21.4"));
        assertEquals("20.4.", NeoForgeVersionProvider.neoforgePrefix("1.20.4"));
    }

    @Test
    @DisplayName("NeoForge: filters by prefix, newest first, installer URL resolved")
    void neoforgeParsesAndFilters() {
        NeoForgeVersionProvider provider = new NeoForgeVersionProvider(
                NeoForgeVersionProvider.DEFAULT_METADATA_URL,
                NeoForgeVersionProvider.INSTALLER_URL_TEMPLATE);

        List<ModLoaderVersion> versions = provider.parseVersions(NEOFORGE_XML, "1.21.4");

        assertEquals(2, versions.size());
        assertEquals("21.4.147", versions.get(0).loaderVersion());
        assertTrue(versions.get(0).stable());
        assertTrue(!versions.get(1).stable());
        assertEquals("neoforge-21.4.147", versions.get(0).installedVersionId());
        assertEquals("https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                        + "21.4.147/neoforge-21.4.147-installer.jar",
                versions.get(0).installerUrl());
    }

    @Test
    @DisplayName("NeoForge: 1.20.1 uses the legacy 47.x build prefix")
    void neoforgeLegacy1201() {
        NeoForgeVersionProvider provider = new NeoForgeVersionProvider(
                NeoForgeVersionProvider.DEFAULT_METADATA_URL,
                NeoForgeVersionProvider.INSTALLER_URL_TEMPLATE);

        List<ModLoaderVersion> versions = provider.parseVersions(NEOFORGE_XML, "1.20.1");
        assertEquals(1, versions.size());
        assertEquals("47.1.104", versions.get(0).loaderVersion());
    }

    @Test
    @DisplayName("Forge: unsupported MC version yields empty list")
    void forgeUnsupportedIsEmpty() {
        ForgeVersionProvider provider = new ForgeVersionProvider();
        assertTrue(provider.parseVersions(FORGE_XML, "1.6.4").isEmpty());
    }

    @Test
    @DisplayName("Forge: supported MC versions derive from the {mc}-{build} entries")
    void forgeSupportedMinecraftVersions() {
        ForgeVersionProvider provider = new ForgeVersionProvider();
        java.util.Set<String> supported =
                provider.parseSupportedMinecraftVersions(FORGE_XML);

        assertEquals(java.util.Set.of("1.20.1", "1.21.4", "1.19.4", "1.7.10"),
                supported);
    }

    @Test
    @DisplayName("NeoForge: supported MC versions decode from the build numbers")
    void neoforgeSupportedMinecraftVersions() {
        NeoForgeVersionProvider provider = new NeoForgeVersionProvider();
        java.util.Set<String> supported =
                provider.parseSupportedMinecraftVersions(NEOFORGE_XML);

        // 47.1.104 → 1.20.1 (legacy numbering), 21.0.167 → 1.21,
        // 20.4.237 → 1.20.4, 21.4.x → 1.21.4, beta suffix stripped
        assertEquals(java.util.Set.of("1.20.1", "1.21", "1.20.4", "1.21.4",
                        "1.21.5"),
                supported);
    }

    @Test
    @DisplayName("NeoForge: build number ↔ MC version round trip")
    void neoforgeVersionMappingRoundTrip() {
        assertEquals("1.20.1", NeoForgeVersionProvider.minecraftVersionOf("47.1.104"));
        assertEquals("1.21", NeoForgeVersionProvider.minecraftVersionOf("21.0.167"));
        assertEquals("1.21.4", NeoForgeVersionProvider.minecraftVersionOf("21.4.147"));
        assertEquals("1.20.4", NeoForgeVersionProvider.minecraftVersionOf("20.4.237"));
        assertEquals("1.21.5", NeoForgeVersionProvider.minecraftVersionOf("21.5.11-beta"));
        assertEquals("1.20.1", NeoForgeVersionProvider.minecraftVersionOf("20.1.0"));
        assertNull(NeoForgeVersionProvider.minecraftVersionOf("10.0.0"));
        assertNull(NeoForgeVersionProvider.minecraftVersionOf("garbage"));

        // Every prefix mapping decodes back to the same MC version
        for (String mc : List.of("1.20.2", "1.20.4", "1.20.6", "1.21",
                "1.21.1", "1.21.4")) {
            String prefix = NeoForgeVersionProvider.neoforgePrefix(mc);
            String build = prefix + "1";
            assertEquals(mc, NeoForgeVersionProvider.minecraftVersionOf(build),
                    "round trip for " + mc);
        }
    }
}
