package org.example.launcher.application.launch;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.application.java.JavaManager;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.i18n.Lang;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.minecraft.NativeExtractor;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

/**
 * Оркестратор запуска сборки, а не класс со всей логикой.
 * Каждый этап — отдельный класс: проверка, починка, аккаунт, Java,
 * команда, процесс, окно, монитор, разбор выхода.
 *
 * <pre>
 * check → [java | repair → recheck] → command → process
 * → hide → monitor → show → finished | crashed
 * </pre>
 *
 * Метод блокирует вызывающий поток: интерфейс запускает его в фоне
 * и получает события через {@link BuildLaunchListener}.
 */
public class BuildLaunchManager {

    private static final int MAX_ROUNDS = 5;

    private final BuildIntegrityChecker integrity;
    private final BuildRepairManager repair;
    private final ElyByAuthenticator authenticator;
    private final JavaManager javaManager;
    private final GameLaunchCommandBuilder commandBuilder;
    private final GameProcessManager processes;
    private final GameProcessMonitor monitor;
    private final GameExitHandler exitHandler;
    private final LauncherWindowManager windows;
    private final FileSystemBuildRepository builds;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public BuildLaunchManager(BuildIntegrityChecker integrity,
                              BuildRepairManager repair,
                              ElyByAuthenticator authenticator,
                              JavaManager javaManager,
                              GameLaunchCommandBuilder commandBuilder,
                              GameProcessManager processes,
                              GameProcessMonitor monitor,
                              GameExitHandler exitHandler,
                              LauncherWindowManager windows,
                              FileSystemBuildRepository builds) {
        this.integrity = Objects.requireNonNull(integrity, "integrity");
        this.repair = Objects.requireNonNull(repair, "repair");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.javaManager = Objects.requireNonNull(javaManager, "javaManager");
        this.commandBuilder = Objects.requireNonNull(commandBuilder, "commandBuilder");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler");
        this.windows = Objects.requireNonNull(windows, "windows");
        this.builds = Objects.requireNonNull(builds, "builds");
    }

    /**
     * Направляет неуспешную проверку по пути восстановления.
     * Вызывать только при {@code !report.ok()}.
     *
     * @param repairAttempted      починка уже выполнялась (защита от циклов)
     * @param javaDownloadAttempted Java 8 уже ставилась (защита от повторов)
     */
    public static LaunchDecision decide(VerificationReport report,
                                        boolean repairAttempted,
                                        boolean javaDownloadAttempted) {
        if (!javaDownloadAttempted
                && report.metadata().isPresent()
                && needsJava8(report.metadata().get())
                && report.errors().stream().allMatch(
                        e -> e.startsWith("No suitable Java runtime"))) {
            return LaunchDecision.INSTALL_JAVA8;
        }
        if (report.isRepairableByInstall() && !repairAttempted) {
            return LaunchDecision.REPAIR;
        }
        return LaunchDecision.FAIL;
    }

    /** Требуют ли метаданные Java 8 или старше. */
    public static boolean needsJava8(VersionMetadata meta) {
        int required = meta.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(8);
        return required <= 8;
    }

    /** Ошибки проверки как текст для диалога. */
    public static String verificationErrorText(VerificationReport report) {
        StringBuilder msg = new StringBuilder(
                Lang.tr("launch.cannot"));
        List<String> errors = report.errors();
        for (String error : errors) {
            msg.append(" - ").append(error).append('\n');
        }
        if (!report.warnings().isEmpty()) {
            msg.append(Lang.tr("launch.warnings"));
            for (String warning : report.warnings()) {
                msg.append(" - ").append(warning).append('\n');
            }
        }
        return msg.toString();
    }

    /**
     * Полный запуск сборки. Блокирует поток до выхода игры.
     *
     * @param repairProgress прогресс починки от интерфейса
     *                       (диалог показывает сам интерфейс)
     */
    public void launch(ModdedProfile profile, GameProfile account,
                       GameDirectory storage,
                       InstallationProgress repairProgress,
                       BuildLaunchListener listener) throws Exception {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(listener, "listener");
        InstallationProgress progress = repairProgress == null
                ? InstallationProgress.NONE : repairProgress;

        listener.onStage(LaunchStage.CHECKING);
        GameProfile current = authenticator.authenticate(account);

        VersionMetadata metadata = null;
        boolean repaired = false;
        boolean javaFixed = false;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            BuildIntegrityResult integrityResult =
                    integrity.check(profile, storage);
            VerificationReport report = integrityResult.report();
            if (report.ok()) {
                Optional<VersionMetadata> resolved = report.metadata();
                if (resolved.isEmpty()) {
                    listener.onFailed("Version metadata missing for "
                            + profile.versionId());
                    return;
                }
                metadata = resolved.get();
                break;
            }
            switch (decide(report, repaired, javaFixed)) {
                case INSTALL_JAVA8 -> {
                    javaFixed = true;
                    listener.onStage(LaunchStage.INSTALLING_JAVA);
                    JavaRuntime runtime;
                    try {
                        runtime = javaManager.ensureInstalled(8,
                                storage.javaRuntimeDir("jre-legacy"));
                    } catch (Exception e) {
                        listener.onFailed(e.getClass().getSimpleName() + ": "
                                + e.getMessage() + Lang.tr("launch.java.manual"));
                        return;
                    }
                    javaManager.adoptManaged(runtime);
                }
                case REPAIR -> {
                    repaired = true;
                    listener.onStage(LaunchStage.REPAIRING);
                    BuildRepairResult result = repair.repair(profile,
                            storage, integrityResult, progress);
                    if (!result.success()) {
                        listener.onFailed(result.error());
                        return;
                    }
                }
                case FAIL -> {
                    listener.onFailed(verificationErrorText(report));
                    return;
                }
            }
        }
        if (metadata == null) {
            listener.onFailed(
                    "Verification did not stabilize for " + profile.name());
            return;
        }

        listener.onStage(LaunchStage.STARTING);
        try {
            NativeExtractor.extractNatives(metadata, storage);
        } catch (IOException e) {
            listener.onFailed("Failed to extract native libraries: "
                    + e.getMessage());
            return;
        }
        LaunchSettings settings = new LaunchSettings(profile.memoryMb(),
                profile.extraJvmArgs());
        GameLaunchCommand command;
        try {
            command = commandBuilder.build(profile, current, settings,
                    metadata, storage);
        } catch (IOException e) {
            listener.onFailed(e.getMessage());
            return;
        }
        GameProcess process;
        try {
            process = processes.start(command, profile.id());
        } catch (IOException e) {
            listener.onFailed(e.getMessage());
            return;
        }
        try {
            builds.touchLastPlayed(profile.id());
        } catch (IOException ignored) {
            // Некритично
        }
        listener.onStage(LaunchStage.RUNNING);
        windows.hide();
        int exitCode;
        try {
            exitCode = monitor.monitor(process);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            windows.show();
            listener.onFailed(Lang.tr("launch.monitor"));
            return;
        }
        windows.show();
        Optional<CrashReport> crash = exitHandler.handle(process, exitCode,
                command.workingDirectory());
        if (crash.isPresent()) {
            listener.onCrashed(crash.get());
        } else {
            listener.onFinished();
        }
    }
}
