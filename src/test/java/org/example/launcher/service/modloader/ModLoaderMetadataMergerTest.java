package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;

@DisplayName("ModLoaderMetadataMerger")
class ModLoaderMetadataMergerTest {

    private final MojangVersionMetadataService parser = new MojangVersionMetadataService();
    private final ModLoaderMetadataMerger merger = new ModLoaderMetadataMerger(parser);

    private static final String VANILLA_JSON = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "24",
              "assetIndex": {
                "id": "24",
                "sha1": "index_sha",
                "size": 300,
                "totalSize": 500000,
                "url": "https://launchermeta.mojang.com/v1/packages/index_sha/24.json"
              },
              "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
              "downloads": {
                "client": {
                  "sha1": "client_sha",
                  "size": 25000000,
                  "url": "https://piston-data.mojang.com/v1/objects/client/client.jar"
                }
              },
              "libraries": [
                { "name": "com.mojang:logging:1.1.1",
                  "downloads": { "artifact": { "path": "com/mojang/logging/1.1.1/logging-1.1.1.jar", "sha1": "log_sha", "size": 100, "url": "https://libraries.minecraft.net/com/mojang/logging/1.1.1/logging-1.1.1.jar" } } },
                { "name": "org.lwjgl:lwjgl:3.3.3",
                  "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", "sha1": "lwjgl_sha", "size": 200, "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar" } } }
              ],
              "arguments": {
                "game": ["--username", "${auth_player_name}", "--version", "${version_name}"],
                "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"]
              }
            }
            """;

    /**
     * Realistic Fabric profile: carries id, mainClass and libraries,
     * but EMPTY argument lists — vanilla arguments are used as-is.
     */
    private static final String FABRIC_JSON = """
            {
              "id": "fabric-loader-0.16.9-1.21.4",
              "inheritsFrom": "1.21.4",
              "type": "release",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "libraries": [
                { "name": "net.fabricmc:fabric-loader:0.16.9",
                  "url": "https://maven.fabricmc.net/",
                  "sha1": "fabric_sha",
                  "size": 123456 },
                { "name": "org.lwjgl:lwjgl:3.3.3",
                  "url": "https://maven.fabricmc.net/",
                  "sha1": "patched_lwjgl_sha",
                  "size": 222 }
              ],
              "arguments": { "game": [], "jvm": [] }
            }
            """;

    /**
     * Realistic Forge profile (47.4.x for 1.20.1): only its OWN
     * arguments — fml game args and module-path jvm args. It relies on
     * the vanilla -cp/${classpath} and --username arguments being
     * concatenated by the launcher (official inheritsFrom semantics).
     */
    private static final String FORGE_JSON = """
            {
              "id": "1.20.1-forge-47.4.23",
              "inheritsFrom": "1.20.1",
              "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
              "libraries": [],
              "arguments": {
                "game": [
                  "--launchTarget", "forgeclient",
                  "--fml.forgeVersion", "47.4.23",
                  "--fml.mcVersion", "1.20.1",
                  "--fml.forgeGroup", "net.minecraftforge",
                  "--fml.mcpVersion", "20230612.114412"
                ],
                "jvm": [
                  "-DignoreList=bootstraplauncher,securejarhandler,${version_name}.jar",
                  "-DlibraryDirectory=${library_directory}",
                  "-p",
                  "${library_directory}/cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar${classpath_separator}${library_directory}/cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar",
                  "--add-modules", "ALL-MODULE-PATH"
                ]
              }
            }
            """;

    private VersionMetadata mergeVanillaWith(String loaderJson) throws IOException {
        return merger.merge(parser.parseMetadata(VANILLA_JSON, "1.21.4"), loaderJson);
    }

    @Test
    @DisplayName("fabric profile: loader id and mainClass, vanilla arguments kept")
    void fabricProfileKeepsVanillaArguments() throws IOException {
        VersionMetadata merged = mergeVanillaWith(FABRIC_JSON);

        assertEquals("fabric-loader-0.16.9-1.21.4", merged.id());
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient",
                merged.mainClass().orElseThrow());

        // Fabric carries empty argument lists → vanilla's are used as-is
        assertEquals(List.of("--username", "${auth_player_name}",
                "--version", "${version_name}"), merged.gameArguments());
        assertEquals(List.of("-Djava.library.path=${natives_directory}",
                "-cp", "${classpath}"), merged.jvmArguments());
    }

    @Test
    @DisplayName("forge profile: loader arguments are appended to vanilla's")
    void forgeConcatenatesArguments() throws IOException {
        VersionMetadata merged = mergeVanillaWith(FORGE_JSON);

        assertEquals("1.20.1-forge-47.4.23", merged.id());
        assertEquals("cpw.mods.bootstraplauncher.BootstrapLauncher",
                merged.mainClass().orElseThrow());

        // Game args: vanilla first (4), forge's fml args appended (10)
        List<String> game = merged.gameArguments();
        assertEquals(14, game.size());
        assertEquals("--username", game.get(0));
        assertEquals("${auth_player_name}", game.get(1));
        assertEquals("--launchTarget", game.get(4));
        assertEquals("forgeclient", game.get(5));
        assertEquals("--fml.forgeVersion", game.get(6));
        assertEquals("47.4.23", game.get(7));

        // JVM args: vanilla -cp entry must survive (BootstrapLauncher
        // reads the legacy class path from it), forge's -p appended
        List<String> jvm = merged.jvmArguments();
        assertTrue(jvm.contains("-cp"),
                "Vanilla -cp must survive the merge: " + jvm);
        assertTrue(jvm.contains("${classpath}"));
        assertTrue(jvm.contains("-p"));
        assertTrue(jvm.contains("--add-modules"));
        assertTrue(jvm.contains("ALL-MODULE-PATH"));
        assertTrue(jvm.contains("-DlibraryDirectory=${library_directory}"));

        // Order: vanilla -cp before forge -p
        assertTrue(jvm.indexOf("-cp") < jvm.indexOf("-p"),
                "Vanilla args must come first: " + jvm);
        assertEquals("-Djava.library.path=${natives_directory}", jvm.get(0));
    }

    @Test
    @DisplayName("merge: loader libraries come first, name conflicts resolved to loader's")
    void librariesMergedLoaderFirst() throws IOException {
        VersionMetadata merged = mergeVanillaWith(FABRIC_JSON);

        List<Library> libs = merged.libraries();
        assertEquals(3, libs.size());

        // Loader libraries first
        assertEquals("net.fabricmc:fabric-loader:0.16.9", libs.get(0).name());
        assertEquals("org.lwjgl:lwjgl:3.3.3", libs.get(1).name());

        // Conflict on lwjgl resolved to the loader's version
        assertEquals("patched_lwjgl_sha",
                libs.get(1).artifact().orElseThrow().sha1().orElseThrow());

        // Vanilla-only library appended
        assertEquals("com.mojang:logging:1.1.1", libs.get(2).name());
    }

    @Test
    @DisplayName("merge: vanilla fields fill in what the loader omits")
    void vanillaFallbacks() throws IOException {
        VersionMetadata merged = mergeVanillaWith(FABRIC_JSON);

        assertEquals("24", merged.assets().orElseThrow());
        assertEquals("24", merged.assetIndex().orElseThrow().id());
        assertEquals(21, merged.javaVersion().orElseThrow().majorVersion());
        assertEquals("https://piston-data.mojang.com/v1/objects/client/client.jar",
                merged.clientDownload().orElseThrow().url());
    }

    @Test
    @DisplayName("merge: fabric-style top-level sha1/size are parsed for downloads")
    void fabricStyleLibraryHashes() throws IOException {
        VersionMetadata merged = mergeVanillaWith(FABRIC_JSON);

        var artifact = merged.libraries().get(0).artifact().orElseThrow();
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/"
                + "0.16.9/fabric-loader-0.16.9.jar", artifact.url());
        assertEquals("fabric_sha", artifact.sha1().orElseThrow());
        assertEquals(123456, artifact.size());
    }
}
