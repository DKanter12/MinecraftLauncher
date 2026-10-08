package org.example.launcher.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.launcher.infrastructure.download.ChecksumVerifier;
import org.example.launcher.infrastructure.download.FileDownloader;
import org.example.launcher.infrastructure.download.FileIntegrityChecker;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.download.HttpFileDownloader;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.download.MinecraftInstaller;
import org.example.launcher.infrastructure.download.Sha1ChecksumVerifier;
import org.example.launcher.infrastructure.mojang.AssetIndexService;
import org.example.launcher.infrastructure.java.AdoptiumJavaRuntimeInstaller;
import org.example.launcher.infrastructure.minecraft.AuthlibInjectorManager;
import org.example.launcher.infrastructure.java.DefaultJavaResolutionService;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;
import org.example.launcher.infrastructure.settings.FileSettingsRepository;
import org.example.launcher.application.launch.BuildIntegrityChecker;
import org.example.launcher.application.launch.BuildLaunchListener;
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
import org.example.launcher.application.launch.LaunchStage;
import org.example.launcher.application.launch.LauncherWindowManager;
import org.example.launcher.infrastructure.mojang.MojangAssetIndexService;
import org.example.launcher.infrastructure.mojang.MojangVersionMetadataService;
import org.example.launcher.infrastructure.mojang.MojangVersionService;
import org.example.launcher.infrastructure.filesystem.ProfileService;
import org.example.launcher.infrastructure.skins.SkinService;
import org.example.launcher.infrastructure.java.SystemJavaDetector;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;
import org.example.launcher.infrastructure.mojang.VersionService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.application.account.AccountManager;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.application.java.JavaManager;
import org.example.launcher.core.LauncherContext;
import org.example.launcher.infrastructure.loaders.ModLoaderMetadataMerger;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService;
import org.example.launcher.infrastructure.loaders.ModdedVersionService;
import org.example.launcher.i18n.Lang;
import org.example.launcher.presentation.MainView;
import org.example.launcher.infrastructure.http.HttpDefaults;
import org.example.launcher.version.ModLoaderFamilyType;
import org.example.launcher.version.VersionTypeRegistry;

/**
 * Точка входа JavaFX-приложения Minecraft Launcher.
 * <p>
 * Связывает все сервисы (корень композиции чистой архитектуры):
 * <ul>
 *   <li>{@link VersionService} — загружает манифест версий</li>
 *   <li>{@link VersionMetadataService} — загружает метаданные версий</li>
 *   <li>{@link AssetIndexService} — загружает и разбирает индексы ассетов</li>
 *   <li>{@link FileDownloader} — скачивает отдельные файлы по HTTP</li>
 *   <li>{@link ChecksumVerifier} — проверяет SHA1-хэши локальных файлов</li>
 * <li>{@link InstallationService} — управляет полной установкой</li>
 * <li>{@link JavaResolutionService} — находит и выбирает подходящий Java-рантайм</li>
 * <li>{@link LaunchService} — запускает игру и следит за процессом</li>
 * <li>{@link ProfileService} — управляет профилями игроков</li>
 * </ul>
 */ 
public class LauncherApplication extends Application {

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);

        VersionTypeRegistry typeRegistry = new VersionTypeRegistry();
        // Семейства загрузчиков как типы версий: установленные модовые версии
        // показываются в общем каталоге версий со своими фильтрами
        for (ModLoaderType loaderType : ModLoaderType.values()) {
            if (loaderType != ModLoaderType.VANILLA) {
                typeRegistry.register(ModLoaderFamilyType.of(loaderType));
            }
        }

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

        JavaResolutionService javaResolutionService = new DefaultJavaResolutionService(
                new SystemJavaDetector());

        JavaRuntimeInstaller javaRuntimeInstaller =
                new AdoptiumJavaRuntimeInstaller(fileDownloader);

        GameDirectory defaultGameDir = GameDirectory.defaultDirectory();
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
        Lang.load(preferences);

        ElyAuthService elyAuthService = new ElyAuthService();

        SkinService skinService = new SkinService(GameDirectory.defaultDirectory());

        // Поддержка модовых загрузчиков (Fabric, Forge, NeoForge, Quilt)
        ModLoaderMetadataMerger merger = new ModLoaderMetadataMerger(
                mojangMetadataService);
        ModLoaderRegistry modLoaderRegistry = ModLoaderRegistry.createDefault(
                HttpDefaults.newClient(),
                fileDownloader, javaResolutionService, merger, installationService);
        ModdedVersionService moddedVersionService = new ModdedVersionService(
                mojangVersionService, mojangMetadataService, merger);

        // Игровые сборки (ванильные + модовые, у каждой свой каталог)
        FileSystemBuildRepository buildRepository =
                new FileSystemBuildRepository(defaultGameDir);
        ModdedProfileVerificationService profileVerificationService =
                new ModdedProfileVerificationService(
                        moddedVersionService, mojangVersionService,
                        mojangMetadataService, modLoaderRegistry,
                        integrityChecker, javaResolutionService,
                        installationService);

        // Создание сборок по схеме UI: isBuildAvailable -> createBuild
        // [isVersionDownloaded -> downloadVersion -> createBuildDirectory]
        MinecraftVersionManager versionManager = new MinecraftVersionManager(
                metadataService, installationService, modLoaderRegistry, defaultGameDir);
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
        LauncherWindowManager windows = new LauncherWindowManager() {
            @Override
            public void hide() {
                Platform.runLater(stage::hide);
            }

            @Override
            public void show() {
                Platform.runLater(stage::show);
            }

            @Override
            public void minimize() {
                Platform.runLater(() -> stage.setIconified(true));
            }
        };
        BuildLaunchManager buildLaunchManager = new BuildLaunchManager(
                buildIntegrity,
                repair, elyAuthenticator, javaManager, commandBuilder,
                processes, monitor, exitHandler, windows, buildRepository);

        LauncherContext context = new LauncherContext(
                versionService,
                javaResolutionService,
                javaManager, profileService, preferences, elyAuthService,
                skinService, modLoaderRegistry,
                buildRepository, createBuild,
                accountManager, buildLaunchManager);

        MainView view = new MainView(context);

        Scene scene = new Scene(view.getView(), 1180, 680);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Minecraft Launcher");
        stage.setMinWidth(900);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();

        view.loadVersions();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
