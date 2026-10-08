package org.example.launcher.app;

import org.example.launcher.application.account.AccountManager;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.application.java.JavaManager;
import org.example.launcher.application.launch.BuildIntegrityChecker;
import org.example.launcher.application.launch.BuildLaunchManager;
import org.example.launcher.application.launch.BuildRepairManager;
import org.example.launcher.application.launch.ElyByAuthenticator;
import org.example.launcher.application.launch.GameAuthenticationProvider;
import org.example.launcher.application.launch.GameExitHandler;
import org.example.launcher.application.launch.GameLaunchCommandBuilder;
import org.example.launcher.application.launch.GameProcessManager;
import org.example.launcher.application.launch.GameProcessMonitor;
import org.example.launcher.application.launch.JavaRuntimeManager;
import org.example.launcher.application.launch.LaunchCommandBuilder;
import org.example.launcher.application.launch.LauncherWindowManager;
import org.example.launcher.core.LauncherContext;
import org.example.launcher.application.version.MinecraftVersions;
import org.example.launcher.infrastructure.download.ChecksumVerifier;
import org.example.launcher.infrastructure.download.FileDownloader;
import org.example.launcher.infrastructure.download.FileIntegrityChecker;
import org.example.launcher.infrastructure.download.HttpFileDownloader;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.download.MinecraftInstaller;
import org.example.launcher.infrastructure.download.Sha1ChecksumVerifier;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.filesystem.ProfileService;
import org.example.launcher.infrastructure.http.HttpDefaults;
import org.example.launcher.infrastructure.java.AdoptiumJavaRuntimeInstaller;
import org.example.launcher.infrastructure.java.DefaultJavaResolutionService;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;
import org.example.launcher.infrastructure.java.SystemJavaDetector;
import org.example.launcher.infrastructure.loaders.ModLoaderMetadataMerger;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService;
import org.example.launcher.infrastructure.loaders.ModdedVersionService;
import org.example.launcher.infrastructure.minecraft.AuthlibInjectorManager;
import org.example.launcher.infrastructure.mojang.AssetIndexService;
import org.example.launcher.infrastructure.mojang.MojangAssetIndexService;
import org.example.launcher.infrastructure.mojang.MojangVersionMetadataService;
import org.example.launcher.infrastructure.mojang.MojangVersionService;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;
import org.example.launcher.infrastructure.mojang.VersionService;
import org.example.launcher.infrastructure.settings.FileSettingsRepository;
import org.example.launcher.infrastructure.skins.SkinService;

/**
 * Единственное место сборки графа зависимостей: инфраструктура →
 * сервисы приложения → use cases → контекст. Бизнес-логики здесь нет,
 * только связывание через конструкторы.
 */
public final class LauncherCompositionRoot {

    private LauncherCompositionRoot() {
    }

    /**
     * Собирает полностью настроенный контекст приложения.
     * Управление окном подставляет presentation (там живёт Stage).
     */
    public static LauncherContext createContext(LauncherWindowManager windows) {
        GameDirectory defaultGameDir = GameDirectory.defaultDirectory();

        MojangVersionService mojangVersionService = new MojangVersionService();
        VersionService versionService = mojangVersionService;
        MojangVersionMetadataService mojangMetadataService =
                new MojangVersionMetadataService();
        VersionMetadataService metadataService = mojangMetadataService;

        AssetIndexService assetIndexService = new MojangAssetIndexService();
        FileDownloader fileDownloader = new HttpFileDownloader();
        ChecksumVerifier checksumVerifier = new Sha1ChecksumVerifier();

        InstallationService installationService = new MinecraftInstaller(
                fileDownloader, checksumVerifier, assetIndexService);

        JavaResolutionService javaResolutionService =
                new DefaultJavaResolutionService(new SystemJavaDetector());
        JavaRuntimeInstaller javaRuntimeInstaller =
                new AdoptiumJavaRuntimeInstaller(fileDownloader);

        AuthlibInjectorManager authlibInjectorManager =
                new AuthlibInjectorManager(defaultGameDir);
        LaunchCommandBuilder argumentBuilder =
                new LaunchCommandBuilder(authlibInjectorManager);

        FileIntegrityChecker integrityChecker =
                new FileIntegrityChecker(checksumVerifier);
        ProfileService profileService = new ProfileService(
                GameDirectory.defaultDirectory().profilesFile());

        FileSettingsRepository preferences = new FileSettingsRepository(
                GameDirectory.defaultDirectory().preferencesFile());

        ElyAuthService elyAuthService = new ElyAuthService();
        SkinService skinService =
                new SkinService(GameDirectory.defaultDirectory());

        ModLoaderMetadataMerger merger = new ModLoaderMetadataMerger(
                mojangMetadataService);
        ModLoaderRegistry modLoaderRegistry = ModLoaderRegistry.createDefault(
                HttpDefaults.newClient(),
                fileDownloader, javaResolutionService, merger,
                installationService);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                mojangVersionService, mojangMetadataService, merger);

        FileSystemBuildRepository buildRepository =
                new FileSystemBuildRepository(defaultGameDir);
        ModdedProfileVerificationService profileVerificationService =
                new ModdedProfileVerificationService(
                        moddedVersionService, mojangVersionService,
                        mojangMetadataService, modLoaderRegistry,
                        integrityChecker, javaResolutionService,
                        installationService);

        MinecraftVersionManager versionManager = new MinecraftVersionManager(
                metadataService, installationService, modLoaderRegistry,
                defaultGameDir);
        CreateBuildUseCase createBuild = new CreateBuildUseCase(
                versionManager, buildRepository, defaultGameDir);
        AccountManager accountManager = new AccountManager(profileService);
        JavaManager javaManager = new JavaManager(
                javaResolutionService, javaRuntimeInstaller);
        ElyByAuthenticator elyAuthenticator =
                new ElyByAuthenticator(elyAuthService);
        JavaRuntimeManager javaRuntimeManager = new JavaRuntimeManager(
                javaManager);
        GameAuthenticationProvider authProvider =
                new GameAuthenticationProvider(elyAuthService);
        GameLaunchCommandBuilder commandBuilder = new GameLaunchCommandBuilder(
                javaRuntimeManager, authProvider, argumentBuilder,
                buildRepository);
        BuildIntegrityChecker buildIntegrity =
                new BuildIntegrityChecker(profileVerificationService);
        BuildRepairManager repair =
                new BuildRepairManager(profileVerificationService);
        GameProcessManager processes = new GameProcessManager();
        GameProcessMonitor monitor = new GameProcessMonitor();
        GameExitHandler exitHandler = new GameExitHandler();

        return new LauncherContext(
                versionService,
                javaResolutionService,
                javaManager, profileService, preferences, elyAuthService,
                skinService, modLoaderRegistry,
                buildRepository, createBuild,
                accountManager, buildLaunchManager(
                        buildIntegrity, repair, elyAuthenticator, javaManager,
                        commandBuilder, processes, monitor, exitHandler,
                        buildRepository, windows));
    }

    private static BuildLaunchManager buildLaunchManager(
            BuildIntegrityChecker integrity,
            BuildRepairManager repair,
            ElyByAuthenticator authenticator,
            JavaManager javaManager,
            GameLaunchCommandBuilder commandBuilder,
            GameProcessManager processes,
            GameProcessMonitor monitor,
            GameExitHandler exitHandler,
            FileSystemBuildRepository builds,
            LauncherWindowManager windows) {
        return new BuildLaunchManager(integrity, repair, authenticator,
                javaManager, commandBuilder, processes, monitor, exitHandler,
                windows, builds);
    }
}
