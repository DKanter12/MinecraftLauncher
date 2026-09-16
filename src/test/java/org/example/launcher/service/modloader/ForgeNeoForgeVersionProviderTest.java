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
                  <version>26.1.0.0-alpha.1+snapshot-1</version>
                  <version>26.1.0.19-beta</version>
                  <version>26.1.1.5-beta</version>
                  <version>26.1.2.101</version>
                  <version>26.2.0.3-beta</version>
                  <version>0.25w14craftmine.3-beta</version>
                  <version>0.25w14craftmine.4-beta</version>
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
    @DisplayName("NeoForge: filters by exact decoded MC version, newest first, installer URL resolved")
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
    @DisplayName("NeoForge: 1.20.1 resolves the legacy 47.x build")
    void neoforgeLegacy1201() {
        NeoForgeVersionProvider provider = new NeoForgeVersionProvider(
                NeoForgeVersionProvider.DEFAULT_METADATA_URL,
                NeoForgeVersionProvider.INSTALLER_URL_TEMPLATE);

        List<ModLoaderVersion> versions = provider.parseVersions(NEOFORGE_XML, "1.20.1");
        assertEquals(1, versions.size());
        assertEquals("47.1.104", versions.get(0).loaderVersion());
    }

    @Test
    @DisplayName("NeoForge: modern 26.x builds filter by their own MC version only")
    void neoforgeModernVersionFiltering() {
        NeoForgeVersionProvider provider = new NeoForgeVersionProvider(
                NeoForgeVersionProvider.DEFAULT_METADATA_URL,
                NeoForgeVersionProvider.INSTALLER_URL_TEMPLATE);

        // MC 26.1 owns only its 26.1.0.x builds — the 26.1.1.x and
        // 26.1.2.x builds belong to MC 26.1.1 / 26.1.2 and must not
        // leak into the list
        List<ModLoaderVersion> versions = provider.parseVersions(NEOFORGE_XML, "26.1");
        assertEquals(2, versions.size());
        assertEquals("26.1.0.19-beta", versions.get(0).loaderVersion());
        assertTrue(versions.get(0).stable());
        assertEquals("26.1.0.0-alpha.1+snapshot-1", versions.get(1).loaderVersion());
        assertTrue(!versions.get(1).stable());
        assertEquals("neoforge-26.1.0.19-beta", versions.get(0).installedVersionId());
        assertEquals("https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                        + "26.1.0.19-beta/neoforge-26.1.0.19-beta-installer.jar",
                versions.get(0).installerUrl());

        assertEquals(1, provider.parseVersions(NEOFORGE_XML, "26.1.2").size());
        assertEquals(1, provider.parseVersions(NEOFORGE_XML, "26.2").size());
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
        // 20.4.237 → 1.20.4, 21.4.x → 1.21.4, 21.5.11-beta → 1.21.5;
        // modern builds: 26.1.0.x → 26.1 (incl. the +snapshot alpha),
        // 26.1.1.x → 26.1.1, 26.1.2.101 → 26.1.2, 26.2.0.x → 26.2;
        // the odd 25w14craftmine builds decode to nothing
        assertEquals(java.util.Set.of("1.20.1", "1.21", "1.20.4", "1.21.4",
                        "1.21.5", "26.1", "26.1.1", "26.1.2", "26.2"),
                supported);
    }

    @Test
    @DisplayName("NeoForge: build number → MC version mapping, both numbering schemes")
    void neoforgeVersionMapping() {
        // Legacy 3-component scheme (MC 1.20–1.21 era)
        assertEquals("1.20.1", NeoForgeVersionProvider.minecraftVersionOf("47.1.104"));
        assertEquals("1.20.1", NeoForgeVersionProvider.minecraftVersionOf("20.1.0"));
        assertEquals("1.20.2", NeoForgeVersionProvider.minecraftVersionOf("20.2.88-beta"));
        assertEquals("1.20.4", NeoForgeVersionProvider.minecraftVersionOf("20.4.237"));
        assertEquals("1.21", NeoForgeVersionProvider.minecraftVersionOf("21.0.167"));
        assertEquals("1.21.4", NeoForgeVersionProvider.minecraftVersionOf("21.4.147"));
        assertEquals("1.21.5", NeoForgeVersionProvider.minecraftVersionOf("21.5.11-beta"));
        assertEquals("1.21.11", NeoForgeVersionProvider.minecraftVersionOf("21.11.7"));

        // Modern scheme (MC 26.x era, no "1." prefix): every component
        // but the last (the build) forms the MC version
        assertEquals("26.1", NeoForgeVersionProvider.minecraftVersionOf("26.1.0.19-beta"));
        assertEquals("26.1", NeoForgeVersionProvider.minecraftVersionOf(
                "26.1.0.0-alpha.1+snapshot-1"));
        assertEquals("26.1.1", NeoForgeVersionProvider.minecraftVersionOf("26.1.1.5-beta"));
        assertEquals("26.1.2", NeoForgeVersionProvider.minecraftVersionOf("26.1.2.101"));
        assertEquals("26.2", NeoForgeVersionProvider.minecraftVersionOf("26.2.0.3"));
        assertEquals("26.2", NeoForgeVersionProvider.minecraftVersionOf("26.2.5"));

        // Unknown and special builds decode to nothing
        assertNull(NeoForgeVersionProvider.minecraftVersionOf("26.1"));
        assertNull(NeoForgeVersionProvider.minecraftVersionOf("10.0.0"));
        assertNull(NeoForgeVersionProvider.minecraftVersionOf("garbage"));
        // Special builds that appear in the real Maven metadata must
        // not crash the decoder (NumberFormatException broke the whole
        // supported-set fetch and removed every NeoForge variant)
        assertNull(NeoForgeVersionProvider.minecraftVersionOf(
                "0.25w14craftmine.3-beta"));
    }
}
