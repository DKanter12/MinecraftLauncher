package org.example.launcher.domain.model;

/**
 * Настройки лаунчера как данные. Пустые и отсутствующие значения —
 * {@code null}; значения по умолчанию решают вызывающие стороны.
 * Хранение — за {@code FileSettingsRepository}, формат JSON-файла
 * не меняется (пустое пишется как {@code ""}, как раньше).
 */
public record LauncherPreferences(
        String lastSelectedVersion,
        String lastSelectedAccount,
        String buildsGitUrl,
        String buildsSourceMode,
        String yandexDiskLink,
        String buildsToken,
        String language) {

    /** Пустые настройки (первый запуск). */
    public static LauncherPreferences empty() {
        return new LauncherPreferences(null, null, null, null, null, null,
                null);
    }
}
