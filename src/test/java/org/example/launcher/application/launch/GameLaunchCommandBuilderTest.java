package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.application.java.JavaManager;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;
import org.example.launcher.infrastructure.mojang.MojangVersionMetadataService;

@DisplayName("GameLaunchCommandBuilder")
class GameLaunchCommandBuilderTest {

    private static final String VERSION_JSON = """
            {
              "id": "1.21",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "assets": "21",
              "assetIndex": {
                "id": "21", "sha1": "abc", "size": 100, "totalSize": 200,
                "url": "https://example.com/21.json"
              },
              "downloads": {
                "client": { "sha1": "c1", "size": 999, "url": "https://example.com/client.jar" }
              },
              "libraries": [],
              "arguments": {
                "game": ["--username", "${auth_player_name}"],
                "jvm": ["-Djava.library.path=${natives_directory}"]
              }
            }
            """;

    private static JavaResolutionService foundService(JavaRuntime rt) {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata meta) {
                return JavaResolutionResult.found(rt);
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return JavaResolutionResult.found(rt);
            }
        };
    }

    private static JavaRuntimeInstaller failingInstaller() {
        return new JavaRuntimeInstaller() {
            @Override
            public JavaRuntime install(String component, Path targetDir) {
                throw new UnsupportedOperationException("stub");
            }

            @Override
            public JavaRuntime install(int requiredMajor, Path targetDir) {
                throw new UnsupportedOperationException("stub");
            }
        };
    }

    @Test
    @DisplayName("builds a runnable command in the build directory")
    void buildsCommand(@TempDir Path dir) throws Exception {
        var gameDir = new GameDirectory(dir);
        var builds = new FileSystemBuildRepository(gameDir);
        ModdedProfile profile = builds.createProfile(ModLoaderType.VANILLA,
                "", "1.21", "1.21", List.of(), "Vanilla 1.21", 4096);
        VersionMetadata metadata = new MojangVersionMetadataService()
                .parseMetadata(VERSION_JSON, "1.21");
        var runtime = new JavaRuntime(Path.of("/java/bin/java"), 21,
                JavaRuntime.Source.JAVA_HOME);
        var builder = new GameLaunchCommandBuilder(
                new JavaRuntimeManager(new JavaManager(
                        foundService(runtime), failingInstaller())),
                new GameAuthenticationProvider(new ElyAuthService()),
                new LaunchCommandBuilder(),
                builds);

        GameLaunchCommand command = builder.build(profile,
                GameProfile.offline("Steve"), LaunchSettings.defaults(),
                metadata, gameDir);

        assertTrue(command.fullCommand().get(0)
                .endsWith("java"));
        assertTrue(command.fullCommand()
                .contains("net.minecraft.client.main.Main"));
        assertTrue(command.fullCommand().contains("Steve"));
        assertTrue(command.fullCommand().contains("-Xmx4096M"));
        assertEquals(gameDir.moddedProfileDir(profile.id()),
                command.workingDirectory());
    }

    @Test
    @DisplayName("settings override profile memory and jvm args")
    void overrides(@TempDir Path dir) throws Exception {
        var gameDir = new GameDirectory(dir);
        var builds = new FileSystemBuildRepository(gameDir);
        ModdedProfile profile = builds.createProfile(ModLoaderType.VANILLA,
                "", "1.21", "1.21", List.of("-Dprofile=1"), "Vanilla", 2048);
        VersionMetadata metadata = new MojangVersionMetadataService()
                .parseMetadata(VERSION_JSON, "1.21");
        var runtime = new JavaRuntime(Path.of("/java/bin/java"), 21,
                JavaRuntime.Source.JAVA_HOME);
        var builder = new GameLaunchCommandBuilder(
                new JavaRuntimeManager(new JavaManager(
                        foundService(runtime), failingInstaller())),
                new GameAuthenticationProvider(new ElyAuthService()),
                new LaunchCommandBuilder(),
                builds);

        GameLaunchCommand command = builder.build(profile,
                GameProfile.offline("Steve"),
                new LaunchSettings(8192, List.of("-Dcustom=1")),
                metadata, gameDir);

        assertTrue(command.fullCommand().contains("-Xmx8192M"));
        assertTrue(command.fullCommand().contains("-Dcustom=1"));
        assertTrue(command.fullCommand().stream()
                .noneMatch(a -> a.equals("-Dprofile=1")));
    }
}
