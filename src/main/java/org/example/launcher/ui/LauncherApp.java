package org.example.launcher.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.launcher.infrastructure.download.ChecksumVerifier;
import org.example.launcher.infrastructure.download.FileDownloader;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.download.HttpFileDownloader;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.download.MinecraftInstaller;
import org.example.launcher.infrastructure.download.Sha1ChecksumVerifier;
import org.example.launcher.service.AssetIndexService;
import org.example.launcher.service.AdoptiumJavaRuntimeInstaller;
import org.example.launcher.service.AuthlibInjectorManager;
import org.example.launcher.service.DefaultJavaResolutionService;
import org.example.launcher.service.ElyAuthService;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.JavaRuntimeInstaller;
import org.example.launcher.service.LauncherPreferences;
import org.example.launcher.service.MinecraftLaunchArgumentBuilder;
import org.example.launcher.service.MinecraftLaunchService;
import org.example.launcher.service.MinecraftLauncher;
import org.example.launcher.service.MojangAssetIndexService;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.service.ProfileService;
import org.example.launcher.service.SkinService;
import org.example.launcher.service.SystemJavaDetector;
import org.example.launcher.service.VersionMetadataService;
import org.example.launcher.service.VersionService;
import org.example.launcher.service.ModdedProfileService;
import org.example.launcher.service.AccountManager;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.service.modloader.ModLoaderMetadataMerger;
import org.example.launcher.service.modloader.ModLoaderRegistry;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.service.modloader.ModdedProfileVerificationService;
import org.example.launcher.service.modloader.ModdedVersionService;
import org.example.launcher.i18n.Lang;
import org.example.launcher.net.HttpDefaults;
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
 * <li>{@link MinecraftLaunchService} — запускает игру и следит за процессом</li>
 * <li>{@link ProfileService} — управляет профилями игроков</li>
 * </ul>
 */ 
public class LauncherApp extends Application {

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

        MinecraftLaunchArgumentBuilder argumentBuilder =
                new MinecraftLaunchArgumentBuilder(authlibInjectorManager);

        MinecraftLauncher launcher = new MinecraftLauncher(
                argumentBuilder,
                javaResolutionService,
                checksumVerifier);
        MinecraftLaunchService launchService = launcher;
        ProfileService profileService = new ProfileService(
                GameDirectory.defaultDirectory().profilesFile());

        LauncherPreferences preferences = new LauncherPreferences(
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

        // Игровые инстансы (ванильные + модовые, у каждого свой каталог)
        ModdedProfileService moddedProfileService =
                new ModdedProfileService(defaultGameDir);
        ModdedProfileVerificationService profileVerificationService =
                new ModdedProfileVerificationService(
                        moddedVersionService, mojangVersionService,
                        mojangMetadataService, modLoaderRegistry,
                        launcher, javaResolutionService, installationService);

        // Создание сборок по схеме UI: isBuildAvailable -> createBuild
        // [isVersionDownloaded -> downloadVersion -> createBuildDirectory]
        MinecraftVersionManager versionManager = new MinecraftVersionManager(
                metadataService, installationService, modLoaderRegistry, defaultGameDir);
        CreateBuildUseCase CreateBuildUseCase = new CreateBuildUseCase(
                versionManager, moddedProfileService, defaultGameDir);
        AccountManager accountManager = new AccountManager(profileService);

        MainView view = new MainView(
                versionService, metadataService, installationService,
                launchService, javaResolutionService, javaRuntimeInstaller,
                profileService, preferences, elyAuthService, skinService,
                modLoaderRegistry, moddedVersionService,
                moddedProfileService, profileVerificationService, CreateBuildUseCase,
                accountManager);

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
