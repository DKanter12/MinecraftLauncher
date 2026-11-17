package org.example.launcher.application.launch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.LaunchArguments;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Формирует команду запуска из сборки, аккаунта и настроек.
 * Метаданные версии приходят готовыми из цепочки проверки —
 * повторно не запрашиваются.
 */
public class GameLaunchCommandBuilder {

    private final JavaRuntimeManager javaRuntime;
    private final GameAuthenticationProvider authProvider;
    private final LaunchCommandBuilder commandBuilder;
    private final FileSystemBuildRepository builds;

    public GameLaunchCommandBuilder(JavaRuntimeManager javaRuntime,
                                    GameAuthenticationProvider authProvider,
                                    LaunchCommandBuilder commandBuilder,
                                    FileSystemBuildRepository builds) {
        this.javaRuntime = Objects.requireNonNull(javaRuntime, "javaRuntime");
        this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
        this.builds = Objects.requireNonNull(builds, "builds");
    }

    /**
     * Строит готовую команду: ник, UUID, токен, директория сборки,
     * версия, загрузчик, Java, память, библиотеки, нативы, JVM-аргументы.
     *
     * @param metadata уже разрешённые метаданные запуска
     */
    public GameLaunchCommand build(ModdedProfile profile, GameProfile account,
                                    LaunchSettings settings,
                                    VersionMetadata metadata,
                                    GameDirectory storage) throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(storage, "storage");

        JavaRuntime runtime;
        try {
            runtime = javaRuntime.getJavaFor(metadata);
        } catch (org.example.launcher.domain.JavaException e) {
            throw new IOException(e.getMessage(), e);
        }
        GameProfile effectiveAccount = authProvider
                .getAuthenticationData(account).toGameProfile();
        java.nio.file.Path runtimeDir = builds.resolveGameDir(profile);
        List<String> jvmArgs = mergeJvmArgs(
                settings.memoryMb() > 0
                        ? settings.memoryMb() : profile.memoryMb(),
                settings.extraJvmArgs().isEmpty()
                        ? profile.extraJvmArgs() : settings.extraJvmArgs());
        LaunchArguments args = commandBuilder.build(metadata, storage,
                effectiveAccount, runtime, runtimeDir, jvmArgs);
        return new GameLaunchCommand(args.fullCommand(),
                args.workingDirectory());
    }

    /**
     * Лимит памяти первым аргументом, конфликтующие {@code -Xmx}/{@code -Xms}
     * из доп. аргументов отбрасываются. Без лимита — как есть.
     */
    static List<String> mergeJvmArgs(int memoryMb, List<String> extra) {
        List<String> base = extra == null ? List.of() : extra;
        if (memoryMb <= 0) {
            return List.copyOf(base);
        }
        List<String> merged = new ArrayList<>();
        merged.add("-Xmx" + memoryMb + "M");
        for (String arg : base) {
            if (arg.matches("-Xmx\\d+[mMgG]") || arg.matches("-Xms\\d+[mMgG]")) {
                continue;
            }
            merged.add(arg);
        }
        return List.copyOf(merged);
    }
}
