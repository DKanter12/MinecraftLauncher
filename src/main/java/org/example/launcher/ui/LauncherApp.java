package org.example.launcher.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.launcher.install.ChecksumVerifier;
import org.example.launcher.install.FileDownloader;
import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.HttpFileDownloader;
import org.example.launcher.install.InstallationService;
import org.example.launcher.install.MinecraftInstaller;
import org.example.launcher.install.Sha1ChecksumVerifier;
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
import org.example.launcher.version.VersionTypeRegistry;

/**
 * JavaFX application entry point for the Minecraft Launcher.
 * <p>
 * Wires up all services (clean architecture composition root):
 * <ul>
 *   <li>{@link VersionService} — fetches the version manifest</li>
 *   <li>{@link VersionMetadataService} — fetches per-version metadata</li>
 *   <li>{@link AssetIndexService} — fetches and parses asset indexes</li>
 *   <li>{@link FileDownloader} — downloads individual files via HTTP</li>
 *   <li>{@link ChecksumVerifier} — verifies SHA1 hashes of local files</li>
 * <li>{@link InstallationService} — orchestrates full installation</li>
 * <li>{@link JavaResolutionService} — detects and selects a suitable Java runtime</li>
 * <li>{@link MinecraftLaunchService} — launches the game and monitors the process</li>
 * <li>{@link ProfileService} — manages player profiles</li>
 * </ul>
 */ 
public class LauncherApp extends Application {

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);

        VersionTypeRegistry typeRegistry = new VersionTypeRegistry();

        VersionService versionService = new MojangVersionService();
        VersionMetadataService metadataService = new MojangVersionMetadataService();

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

        MinecraftLaunchArgumentBuilder argumentBuilder = new MinecraftLaunchArgumentBuilder();
        argumentBuilder.setAuthlibInjectorManager(authlibInjectorManager);

        MinecraftLaunchService launchService = new MinecraftLauncher(
                argumentBuilder,
                javaResolutionService,
                checksumVerifier);

        ProfileService profileService = new ProfileService(
                GameDirectory.defaultDirectory().profilesFile());

        LauncherPreferences preferences = new LauncherPreferences(
                GameDirectory.defaultDirectory().preferencesFile());

        ElyAuthService elyAuthService = new ElyAuthService();

        SkinService skinService = new SkinService(GameDirectory.defaultDirectory());

        MainView view = new MainView(
                versionService, metadataService, installationService,
                launchService, javaResolutionService, javaRuntimeInstaller,
                profileService, typeRegistry, preferences, elyAuthService, skinService);

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
