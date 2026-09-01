package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchArguments;
import org.example.launcher.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MinecraftLaunchArgumentBuilder")
class MinecraftLaunchArgumentBuilderTest {

    private final MinecraftLaunchArgumentBuilder builder = new MinecraftLaunchArgumentBuilder();
    private final MojangVersionMetadataService metadataService = new MojangVersionMetadataService();

    private static final String MODERN_JSON = """
            {
              "id": "1.21",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "21",
              "assetIndex": {
                "id": "21", "sha1": "abc", "size": 100, "totalSize": 200,
                "url": "https://example.com/21.json"
              },
              "javaVersion": { "component": "java-runtime-gamma", "majorVersion": 21 },
              "downloads": {
                "client": { "sha1": "c1", "size": 999, "url": "https://example.com/client.jar" }
              },
              "libraries": [
                {
                  "name": "com.mojang:logging:1.1.1",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/logging/1.1.1/logging-1.1.1.jar",
                      "sha1": "l1", "size": 100,
                      "url": "https://example.com/logging-1.1.1.jar"
                    }
                  }
                },
                {
                  "name": "org.lwjgl:lwjgl:3.3.1",
                  "downloads": {
                    "artifact": {
                      "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1.jar",
                      "sha1": "l2", "size": 200,
                      "url": "https://example.com/lwjgl-3.3.1.jar"
                    },
                    "classifiers": {
                      "natives-linux": {
                        "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar",
                        "sha1": "nl", "size": 300,
                        "url": "https://example.com/lwjgl-3.3.1-natives-linux.jar"
                      },
                      "natives-windows": {
                        "path": "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-windows.jar",
                        "sha1": "nw", "size": 400,
                        "url": "https://example.com/lwjgl-3.3.1-natives-windows.jar"
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
                  "--gameDir", "${game_directory}",
                  "--assetsDir", "${assets_root}",
                  "--assetIndex", "${assets_index_name}",
                  "--uuid", "${auth_uuid}",
                  "--accessToken", "${auth_access_token}",
                  "--userType", "${user_type}",
                  "--versionType", "${version_type}"
                ],
                "jvm": [
                  "-Djava.library.path=${natives_directory}",
                  "-cp", "${classpath}",
                  "-Dminecraft.launcher.brand=custom"
                ]
              }
            }
            """;

    private static final String LEGACY_JSON = """
            {
              "id": "1.7.10",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "1.7.10",
              "libraries": [
                {
                  "name": "com.mojang:authlib:1.5.21",
                  "url": "https://libraries.minecraft.net/"
                }
              ],
              "minecraftArguments": "--username ${auth_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_directory} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}"
            }
            """;

    private final JavaRuntime sampleRuntime = new JavaRuntime(
            Path.of("/usr/lib/jvm/java-21/bin/java"), 21, JavaRuntime.Source.JAVA_HOME);

    // ==================================================================
    //  Modern version tests
    // ==================================================================

    @Test
    @DisplayName("builds Forge-style module path with ${classpath_separator} replaced")
    void buildsForgeStyleModulePath(@TempDir Path dir) throws IOException {
        // Mirrors Forge 1.20.1's profile JSON: the module path (-p) is
        // assembled from ${library_directory} entries joined by
        // ${classpath_separator} instead of using ${classpath}
        String forgeJson = """
                {
                  "id": "1.20.1-forge-47.4.10",
                  "type": "release",
                  "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                  "assets": "5",
                  "libraries": [],
                  "downloads": {
                    "client": { "sha1": "c1", "size": 999, "url": "https://example.com/client.jar" }
                  },
                  "arguments": {
                    "game": ["--launchTarget", "forgeclient", "--username", "${auth_name}"],
                    "jvm": [
                      "-Djava.library.path=${natives_directory}",
                      "-p",
                      "${library_directory}/cpw/mods/bootstraplauncher/1.1.2/bootstraplauncher-1.1.2.jar${classpath_separator}${library_directory}/cpw/mods/securejarhandler/2.1.10/securejarhandler-2.1.10.jar${classpath_separator}${library_directory}/cpw/mods/JarJarFileSystems/0.3.19/JarJarFileSystems-0.3.19.jar",
                      "--add-modules", "ALL-DEFAULT",
                      "-DignoreList=bootstraplauncher,securejarhandler,asm-commons,asm-util,asm-analysis,asm-tree,asm,JarJarFileSystems,client.jar",
                      "-cp", "${classpath}"
                    ]
                  }
                }
                """;
        VersionMetadata meta = metadataService.parseMetadata(forgeJson,
                "1.20.1-forge-47.4.10");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        List<String> jvm = args.jvmArguments();

        // No unreplaced placeholders anywhere
        assertFalse(jvm.stream().anyMatch(s -> s.contains("${")),
                "No unreplaced placeholders should remain in JVM args: " + jvm);

        // The module path must be split by the platform path separator
        int pIdx = jvm.indexOf("-p");
        assertTrue(pIdx >= 0 && pIdx + 1 < jvm.size(),
                "Expected -p followed by a value");
        String modulePath = jvm.get(pIdx + 1);
        String separator = java.io.File.pathSeparator;

        assertTrue(modulePath.contains(separator),
                "Module path should contain the platform path separator: " + modulePath);
        String[] modules = modulePath.split(java.util.regex.Pattern.quote(separator));
        assertEquals(3, modules.length,
                "Module path should contain 3 jars: " + modulePath);
        assertTrue(modules[0].endsWith("bootstraplauncher-1.1.2.jar"));
        assertTrue(modules[1].endsWith("securejarhandler-2.1.10.jar"));
        assertTrue(modules[2].endsWith("JarJarFileSystems-0.3.19.jar"));

        // Each entry must live inside the libraries directory
        String librariesDir = gameDir.librariesDir().toString();
        for (String m : modules) {
            assertTrue(m.startsWith(librariesDir),
                    "Module should be inside libraries dir: " + m);
        }
    }

    @Test
    @DisplayName("builds with separate runtime directory and extra JVM args (modded profile)")
    void buildsWithRuntimeDirectoryAndExtraJvmArgs(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory storage = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");
        Path runtimeDir = dir.resolve("profiles").resolve("MyPack");

        LaunchArguments args = builder.build(meta, storage, profile, sampleRuntime,
                runtimeDir, List.of("-Xmx4G", "-Dsodium.hideAAA"));

        // The game runs inside the profile's own directory
        assertEquals(runtimeDir, args.workingDirectory());

        // --gameDir points at the runtime directory, not the storage root
        List<String> game = args.gameArguments();
        int gameDirIdx = game.indexOf("--gameDir");
        assertTrue(gameDirIdx >= 0, "game args should contain --gameDir: " + game);
        assertEquals(runtimeDir.toString(), game.get(gameDirIdx + 1));

        // Extra JVM args are appended after the base ones
        List<String> jvm = args.jvmArguments();
        assertTrue(jvm.contains("-Xmx4G"));
        assertTrue(jvm.contains("-Dsodium.hideAAA"));
        assertTrue(jvm.indexOf("-Dminecraft.launcher.brand=custom")
                < jvm.indexOf("-Xmx4G"));

        // Classpath still resolves from the shared storage root
        assertTrue(args.classpath().contains("logging-1.1.1.jar"));
        assertTrue(args.classpath().contains("1.21.jar"));
    }

    @Test
    @DisplayName("builds modern version with all placeholders replaced")
    void buildsModernVersion(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        assertEquals("net.minecraft.client.main.Main", args.mainClass());
        assertEquals(gameDir.root(), args.workingDirectory());

        // JVM args
        List<String> jvm = args.jvmArguments();
        assertTrue(jvm.contains("-cp"));
        assertTrue(jvm.get(0).startsWith("-Djava.library.path="));
        assertTrue(jvm.get(0).contains("natives"));
        assertTrue(jvm.contains("-Dminecraft.launcher.brand=custom"));

        // ${classpath} must be replaced — no literal placeholder should remain
        assertFalse(jvm.stream().anyMatch(s -> s.contains("${")),
                "No unreplaced placeholders should remain in JVM args: " + jvm);

        // The -cp value in JVM args must be the actual classpath
        int cpIdx = jvm.indexOf("-cp");
        assertTrue(cpIdx >= 0 && cpIdx + 1 < jvm.size(),
                "Expected -cp followed by a value in JVM args");
        String cpInJvm = jvm.get(cpIdx + 1);
        assertTrue(cpInJvm.contains("logging-1.1.1.jar"),
                "Classpath in JVM args should contain logging jar: " + cpInJvm);
        assertTrue(cpInJvm.contains("1.21.jar"),
                "Classpath in JVM args should contain client jar: " + cpInJvm);
        assertFalse(cpInJvm.contains("${classpath}"),
                "${classpath} should be replaced, not literal: " + cpInJvm);

        // Classpath field
        String cp = args.classpath();
        assertTrue(cp.contains("logging-1.1.1.jar"));
        assertTrue(cp.contains("lwjgl-3.3.1.jar"));
        assertTrue(cp.contains("1.21.jar"));

        // Native JAR for current OS should be on classpath
        String osName = org.example.launcher.util.OsDetector.mojangName();
        assertTrue(cp.contains("lwjgl-3.3.1-natives-" + osName),
                "Native JAR for " + osName + " should be on classpath: " + cp);

        // Game args
        List<String> game = args.gameArguments();
        assertTrue(game.contains("--username"));
        assertTrue(game.contains("Steve"));
        assertTrue(game.contains("--version"));
        assertTrue(game.contains("1.21"));
        assertTrue(game.contains("--gameDir"));
        assertTrue(game.contains(dir.toString()));
        assertTrue(game.contains("--assetsDir"));
        assertTrue(game.stream().anyMatch(s -> s.contains("assets")));
        assertTrue(game.contains("--assetIndex"));
        assertTrue(game.contains("21"));
        assertTrue(game.contains("--uuid"));
        assertTrue(game.contains("--accessToken"));
        assertTrue(game.contains("offline"));
        assertTrue(game.contains("--userType"));
        assertTrue(game.contains("mojang"));
        assertTrue(game.contains("--versionType"));
        assertTrue(game.contains("release"));
    }

    @Test
    @DisplayName("modern version generates offline UUID for offline profile")
    void modernOfflineUuid(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Alex");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        String uuid = args.gameArguments().stream()
                .filter(s -> s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                .findFirst()
                .orElse(null);

        assertNotNull(uuid, "Should have a UUID in game args");
        assertEquals(MinecraftLaunchArgumentBuilder.generateOfflineUuid("Alex"), uuid);
    }

    @Test
    @DisplayName("modern online profile uses provided UUID and token")
    void modernOnlineProfile(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = new GameProfile("Premium", "12345678-1234-1234-1234-123456789012",
                "my-token", true);

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        List<String> game = args.gameArguments();
        assertTrue(game.contains("Premium"));
        assertTrue(game.contains("12345678-1234-1234-1234-123456789012"));
        assertTrue(game.contains("my-token"));
    }

    @Test
    @DisplayName("fullCommand assembles java + jvmArgs + mainClass + gameArgs")
    void fullCommand(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        List<String> cmd = args.fullCommand();
        assertEquals(sampleRuntime.javaExecutable().toString(), cmd.get(0));
        assertTrue(cmd.contains("net.minecraft.client.main.Main"));
        int mainIdx = cmd.indexOf("net.minecraft.client.main.Main");
        assertTrue(mainIdx > 0);
        assertTrue(cmd.subList(0, mainIdx).contains("-cp"));

        // The classpath value after -cp must contain real paths, not ${classpath}
        int cpIdx = cmd.indexOf("-cp");
        String cpVal = cmd.get(cpIdx + 1);
        assertTrue(cpVal.contains("logging-1.1.1.jar"),
                "Classpath in fullCommand should have real paths: " + cpVal);
        assertFalse(cpVal.contains("${"),
                "No unreplaced placeholders in classpath: " + cpVal);

        assertTrue(cmd.subList(mainIdx + 1, cmd.size()).contains("--username"));
    }

    // ==================================================================
    //  Legacy version tests
    // ==================================================================

    @Test
    @DisplayName("builds legacy version with default JVM args")
    void buildsLegacyVersion(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(LEGACY_JSON, "1.7.10");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        // JVM args should include -Djava.library.path and -cp
        List<String> jvm = args.jvmArguments();
        assertTrue(jvm.stream().anyMatch(a -> a.startsWith("-Djava.library.path=")));
        assertTrue(jvm.contains("-cp"));

        // Game args from legacy string
        List<String> game = args.gameArguments();
        assertTrue(game.contains("--username"));
        assertTrue(game.contains("Steve"));
        assertTrue(game.contains("--version"));
        assertTrue(game.contains("1.7.10"));
        assertTrue(game.contains("--gameDir"));
        assertTrue(game.contains("--assetsDir"));
        assertTrue(game.contains("--uuid"));
        assertTrue(game.contains("--accessToken"));
        assertTrue(game.contains("offline"));
        assertTrue(game.contains("--userProperties"));
        assertTrue(game.contains("{}"));
        assertTrue(game.contains("--userType"));
        assertTrue(game.contains("mojang"));
    }

    @Test
    @DisplayName("legacy version classpath includes libraries and client jar")
    void legacyClasspath(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(LEGACY_JSON, "1.7.10");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        String cp = args.classpath();
        assertTrue(cp.contains("authlib-1.5.21.jar"));
        assertTrue(cp.contains("1.7.10.jar"));
    }

    // ==================================================================
    //  Edge case tests
    // ==================================================================

    @Test
    @DisplayName("no-placeholder JVM args pass through unchanged")
    void noPlaceholderJvmArgs(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        assertTrue(args.jvmArguments().contains("-Dminecraft.launcher.brand=custom"));
    }

    @Test
    @DisplayName("classpath entries are unique (no duplicates)")
    void classpathUnique(@TempDir Path dir) throws IOException {
        VersionMetadata meta = metadataService.parseMetadata(MODERN_JSON, "1.21");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        String[] entries = args.classpath().split(java.io.File.pathSeparator);
        long unique = java.util.Arrays.stream(entries).distinct().count();
        assertEquals(entries.length, unique, "Classpath should have no duplicate entries");
    }

    @Test
    @DisplayName("version type defaults to release when absent")
    void versionTypeDefault(@TempDir Path dir) throws IOException {
        String json = """
                {
                  "id": "test",
                  "mainClass": "Main",
                  "libraries": [],
                  "arguments": {
                    "game": ["--versionType", "${version_type}"],
                    "jvm": ["-cp", "${classpath}"]
                  }
                }
                """;
        VersionMetadata meta = metadataService.parseMetadata(json, "test");
        GameDirectory gameDir = new GameDirectory(dir);
        GameProfile profile = GameProfile.offline("Steve");

        LaunchArguments args = builder.build(meta, gameDir, profile, sampleRuntime);

        assertTrue(args.gameArguments().contains("release"));
    }
}
