package org.example.launcher.infrastructure.updater;

import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;

/**
 * Список изменений конкретной версии лаунчера.
 */
public class ReleaseNotesProvider {

    public ReleaseNotesProvider() {
    }

    /** Возвращает описание обновления человекочитаемым текстом. */
    public String getReleaseNotes(LauncherVersion version) {
        Objects.requireNonNull(version, "version");
        return version.releaseNotes();
    }
}
