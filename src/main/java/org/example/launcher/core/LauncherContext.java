package org.example.launcher.core;

import org.example.launcher.application.account.AccountManager;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.java.JavaEnvironmentManager;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.ProfileService;
import org.example.launcher.infrastructure.java.JavaFinder;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.application.launch.BuildLaunchManager;
import org.example.launcher.infrastructure.mojang.VersionService;
import org.example.launcher.infrastructure.settings.FileSettingsRepository;
import org.example.launcher.infrastructure.skins.SkinService;

/**
 * Единый объект со всеми сервисами лаунчера.
 * Собирается один раз в {@code LauncherApplication}, дальше передаётся
 * целиком (например, в главный вид) вместо десятка отдельных параметров.
 */
public final class LauncherContext {

    private final VersionService versionService;
    private final JavaFinder javaFinder;
    private final JavaEnvironmentManager javaEnvironmentManager;
    private final ProfileService profileService;
    private final FileSettingsRepository preferences;
    private final ElyAuthService elyAuthService;
    private final SkinService skinService;
    private final ModLoaderRegistry modLoaderRegistry;
    private final FileSystemBuildRepository buildRepository;
    private final CreateBuildUseCase createBuildUseCase;
    private final AccountManager accountManager;
    private final BuildLaunchManager buildLaunchManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public LauncherContext(
            VersionService versionService,
            JavaFinder javaFinder,
            JavaEnvironmentManager javaEnvironmentManager,
            ProfileService profileService,
            FileSettingsRepository preferences,
            ElyAuthService elyAuthService,
            SkinService skinService,
            ModLoaderRegistry modLoaderRegistry,
            FileSystemBuildRepository buildRepository,
            CreateBuildUseCase createBuildUseCase,
            AccountManager accountManager,
            BuildLaunchManager buildLaunchManager) {
        this.versionService = versionService;
        this.javaFinder = javaFinder;
        this.javaEnvironmentManager = javaEnvironmentManager;
        this.profileService = profileService;
        this.preferences = preferences;
        this.elyAuthService = elyAuthService;
        this.skinService = skinService;
        this.modLoaderRegistry = modLoaderRegistry;
        this.buildRepository = buildRepository;
        this.createBuildUseCase = createBuildUseCase;
        this.accountManager = accountManager;
        this.buildLaunchManager = buildLaunchManager;
    }

    public VersionService versionService() {
        return versionService;
    }

    public JavaFinder javaFinder() {
        return javaFinder;
    }

    public JavaEnvironmentManager javaEnvironmentManager() {
        return javaEnvironmentManager;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public FileSettingsRepository preferences() {
        return preferences;
    }

    public ElyAuthService elyAuthService() {
        return elyAuthService;
    }

    public SkinService skinService() {
        return skinService;
    }

    public ModLoaderRegistry modLoaderRegistry() {
        return modLoaderRegistry;
    }

    public FileSystemBuildRepository buildRepository() {
        return buildRepository;
    }

    public CreateBuildUseCase createBuildUseCase() {
        return createBuildUseCase;
    }

    public AccountManager accountManager() {
        return accountManager;
    }

    public BuildLaunchManager buildLaunchManager() {
        return buildLaunchManager;
    }
}
