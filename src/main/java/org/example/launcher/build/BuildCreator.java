package org.example.launcher.build;

import java.io.IOException;
import java.util.Objects;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.service.ModdedProfileService;

/**
 * Оркестратор создания сборок по схеме UI:
 * {@code isBuildAvailable -> createBuild [isVersionDownloaded ->
 * createBuildDirectory + populate / downloadVersion + повторная проверка] ->
 * отображение в интерфейсе}.
 *
 * <p>Класс не качает файлы и не строит UI сам: скачивание делегировано
 * {@link MinecraftVersionManager}, папки/реестр — {@link ModdedProfileService},
 * отображение остаётся в {@code MainView}.
 */
public class BuildCreator {

    private final MinecraftVersionManager versionManager;
    private final ModdedProfileService profileService;
    private final GameDirectory storage;

    public BuildCreator(
            MinecraftVersionManager versionManager,
            ModdedProfileService profileService,
            GameDirectory storage) {
        this.versionManager = Objects.requireNonNull(versionManager);
        this.profileService = Objects.requireNonNull(profileService);
        this.storage = Objects.requireNonNull(storage);
    }

    /**
     * Проверка, можно ли создавать сборку с таким именем и версией.
     *
     * @param versionId id версии ({@code 1.21.4} или {@code fabric-loader-...})
     * @param buildName имя из интерфейса (пустое = авто-имя, всегда доступно)
     * @return {@code true} — такой сборки ещё нет, можно создавать;
     *         {@code false} — сборка с тем же именем под ту же версию уже создана
     */
    public boolean isBuildAvailable(String versionId, String buildName) throws IOException {
        String wanted = buildName == null ? "" : buildName.trim();
        if (wanted.isEmpty()) {
            return true;
        }
        for (ModdedProfile existing : profileService.loadProfiles()) {
            boolean sameName = existing.name().trim().equalsIgnoreCase(wanted);
            boolean sameVersion = existing.versionId().equals(versionId);
            if (sameName && sameVersion) {
                return false;
            }
        }
        return true;
    }

    /** Проверка, скачана ли нужная версия (делегирует менеджеру версий). */
    public boolean isVersionDownloaded(BuildRequest request) {
        return versionManager.isVersionDownloaded(request);
    }

    /**
     * Полный пайплайн создания сборки.
     *
     * @return созданный профиль (его уже можно показывать в интерфейсе)
     */
    public ModdedProfile createBuild(BuildRequest request, InstallationProgress progress)
            throws IOException {
        String versionId = versionManager.resolveVersionId(request);
        String effectiveVersionId = versionId;
        if (!isVersionDownloaded(request)) {
            // Установщик возвращает реальный id из profile json (для Fabric
            // обычно совпадает с ожидаемым, но доверяем факту, а не формуле).
            effectiveVersionId = versionManager.downloadVersion(request,
                    progress == null ? InstallationProgress.NONE : progress);
            boolean ok = effectiveVersionId.equals(versionId)
                    ? isVersionDownloaded(request)
                    : versionManager.isVersionFilesPresent(effectiveVersionId);
            if (!ok) {
                throw new IOException("Version download did not produce " + effectiveVersionId);
            }
        }
        ModdedProfile profile = createBuildDirectory(request, effectiveVersionId);
        populateBuildDirectories(profile);
        return profile;
    }

    /**
     * Создаёт директорию под сборку и регистрирует её
     * (имя каталога выводится из названия сборки + делается уникальным
     * внутри группы версии; см. {@link Build}).
     */
    public ModdedProfile createBuildDirectory(BuildRequest request, String versionId)
            throws IOException {
        Build build = request.build();
        return profileService.createProfile(
                build.core(),
                build.isVanilla() ? "" : build.coreVersion(),
                build.minecraftVersion(),
                versionId,
                request.extraJvmArgs(),
                build.effectiveName(),
                0);
    }

    /**
     * Добавляет всё нужное для запуска внутрь папки сборки.
     * Отдельный метод, чтобы было видно: регистрация в реестре ≠ наполнение папки.
     * Сейчас это стандартные папки ({@code mods, config, resourcepacks,
     * shaderpacks, saves, logs}); существующие файлы не трогаются.
     */
    public void populateBuildDirectories(ModdedProfile profile) throws IOException {
        ModdedProfileService.ensureProfileFolders(
                storage.moddedProfileDir(profile.id()));
    }
}
