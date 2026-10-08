package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LauncherUpdateRollback")
class LauncherUpdateRollbackTest {

    private static Path appHome(Path dir) throws IOException {
        Path home = dir.resolve("app");
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("bin").resolve("app.jar"), "v1");
        Files.writeString(home.resolve("version.txt"), "1.1.0");
        return home;
    }

    @Test
    @DisplayName("backup copies the app and prunes older backups")
    void backup(@TempDir Path dir) throws IOException {
        Path home = appHome(dir);
        Path updates = dir.resolve("updates");
        Files.createDirectories(updates.resolve("backup-1.0.0"));
        Files.writeString(
                updates.resolve("backup-1.0.0").resolve("version.txt"), "1.0.0");

        Path backup = LauncherUpdateRollback.backup(home, updates, "1.2.0");

        assertTrue(Files.isRegularFile(backup.resolve("bin").resolve("app.jar")));
        assertEquals("v1", Files.readString(
                backup.resolve("bin").resolve("app.jar")));
        assertTrue(LauncherUpdateRollback.hasBackup(updates, "1.2.0"));
        assertFalse(Files.exists(updates.resolve("backup-1.0.0")),
                "older backups are pruned");
    }

    @Test
    @DisplayName("rollback restores files from the backup")
    void rollback(@TempDir Path dir) throws IOException {
        Path home = appHome(dir);
        Path updates = dir.resolve("updates");
        Path backup = LauncherUpdateRollback.backup(home, updates, "1.2.0");

        Files.writeString(home.resolve("bin").resolve("app.jar"), "broken");
        LauncherUpdateRollback.rollback(backup, home);

        assertEquals("v1",
                Files.readString(home.resolve("bin").resolve("app.jar")));
    }

    @Test
    @DisplayName("rollback without backup fails")
    void missing(@TempDir Path dir) {
        assertThrows(IOException.class, () -> LauncherUpdateRollback.rollback(
                dir.resolve("updates").resolve("backup-9.9.9"),
                dir.resolve("app")));
        assertFalse(LauncherUpdateRollback.hasBackup(
                dir.resolve("updates"), "9.9.9"));
    }
}
