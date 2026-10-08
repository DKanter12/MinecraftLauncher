package org.example.launcher.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.example.launcher.infrastructure.updater.AppVersion;
import org.example.launcher.infrastructure.updater.LauncherUpdate;

class AppVersionTest {

    @Test
    void comparesSemver() {
        assertTrue(AppVersion.compare("1.1.0", "1.0.9") > 0);
        assertTrue(AppVersion.compare("1.10.0", "1.9.9") > 0);
        assertEquals(0, AppVersion.compare("1.0.0", "1.0.0"));
        assertTrue(AppVersion.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    void toleratesLeadingV() {
        assertEquals(0, AppVersion.compare("v1.0", "1.0"));
        assertTrue(AppVersion.compare("v1.1", "v1.0") > 0);
    }

    @Test
    void isNewerDrivesUpdateDetection() {
        assertTrue(AppVersion.isNewer("1.1.0", "v1.0"));
        assertFalse(AppVersion.isNewer("v1.0", "v1.0"));
        assertFalse(AppVersion.isNewer("1.0-SNAPSHOT", "1.0"));
    }
}

class LauncherUpdateTest {

    private static final String MANIFEST = """
            {
              "version": "1.1.0",
              "notes": "Bug fixes",
              "package": {
                "url": "https://example.com/launcher.zip",
                "sha256": "abc123",
                "size": 42
              }
            }
            """;

    @Test
    void parsesManifest() {
        LauncherUpdate update = LauncherUpdate.parse(MANIFEST);

        assertEquals("1.1.0", update.version());
        assertEquals("Bug fixes", update.notes());
        assertEquals("https://example.com/launcher.zip", update.packageUrl());
        assertEquals("abc123", update.sha256());
        assertEquals(42, update.size());
    }

    @Test
    void rejectsBrokenManifests() {
        assertThrows(IllegalArgumentException.class,
                () -> LauncherUpdate.parse("{ broken"));
        assertThrows(IllegalArgumentException.class,
                () -> LauncherUpdate.parse("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> LauncherUpdate.parse(
                        "{\"version\": \"1.1.0\"}"));
    }
}

