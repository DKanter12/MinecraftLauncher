package org.example.launcher.application.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

/**
 * Итог проверки сборки: какие файлы отсутствуют, какие повреждены,
 * готова ли сборка к запуску. Внутри — полный отчёт проверки
 * для следующих стадий (починка, решение, запуск).
 */
public record BuildIntegrityResult(
        List<String> missingFiles,
        List<String> corruptedFiles,
        boolean valid,
        VerificationReport report) {

    public BuildIntegrityResult {
        Objects.requireNonNull(missingFiles, "missingFiles");
        Objects.requireNonNull(corruptedFiles, "corruptedFiles");
        Objects.requireNonNull(report, "report");
        missingFiles = List.copyOf(missingFiles);
        corruptedFiles = List.copyOf(corruptedFiles);
    }

    /** Все проблемные файлы (отсутствующие + повреждённые). */
    public List<String> problemFiles() {
        var all = new ArrayList<>(missingFiles);
        all.addAll(corruptedFiles);
        return List.copyOf(all);
    }
}
