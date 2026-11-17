package org.example.launcher.application.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

/**
 * Проверка целостности файлов сборки: существуют ли все необходимые файлы
 * (клиент, библиотеки, файлы загрузчика, файлы сборки) и целы ли они
 * по контрольным суммам.
 */
public class BuildIntegrityChecker {

    private final ModdedProfileVerificationService verificationService;

    public BuildIntegrityChecker(
            ModdedProfileVerificationService verificationService) {
        this.verificationService = Objects.requireNonNull(
                verificationService, "verificationService");
    }

    /**
     * Проверяет сборку и раскладывает проблемы на отсутствующие
     * и повреждённые (несовпадение хэша) файлы.
     */
    public BuildIntegrityResult check(ModdedProfile profile,
                                      GameDirectory storage)
            throws Exception {
        VerificationReport report =
                verificationService.verify(profile, storage);
        List<String> missing = new ArrayList<>();
        List<String> corrupted = new ArrayList<>();
        for (String error : report.errors()) {
            if (error.toLowerCase().contains("hash mismatch")) {
                corrupted.add(error);
            } else {
                missing.add(error);
            }
        }
        return new BuildIntegrityResult(missing, corrupted,
                report.ok(), report);
    }
}
