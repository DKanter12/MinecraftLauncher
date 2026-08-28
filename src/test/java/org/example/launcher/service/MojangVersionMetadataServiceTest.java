package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

@DisplayName("MojangVersionMetadataService parsing")
class MojangVersionMetadataServiceTest {

    private final MojangVersionMetadataService service = new MojangVersionMetadataService();

    private static final String MODERN_VERSION_JSON = """
            {
              "id": "1.21",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "21",
              "assetIndex": {
                "id": "21",
                "sha1": "abc123",
                "size": 1500,
                "totalSize": 500000000,
                "url": "https://launchermeta.mojang.com/v1/packages/abc/21.json"
              },
              "javaVersion": {
                "component": "java-runtime-gamma",
                "majorVersion": 21
              },
              "downloads": {
                "client": {
                  "sha1": "client_sha1_hash",
                  "size": 23456789,
                  "url": "https://piston-data.mojang.com/v1/objects/client_hash/client.jar"
                }
              },
              "libraries": [
                {
                  "name": "com.mojang:logging:1.1.1",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/logging/1.1.1/logging-1.1.1.jar",
                      "sha1": "lib1_sha",
                      "size": 12345,
                      "url": "https://libraries.minecraft.net/com/mojang/logging/1.1.1/logging-1.1.1.jar"
                    }
                  }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.3.1",
                  "downloads": {
                    "artifact": {
                      "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar",
                      "sha1": "lwjgl_sha",
                      "size": 54321,
                      "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar"
                    },
                    "classifiers": {
                      "natives-linux": {
                        "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar",
                        "sha1": "natives_linux_sha",
                        "size": 5678,
                        "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar"
                      },
                      "natives-windows": {
                        "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar",
                        "sha1": "natives_win_sha",
                        "size": 6789,
                        "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar"
                      }
                    }
                  },
                  "natives": {
                    "linux": "natives-linux",
                    "windows": "natives-windows"
                  }
                }
              ],
              "arguments": {
                "game": [
                  "--username", "${auth_name}",
                  "--version", "${version_name}",
                  {
                    "value": "--demo",
                    "rules": [{"action": "allow", "features": {"is_demo_user": true}}]
                  }
                ],
                "jvm": [
                  "-Djava.library.path=${natives_directory}",
                  "-cp", "${classpath}"
                ]
              }
            }
            """;

    private static final String LEGACY_VERSION_JSON = """
            {
              "id": "1.8.9",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "1.8",
              "minecraftArguments": "--username ${auth_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_directory} --assetIndex 1.8 --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties {} --userType mojang",
              "libraries": [
                {
                  "name": "com.mojang:netty:1.8.9",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/netty/1.8.9/netty-1.8.9.jar",
                      "sha1": "legacy_sha",
                      "size": 1000,
                      "url": "https://libraries.minecraft.net/com/mojang/netty/1.8.9/netty-1.8.9.jar"
                    }
                  }
                }
              ]
            }
            """;

    // ==================================================================
    //  Modern version tests
    // ==================================================================

    @Test
    @DisplayName("parses id, type and mainClass")
    void parsesBasicFields() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        assertEquals("1.21", meta.id());
        assertEquals("release", meta.type().orElse(""));
        assertEquals("net.minecraft.client.main.Main", meta.mainClass().orElse(""));
    }

    @Test
    @DisplayName("parses client JAR download URL, sha1 and size")
    void parsesClientDownload() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        DownloadInfo client = meta.clientDownload().orElseThrow();
        assertEquals("https://piston-data.mojang.com/v1/objects/client_hash/client.jar", client.url());
        assertEquals("client_sha1_hash", client.sha1().orElse(""));
        assertEquals(23456789L, client.size());
    }

    @Test
    @DisplayName("parses asset index")
    void parsesAssetIndex() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        AssetIndex ai = meta.assetIndex().orElseThrow();
        assertEquals("21", ai.id());
        assertEquals("https://launchermeta.mojang.com/v1/packages/abc/21.json", ai.url());
        assertEquals(500000000L, ai.totalSize());
        assertEquals(1500L, ai.size());
    }

    @Test
    @DisplayName("parses Java version")
    void parsesJavaVersion() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        JavaVersion jv = meta.javaVersion().orElseThrow();
        assertEquals(21, jv.majorVersion());
        assertEquals("java-runtime-gamma", jv.component());
    }

    @Test
    @DisplayName("parses libraries count")
    void parsesLibrariesCount() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        assertEquals(2, meta.libraries().size());
    }

    @Test
    @DisplayName("parses library artifact info")
    void parsesLibraryArtifact() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        Library lib = meta.libraries().get(0);
        assertEquals("com.mojang:logging:1.1.1", lib.name());
        DownloadInfo art = lib.artifact().orElseThrow();
        assertEquals("https://libraries.minecraft.net/com/mojang/logging/1.1.1/logging-1.1.1.jar", art.url());
        assertEquals(12345L, art.size());
        assertTrue(art.path().isPresent());
    }

    @Test
    @DisplayName("parses native classifiers and natives map")
    void parsesNatives() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        Library lwjgl = meta.libraries().get(1);
        assertTrue(lwjgl.hasNatives());

        Optional<DownloadInfo> winNative = lwjgl.nativeDownload("windows");
        assertTrue(winNative.isPresent());
        assertEquals("https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar",
                winNative.get().url());

        Optional<DownloadInfo> linuxNative = lwjgl.nativeDownload("linux");
        assertTrue(linuxNative.isPresent());

        assertFalse(lwjgl.nativeDownload("osx").isPresent());
    }

    @Test
    @DisplayName("nativeLibraries filters by OS")
    void nativeLibrariesFiltersByOs() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        List<Library> winNatives = meta.nativeLibraries("windows");
        assertEquals(1, winNatives.size());
        assertEquals("org.lwjgl:lwjgl:3.3.1", winNatives.get(0).name());
    }

    @Test
    @DisplayName("parses structured game arguments")
    void parsesGameArguments() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        List<String> gameArgs = meta.gameArguments();
        assertTrue(gameArgs.contains("--username"));
        assertTrue(gameArgs.contains("${auth_name}"));
        assertTrue(gameArgs.contains("--version"));
        assertFalse(gameArgs.contains("--demo"));
    }

    @Test
    @DisplayName("parses structured JVM arguments")
    void parsesJvmArguments() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        List<String> jvmArgs = meta.jvmArguments();
        assertTrue(jvmArgs.contains("-Djava.library.path=${natives_directory}"));
        assertTrue(jvmArgs.contains("-cp"));
        assertTrue(jvmArgs.contains("${classpath}"));
    }

    @Test
    @DisplayName("has structured arguments for modern version")
    void hasStructuredArguments() throws IOException {
        VersionMetadata meta = service.parseMetadata(MODERN_VERSION_JSON, "fallback");
        assertTrue(meta.hasStructuredArguments());
        assertTrue(meta.legacyMinecraftArguments().isEmpty());
    }

    // ==================================================================
    //  Legacy version tests
    // ==================================================================

    @Test
    @DisplayName("parses legacy minecraftArguments string")
    void parsesLegacyArguments() throws IOException {
        VersionMetadata meta = service.parseMetadata(LEGACY_VERSION_JSON, "fallback");
        assertFalse(meta.legacyMinecraftArguments().isEmpty());
        String args = meta.legacyMinecraftArguments().orElse("");
        assertTrue(args.contains("--username ${auth_name}"));
        assertTrue(args.contains("--gameDir ${game_directory}"));
    }

    @Test
    @DisplayName("legacy version has no structured arguments")
    void legacyHasNoStructuredArguments() throws IOException {
        VersionMetadata meta = service.parseMetadata(LEGACY_VERSION_JSON, "fallback");
        assertFalse(meta.hasStructuredArguments());
        assertTrue(meta.gameArguments().isEmpty());
        assertTrue(meta.jvmArguments().isEmpty());
    }

    @Test
    @DisplayName("legacy version has no javaVersion or assetIndex")
    void legacyMissingFields() throws IOException {
        VersionMetadata meta = service.parseMetadata(LEGACY_VERSION_JSON, "fallback");
        assertTrue(meta.javaVersion().isEmpty());
        assertTrue(meta.assetIndex().isEmpty());
        assertTrue(meta.clientDownload().isEmpty());
    }

    @Test
    @DisplayName("legacy version still parses libraries")
    void legacyParsesLibraries() throws IOException {
        VersionMetadata meta = service.parseMetadata(LEGACY_VERSION_JSON, "fallback");
        assertEquals(1, meta.libraries().size());
        assertEquals("com.mojang:netty:1.8.9", meta.libraries().get(0).name());
    }

    // ==================================================================
    //  Error handling tests
    // ==================================================================

    @Test
    @DisplayName("throws on invalid JSON")
    void throwsOnInvalidJson() {
        assertThrows(IOException.class, () -> service.parseMetadata("{ broken", "fallback"));
    }

    @Test
    @DisplayName("throws on empty JSON")
    void throwsOnEmptyJson() {
        assertThrows(IOException.class, () -> service.parseMetadata("", "fallback"));
    }

    @Test
    @DisplayName("uses fallback id when JSON has no id")
    void usesFallbackId() throws IOException {
        VersionMetadata meta = service.parseMetadata("{}", "fallback-id");
        assertEquals("fallback-id", meta.id());
    }

    @Test
    @DisplayName("handles version with minimal fields")
    void handlesMinimalVersion() throws IOException {
        String json = """
                { "id": "minimal", "type": "snapshot" }
                """;
        VersionMetadata meta = service.parseMetadata(json, "fallback");
        assertEquals("minimal", meta.id());
        assertEquals("snapshot", meta.type().orElse(""));
        assertTrue(meta.libraries().isEmpty());
        assertTrue(meta.clientDownload().isEmpty());
        assertTrue(meta.mainClass().isEmpty());
    }

    @Test
    @DisplayName("parses legacy libraries with root url instead of downloads field")
    void parsesLegacyLibrariesWithRootUrl() throws IOException {
        String json = """
                {
                  "id": "1.7.10",
                  "type": "release",
                  "mainClass": "net.minecraft.client.main.Main",
                  "libraries": [
                    {
                      "name": "com.mojang:authlib:1.5.21",
                      "url": "https://libraries.minecraft.net/"
                    },
                    {
                      "name": "org.lwjgl.lwjgl:lwjgl:2.9.1",
                      "url": "https://libraries.minecraft.net/"
                    }
                  ]
                }
                """;
        VersionMetadata meta = service.parseMetadata(json, "fallback");

        assertEquals(2, meta.libraries().size());

        Library lib1 = meta.libraries().get(0);
        assertTrue(lib1.artifact().isPresent());
        DownloadInfo art1 = lib1.artifact().get();
        assertTrue(art1.path().get().contains("com/mojang/authlib/1.5.21/authlib-1.5.21.jar"));
        assertEquals("https://libraries.minecraft.net/com/mojang/authlib/1.5.21/authlib-1.5.21.jar",
                art1.url());

        Library lib2 = meta.libraries().get(1);
        assertTrue(lib2.artifact().isPresent());
        assertTrue(lib2.artifact().get().path().get().contains("lwjgl-2.9.1.jar"));
        assertEquals("https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.1/lwjgl-2.9.1.jar",
                lib2.artifact().get().url());
    }

    @Test
    @DisplayName("legacy library URL without trailing slash is handled")
    void legacyLibraryUrlWithoutTrailingSlash() throws IOException {
        String json = """
                {
                  "id": "test",
                  "libraries": [
                    {
                      "name": "com.mojang:authlib:1.5.21",
                      "url": "https://libraries.minecraft.net"
                    }
                  ]
                }
                """;
        VersionMetadata meta = service.parseMetadata(json, "test");
        DownloadInfo art = meta.libraries().get(0).artifact().orElseThrow();
        assertEquals("https://libraries.minecraft.net/com/mojang/authlib/1.5.21/authlib-1.5.21.jar",
                art.url());
    }

    @Test
    @DisplayName("OS-conditional arguments are filtered by current OS")
    void osConditionalArgumentsFiltered() throws IOException {
        String json = """
                {
                  "id": "test",
                  "mainClass": "Main",
                  "libraries": [],
                  "arguments": {
                    "game": [
                      "--always-here",
                      {
                        "value": "--windows-only",
                        "rules": [{"action": "allow", "os": {"name": "windows"}}]
                      },
                      {
                        "value": "--linux-only",
                        "rules": [{"action": "allow", "os": {"name": "linux"}}]
                      },
                      {
                        "value": "--demo-only",
                        "rules": [{"action": "allow", "features": {"is_demo_user": true}}]
                      }
                    ],
                    "jvm": ["-cp", "${classpath}"]
                  }
                }
                """;
        VersionMetadata meta = service.parseMetadata(json, "test");
        List<String> gameArgs = meta.gameArguments();

        assertTrue(gameArgs.contains("--always-here"));
        assertFalse(gameArgs.contains("--demo-only"));

        String currentOs = org.example.launcher.util.OsDetector.mojangName();
        if ("windows".equals(currentOs)) {
            assertTrue(gameArgs.contains("--windows-only"));
            assertFalse(gameArgs.contains("--linux-only"));
        } else if ("linux".equals(currentOs)) {
            assertTrue(gameArgs.contains("--linux-only"));
            assertFalse(gameArgs.contains("--windows-only"));
        }
    }
}
