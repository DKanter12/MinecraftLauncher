package org.example.launcher.application.launch;

import java.util.Objects;

import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

/**
 * Восстановление сборки после проверки: определяет, откуда взять
 * отсутствующие/повреждённые файлы, докачивает их, заменяет битые
 * и повторно проверяет сборку. Пользователь файлы не выбирает.
 */
public class BuildRepairManager {

    private final ModdedProfileVerificationService verificationService;

    public BuildRepairManager(
            ModdedProfileVerificationService verificationService) {
        this.verificationService = Objects.requireNonNull(
                verificationService, "verificationService");
    }

    /**
     * Чинит сборку по итогу проверки и перепроверяет.
     * Если восстановить не удалось — игра не запускается,
     * ошибка уходит в систему отображения ошибок.
     */
    public BuildRepairResult repair(ModdedProfile profile,
                                    GameDirectory storage,
                                    BuildIntegrityResult integrity,
                                    InstallationProgress progress)
            throws Exception {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(integrity, "integrity");
        InstallationProgress active =
                progress == null ? InstallationProgress.NONE : progress;
        verificationService.repair(profile, storage, active);
        VerificationReport after =
                verificationService.verify(profile, storage);
        if (after.ok()) {
            return BuildRepairResult.restored(integrity.problemFiles());
        }
        return BuildRepairResult.partial(integrity.problemFiles(),
                after.errors(),
                "Repair did not fix: " + String.join("; ", after.errors()));
    }
}
