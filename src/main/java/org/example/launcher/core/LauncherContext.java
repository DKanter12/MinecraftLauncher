package org.example.launcher.core;

import org.example.launcher.application.account.AccountManager;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.java.JavaManager;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.ProfileService;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.application.launch.LaunchManager;
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
    private final JavaResolutionService javaResolutionService;
    private final JavaManager javaManager;
    private final ProfileService profileService;
    private final FileSettingsRepository preferences;
    private final ElyAuthService elyAuthService;
    private final SkinService skinService;
    private final ModLoaderRegistry modLoaderRegistry;
    private final FileSystemBuildRepository buildRepository;
    private final CreateBuildUseCase createBuildUseCase;
    private final AccountManager accountManager;
    private final LaunchManager launchManager;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public LauncherContext(
            VersionService versionService,
            JavaResolutionService javaResolutionService,
            JavaManager javaManager,
            ProfileService profileService,
            FileSettingsRepository preferences,
            ElyAuthService elyAuthService,
            SkinService skinService,
            ModLoaderRegistry modLoaderRegistry,
            FileSystemBuildRepository buildRepository,
            CreateBuildUseCase createBuildUseCase,
            AccountManager accountManager,
            LaunchManager launchManager) {
        this.versionService = versionService;
        this.javaResolutionService = javaResolutionService;
        this.javaManager = javaManager;
        this.profileService = profileService;
        this.preferences = preferences;
        this.elyAuthService = elyAuthService;
        this.skinService = skinService;
        this.modLoaderRegistry = modLoaderRegistry;
        this.buildRepository = buildRepository;
        this.createBuildUseCase = createBuildUseCase;
        this.accountManager = accountManager;
        this.launchManager = launchManager;
    }

    public VersionService versionService() {
        return versionService;
    }

    public JavaResolutionService javaResolutionService() {
        return javaResolutionService;
    }

    public JavaManager javaManager() {
        return javaManager;
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

    public LaunchManager launchManager() {
        return launchManager;
    }
}
