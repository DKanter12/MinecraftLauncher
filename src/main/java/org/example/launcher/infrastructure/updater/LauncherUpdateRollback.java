package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Откат обновления: восстановление предыдущей версии,
 * если новая не смогла корректно установиться.
 * Старая версия не удаляется, пока новая не заменена успешно.
 */
public final class LauncherUpdateRollback {

    private static final String BACKUP_PREFIX = "backup-";

    private LauncherUpdateRollback() {
    }

    /**
     * Сохраняет резервную копию домашнего каталога приложения.
     * Старые копии, кроме новой, удаляются.
     *
     * @return каталог копии
     */
    public static Path backup(Path appHome, Path updatesDir, String version)
            throws IOException {
        Objects.requireNonNull(appHome, "appHome");
        Objects.requireNonNull(updatesDir, "updatesDir");
        Objects.requireNonNull(version, "version");
        if (!Files.isDirectory(appHome)) {
            throw new IOException(
                    "Application home not found: " + appHome);
        }
        Files.createDirectories(updatesDir);
        Path backupDir = updatesDir.resolve(BACKUP_PREFIX
                + LauncherUpdateInstaller.sanitizeVersion(version));
        LauncherUpdateInstaller.deleteTree(backupDir);
        copyTree(appHome, backupDir);
        pruneOldBackups(updatesDir, backupDir);
        return backupDir;
    }

    /**
     * Восстанавливает предыдущую версию из копии и запускает её.
     * Копия сохраняется, чтобы откат можно было повторить.
     */
    public static void rollback(Path backupDir, Path appHome)
            throws IOException {
        Objects.requireNonNull(backupDir, "backupDir");
        Objects.requireNonNull(appHome, "appHome");
        if (!Files.isDirectory(backupDir)) {
            throw new IOException("No update backup found: " + backupDir);
        }
        copyTree(backupDir, appHome);
    }

    /** Есть ли сохранённая копия для отката. */
    public static boolean hasBackup(Path updatesDir, String version) {
        if (updatesDir == null || version == null) {
            return false;
        }
        return Files.isDirectory(updatesDir.resolve(BACKUP_PREFIX
                + LauncherUpdateInstaller.sanitizeVersion(version)));
    }

    private static void copyTree(Path source, Path target)
            throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path from : stream.sorted().toList()) {
                Path to = target.resolve(source.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Path parent = to.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(from, to,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void pruneOldBackups(Path updatesDir, Path keep)
            throws IOException {
        if (!Files.isDirectory(updatesDir)) {
            return;
        }
        try (var stream = Files.list(updatesDir)) {
            for (Path child : stream.toList()) {
                String name = child.getFileName().toString();
                if (!child.equals(keep) && name.startsWith(BACKUP_PREFIX)) {
                    LauncherUpdateInstaller.deleteTree(child);
                }
            }
        }
    }

    static String backupDirName(String version) {
        return BACKUP_PREFIX
                + LauncherUpdateInstaller.sanitizeVersion(version);
    }
}
