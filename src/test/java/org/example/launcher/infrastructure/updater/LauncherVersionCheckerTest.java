package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.LauncherVersion;

@DisplayName("LauncherVersionChecker")
class LauncherVersionCheckerTest {

    private static LauncherVersion version(String number) {
        return new LauncherVersion(number, null, "https://example.com/l.zip",
                10, "ab".repeat(32), "notes");
    }

    @Test
    @DisplayName("strictly newer version is an update")
    void newer() {
        assertTrue(new LauncherVersionChecker()
                .isUpdateAvailable(version("9.9.9")));
    }

    @Test
    @DisplayName("same version is not an update")
    void same() {
        assertFalse(new LauncherVersionChecker().isUpdateAvailable(
                version(AppVersion.current())));
    }
}
