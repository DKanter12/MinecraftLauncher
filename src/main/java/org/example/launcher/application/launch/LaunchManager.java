package org.example.launcher.application.launch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.example.launcher.application.java.JavaManager;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.LaunchResult;
import org.example.launcher.i18n.Lang;
import org.example.launcher.domain.model.MinecraftProcess;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

/**
 * Верх подсистемы запуска: проверка → починка → Java → старт → разбор итога.
 * Потоков и диалогов не знает — стадии вызываются по одной,
 * решения принимает чистая {@link #decide}.
 */
public class LaunchManager {

    private final ModdedProfileVerificationService verificationService;
    private final LaunchService launchService;
    private final JavaManager javaManager;
    private final FileSystemBuildRepository builds;
    private final ElyAuthService elyAuthService;

    public LaunchManager(ModdedProfileVerificationService verificationService,
                         LaunchService launchService,
                         JavaManager javaManager,
                         FileSystemBuildRepository builds,
                         ElyAuthService elyAuthService) {
        this.verificationService = Objects.requireNonNull(verificationService, "verificationService");
        this.launchService = Objects.requireNonNull(launchService, "launchService");
        this.javaManager = Objects.requireNonNull(javaManager, "javaManager");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.elyAuthService = Objects.requireNonNull(elyAuthService, "elyAuthService");
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

    /** Проверяет установку сборки. */
    public VerificationReport verify(ModdedProfile profile, GameDirectory storage)
            throws Exception {
        return verificationService.verify(profile, storage);
    }

    /** Чинит докачкой и перепроверяет. */
    public VerificationReport repairAndReverify(ModdedProfile profile,
                                                GameDirectory storage,
                                                InstallationProgress progress)
            throws Exception {
        verificationService.repair(profile, storage, progress);
        return verificationService.verify(profile, storage);
    }

    /** Ставит управляемую JRE 8 для legacy-сборок. */
    public JavaRuntime ensureJava8(GameDirectory storage) throws Exception {
        return javaManager.ensureInstalled(8, storage.javaRuntimeDir("jre-legacy"));
    }

    /** Делает поставленный рантайм используемым. */
    public void adoptJava(JavaRuntime runtime) {
        javaManager.adoptManaged(runtime);
    }

    /**
     * Аккаунт для запуска: Ely.by обновляется, остальные как есть.
     * При ошибке обновления возвращается исходный профиль (кэш).
     */
    public GameProfile resolveAccount(GameProfile account) {
        if (account != null && account.isElyBy()) {
            return elyAuthService.refreshProfile(account);
        }
        return account;
    }

    /** Стартует игру в каталоге сборки. */
    public LaunchResult launch(ModdedProfile profile, GameProfile account,
                               VersionMetadata metadata, GameDirectory storage)
            throws IOException {
        Path runtimeDir = builds.resolveGameDir(profile);
        FileSystemBuildRepository.ensureProfileFolders(runtimeDir);
        return launchService.launch(metadata, storage, account, runtimeDir,
                FileSystemBuildRepository.effectiveJvmArgs(profile));
    }

    /** Фиксирует запуск в метке {@code lastPlayed}. */
    public void markPlayed(String profileId) throws IOException {
        builds.touchLastPlayed(profileId);
    }

    /** Разбирает падение процесса. */
    public CrashReport analyzeCrash(MinecraftProcess process, int exitCode) {
        return CrashAnalyzer.analyze(exitCode, process.stdout(), process.stderr());
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
}
