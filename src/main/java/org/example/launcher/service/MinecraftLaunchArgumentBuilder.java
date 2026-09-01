package org.example.launcher.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchArguments;
import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.util.OsDetector;

/**
 * Default {@link LaunchArgumentBuilder} for Minecraft.
 * <p>
 * Replaces all Mojang placeholders with concrete values from the
 * provided context, builds the classpath from library artifact paths
 * and the client JAR, and assembles the final JVM and game argument
 * lists.
 * <p>
 * For modern versions (1.13+), JVM and game arguments come from
 * {@link VersionMetadata#jvmArguments()} and
 * {@link VersionMetadata#gameArguments()} respectively. For legacy
 * versions, a default set of JVM arguments is constructed and the
 * game arguments are parsed from
 * {@link VersionMetadata#legacyMinecraftArguments()}.
 */
public class MinecraftLaunchArgumentBuilder implements LaunchArgumentBuilder {

    private static final String OFFLINE_TOKEN = "offline";
    private static final String DEFAULT_USER_TYPE = "mojang";

    private AuthlibInjectorManager authlibInjectorManager;

    public void setAuthlibInjectorManager(AuthlibInjectorManager manager) {
        this.authlibInjectorManager = manager;
    }

    @Override
    public LaunchArguments build(VersionMetadata metadata,
                                  GameDirectory gameDir,
                                  GameProfile profile,
                                  JavaRuntime javaRuntime) {
        return build(metadata, gameDir, profile, javaRuntime,
                gameDir.root(), List.of());
    }

    @Override
    public LaunchArguments build(VersionMetadata metadata,
                                  GameDirectory gameDir,
                                  GameProfile profile,
                                  JavaRuntime javaRuntime,
                                  Path runtimeDirectory,
                                  List<String> extraJvmArgs) {

        String classpath = buildClasspath(metadata, gameDir);
        Map<String, String> placeholders = buildPlaceholders(
                metadata, gameDir, profile, runtimeDirectory);
        placeholders.put("${classpath}", classpath);

        List<String> jvmArgs;
        List<String> gameArgs;

        if (metadata.hasStructuredArguments()) {
            jvmArgs = replacePlaceholders(metadata.jvmArguments(), placeholders);
            gameArgs = replacePlaceholders(metadata.gameArguments(), placeholders);
        } else {
            jvmArgs = buildLegacyJvmArgs(metadata, gameDir, classpath);
            gameArgs = buildLegacyGameArgs(metadata, placeholders);
        }

        // Profile-specific JVM arguments (e.g. -Xmx4G)
        if (extraJvmArgs != null && !extraJvmArgs.isEmpty()) {
            List<String> withExtra = new ArrayList<>(
                    jvmArgs.size() + extraJvmArgs.size());
            withExtra.addAll(jvmArgs);
            withExtra.addAll(extraJvmArgs);
            jvmArgs = withExtra;
        }

        if (profile.isElyBy() && authlibInjectorManager != null) {
            try {
                Path agentJar = authlibInjectorManager.ensureAvailable();
                String agentArg = authlibInjectorManager.buildAgentArg(agentJar);
                jvmArgs.add(0, agentArg);
            } catch (java.io.IOException e) {
                // If we can't download authlib-injector, fall back to JVM properties
                jvmArgs = addElyByServerArgs(jvmArgs);
            }
        }

        String mainClass = metadata.mainClass().orElse("net.minecraft.client.main.Main");

        Path workingDir = runtimeDirectory != null ? runtimeDirectory : gameDir.root();

        return new LaunchArguments(
                javaRuntime.javaExecutable(),
                jvmArgs,
                classpath,
                mainClass,
                gameArgs,
                workingDir);
    }

    // ------------------------------------------------------------------
    //  Placeholder construction
    // ------------------------------------------------------------------

    private Map<String, String> buildPlaceholders(VersionMetadata meta, GameDirectory gameDir,
                                                   GameProfile profile,
                                                   Path runtimeDirectory) {
        Map<String, String> map = new HashMap<>();

        String playerName = profile.name();
        String rawUuid = profile.uuid().orElseGet(() -> generateOfflineUuid(playerName));
        String uuid = formatUuid(rawUuid);
        String token = profile.accessToken().orElse(OFFLINE_TOKEN);
        String assetIndexName = meta.assetIndex()
                .map(ai -> ai.id())
                .or(meta::assets)
                .orElse("legacy");

        String sessionToken = profile.isElyBy()
                ? "token:" + token
                : token;

        String userType = profile.isElyBy() ? "mojang" : DEFAULT_USER_TYPE;

        String userProperties = profile.profileProperties().orElse("{}");

        // The game runs inside the runtime directory (per-profile for
        // modded profiles); shared storage paths (assets, libraries,
        // natives) keep pointing at the storage root
        String gameDirValue = runtimeDirectory != null
                ? runtimeDirectory.toString() : gameDir.root().toString();

        map.put("${auth_player_name}", playerName);
        map.put("${auth_name}", playerName);
        map.put("${auth_uuid}", uuid);
        map.put("${auth_access_token}", token);
        map.put("${auth_session}", sessionToken);
        map.put("${game_directory}", gameDirValue);
        map.put("${gameDir}", gameDirValue);
        map.put("${assets_root}", gameDir.assetsDir().toString());
        map.put("${assets_directory}", gameDir.assetsDir().toString());
        map.put("${assets_index_name}", assetIndexName);
        map.put("${assets_assets_index_name}", assetIndexName);
        map.put("${version_name}", meta.id());
        map.put("${version_type}", meta.type().orElse("release"));
        map.put("${natives_directory}", gameDir.nativeDir(meta.id()).toString());
        map.put("${user_properties}", userProperties);
        map.put("${user_type}", userType);
        map.put("${game_assets}", gameDir.virtualAssetsDir(assetIndexName).toString());
        map.put("${library_directory}", gameDir.librariesDir().toString());
        // Forge/NeoForge build their module path (-p) from
        // ${library_directory}/…jar${classpath_separator}… — the
        // separator must resolve to the platform path separator
        map.put("${classpath_separator}", File.pathSeparator);
        map.put("${launcher_name}", "opencode-launcher");
        map.put("${launcher_version}", "1.0");

        return map;
    }

    private List<String> replacePlaceholders(List<String> args, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            result.add(replacePlaceholdersInString(arg, placeholders));
        }
        return result;
    }

    private String replacePlaceholdersInString(String arg, Map<String, String> placeholders) {
        String result = arg;
        for (var entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Classpath construction
    // ------------------------------------------------------------------

    private String buildClasspath(VersionMetadata metadata, GameDirectory gameDir) {
        List<String> paths = new ArrayList<>();
        String osName = OsDetector.mojangName();

        for (Library lib : metadata.libraries()) {
            lib.artifact().ifPresent(artifact -> {
                Path p = resolveLibraryPath(gameDir, artifact);
                if (!paths.contains(p.toString())) {
                    paths.add(p.toString());
                }
            });

            var nativeDl = lib.nativeDownload(osName);
            if (nativeDl.isPresent()) {
                Path np = resolveLibraryPath(gameDir, nativeDl.get());
                if (!paths.contains(np.toString())) {
                    paths.add(np.toString());
                }
            }
        }

        Path clientJar = gameDir.clientJar(metadata.id());
        paths.add(clientJar.toString());

        return String.join(File.pathSeparator, paths);
    }

    private Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        Optional<String> pathOpt = dl.path();
        if (pathOpt.isPresent()) {
            return gameDir.library(pathOpt.get());
        }
        String url = dl.url();
        if (url != null && !url.isBlank()) {
            int idx = url.lastIndexOf('/');
            String fileName = (idx >= 0) ? url.substring(idx + 1) : url;
            return gameDir.librariesDir().resolve(fileName);
        }
        return gameDir.librariesDir().resolve("unknown.jar");
    }

    // ------------------------------------------------------------------
    //  Legacy argument construction (pre-1.13)
    // ------------------------------------------------------------------

    private List<String> buildLegacyJvmArgs(VersionMetadata meta, GameDirectory gameDir,
                                            String classpath) {
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-Djava.library.path=" + gameDir.nativeDir(meta.id()));
        jvmArgs.add("-cp");
        jvmArgs.add(classpath);
        return jvmArgs;
    }

    /**
     * Adds Ely.by server override JVM properties so Minecraft uses
     * Ely.by's auth/session/skin servers instead of Mojang's.
     */
    private List<String> addElyByServerArgs(List<String> jvmArgs) {
        List<String> result = new ArrayList<>(jvmArgs);
        result.add("-Dminecraft.api.auth.host=https://authserver.ely.by/auth");
        result.add("-Dminecraft.api.account.host=https://api.ely.by");
        result.add("-Dminecraft.api.session.host=https://authserver.ely.by/session");
        result.add("-Dminecraft.api.services.host=https://api.ely.by");
        return result;
    }

    private List<String> buildLegacyGameArgs(VersionMetadata meta, Map<String, String> placeholders) {
        String legacy = meta.legacyMinecraftArguments().orElse("");
        String[] tokens = legacy.split("\\s+");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(replacePlaceholdersInString(token, placeholders));
            }
        }
        return result;
    }

    /**
     * Formats a UUID string to the standard 8-4-4-4-12 dashed format.
     * Ely.by returns UUIDs without dashes (e.g. "a1b2c3d4e5f6..."),
     * but Minecraft expects them with dashes.
     */
    static String formatUuid(String uuid) {
        if (uuid == null) return null;
        String clean = uuid.replace("-", "");
        if (clean.length() != 32) return uuid;
        return clean.substring(0, 8) + "-" + clean.substring(8, 12) + "-"
                + clean.substring(12, 16) + "-" + clean.substring(16, 20) + "-"
                + clean.substring(20);
    }

    // ------------------------------------------------------------------
    //  Offline UUID generation
    // ------------------------------------------------------------------

    /**
     * Generates an offline UUID from the player name, matching the
     * algorithm used by Minecraft (bukkit/Spigot OfflinePlayer).
     */
    static String generateOfflineUuid(String playerName) {
        byte[] bytes = ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes).toString();
    }
}
