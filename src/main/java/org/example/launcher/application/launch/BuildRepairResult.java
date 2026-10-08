package org.example.launcher.application.launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Итог восстановления сборки.
 *
 * @param success       сборка восстановлена и проходит проверку
 * @param restoredFiles файлы, которые удалось восстановить
 * @param failedFiles   файлы, восстановить которые не удалось
 * @param error         текст ошибки при неудаче (пусто при успехе)
 */
public record BuildRepairResult(
        boolean success,
        List<String> restoredFiles,
        List<String> failedFiles,
        String error) {

    public BuildRepairResult {
        Objects.requireNonNull(restoredFiles, "restoredFiles");
        Objects.requireNonNull(failedFiles, "failedFiles");
        restoredFiles = List.copyOf(restoredFiles);
        failedFiles = List.copyOf(failedFiles);
        if (error == null) {
            error = "";
        }
    }

    /** Успешный итог: всё из проблемного списка восстановлено. */
    public static BuildRepairResult restored(List<String> files) {
        return new BuildRepairResult(true, files, List.of(), "");
    }

    /** Неудачный итог: что осталось битым и почему. */
    public static BuildRepairResult failed(List<String> failedFiles,
                                           String error) {
        return new BuildRepairResult(false, List.of(), failedFiles, error);
    }

    /** Частичный итог после повторной проверки. */
    public static BuildRepairResult partial(List<String> before,
                                            List<String> remaining,
                                            String error) {
        List<String> restored = new ArrayList<>(before);
        restored.removeAll(remaining);
        return new BuildRepairResult(false, restored, remaining, error);
    }
}
