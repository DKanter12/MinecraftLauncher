package org.example.launcher.infrastructure.updater;

import java.util.Objects;

import org.example.launcher.domain.model.LauncherVersion;

/**
 * Сравнивает установленную и доступную версии лаунчера.
 */
public class LauncherVersionChecker {

    public LauncherVersionChecker() {
    }

    /**
     * @return true, если доступная версия строго новее текущей
     */
    public boolean isUpdateAvailable(LauncherVersion latestVersion) {
        Objects.requireNonNull(latestVersion, "latestVersion");
        return AppVersion.isNewer(latestVersion.version(),
                AppVersion.current());
    }
}
