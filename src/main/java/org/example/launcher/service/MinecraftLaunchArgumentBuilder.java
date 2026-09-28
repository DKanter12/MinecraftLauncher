package org.example.launcher.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchArguments;
import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.util.LibraryPaths;
import org.example.launcher.util.OsDetector;

/**
 * Реализация {@link LaunchArgumentBuilder} для Minecraft по умолчанию.
 * <p>
 * Заменяет все плейсхолдеры Mojang конкретными значениями из
 * переданного контекста, строит classpath из путей артефактов библиотек
 * и клиентского JAR и собирает итоговые списки JVM- и игровых аргументов.
 * <p>
 * Для современных версий (1.13+) JVM- и игровые аргументы берутся из
 * {@link VersionMetadata#jvmArguments()} и
 * {@link VersionMetadata#gameArguments()} соответственно. Для старых
 * версий строится набор JVM-аргументов по умолчанию, а игровые
 * разбираются из {@link VersionMetadata#legacyMinecraftArguments()}.
 */
public class MinecraftLaunchArgumentBuilder implements LaunchArgumentBuilder {

    private static final String OFFLINE_TOKEN = "offline";
    private static final String DEFAULT_USER_TYPE = "mojang";
    private static final String LAUNCHER_NAME = "opencode-launcher";
    private static final String LAUNCHER_VERSION = "1.0";
    private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";
    private static final String DEFAULT_MAIN_CLASS = "net.minecraft.client.main.Main";

    private AuthlibInjectorManager authlibInjectorManager;

    public MinecraftLaunchArgumentBuilder() {
        this(null);
    }

    public MinecraftLaunchArgumentBuilder(AuthlibInjectorManager authlibInjectorManager) {
        this.authlibInjectorManager = authlibInjectorManager;
    }

    /**
     * @deprecated предпочтительнее внедрение через конструктор
     * {@link #MinecraftLaunchArgumentBuilder(AuthlibInjectorManager)}.
     */
    @Deprecated
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

        // Профильные JVM-аргументы (например, -Xmx4G)
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
            } catch (IOException e) {
                // Если authlib-injector скачать не удалось, откатываемся на JVM-свойства
                jvmArgs = addElyByServerArgs(jvmArgs);
            }
        }

        String mainClass = metadata.mainClass().orElse(DEFAULT_MAIN_CLASS);

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
    //  Построение плейсхолдеров
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

        // Игра работает внутри рабочего каталога (для модовых профилей — свой
        // на профиль); общие пути хранилища (ассеты, библиотеки,
        // нативы) продолжают указывать на корень хранилища
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
        // Forge/NeoForge строят свой module path (-p) из
        // ${library_directory}/…jar${classpath_separator}… — разделитель
        // должен разрешаться в платформенный разделитель путей
        map.put("${classpath_separator}", File.pathSeparator);
        map.put("${launcher_name}", LAUNCHER_NAME);
        map.put("${launcher_version}", LAUNCHER_VERSION);

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
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Построение classpath
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

            lib.nativeDownload(osName).ifPresent(nativeDl -> {
                Path np = resolveLibraryPath(gameDir, nativeDl);
                if (!paths.contains(np.toString())) {
                    paths.add(np.toString());
                }
            });
        }

        Path clientJar = gameDir.clientJar(metadata.id());
        paths.add(clientJar.toString());

        return String.join(File.pathSeparator, paths);
    }

    private Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        return LibraryPaths.resolve(gameDir, dl);
    }

    // ------------------------------------------------------------------
    //  Построение legacy-аргументов (до 1.13)
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
     * Добавляет переопределения серверов Ely.by через JVM-свойства, чтобы Minecraft
     * использовал серверы авторизации/сессий/скинов Ely.by вместо Mojang.
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
     * Форматирует строку UUID в стандартный вид 8-4-4-4-12 с дефисами.
     * Ely.by возвращает UUID без дефисов (например, "a1b2c3d4e5f6..."),
     * а Minecraft ожидает их с дефисами.
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
    //  Генерация офлайн-UUID
    // ------------------------------------------------------------------

    /**
     * Генерирует офлайн-UUID из ника игрока, повторяя алгоритм
     * Minecraft (bukkit/Spigot OfflinePlayer).
     */
    static String generateOfflineUuid(String playerName) {
        Objects.requireNonNull(playerName, "playerName");
        byte[] bytes = (OFFLINE_UUID_PREFIX + playerName).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes).toString();
    }
}
