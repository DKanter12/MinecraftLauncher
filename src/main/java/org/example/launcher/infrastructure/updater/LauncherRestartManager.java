package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Перезапуск лаунчера с подготовленным обновлением.
 * Сохраняет резервную копию текущей версии: если новая не стартует,
 * {@link LauncherUpdateRollback} восстановит старую.
 */
public class LauncherRestartManager {

    public LauncherRestartManager() {
    }

    /**
     * Готовит применение обновления и запускает процесс обновления.
     * Пусто, когда раскладка приложения не распознана — тогда файлы
     * копируются вручную. Вызывающая сторона после успеха обязана выйти.
     *
     * @return путь скрипта обновляльщика либо пусто для ручного копирования
     */
    public Optional<Path> restartWithUpdate(UpdateService service,
                                            Path stageDir, String version,
                                            long pid) throws IOException {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(stageDir, "stageDir");
        Objects.requireNonNull(version, "version");
        Optional<UpdateApplier.ApplyPlan> plan =
                UpdateApplier.plan(stageDir, version);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        Path appHome = plan.get().appHome();
        Path updatesDir = stageDir.getParent();
        if (updatesDir != null && Files.isDirectory(appHome)) {
            LauncherUpdateRollback.backup(appHome, updatesDir, version);
        }
        try {
            Path updater = LauncherUpdateProcess.applyUpdate(
                    service, plan.get(), pid);
            return Optional.of(updater);
        } catch (IOException | RuntimeException e) {
            Path backupDir = updatesDir == null ? null : updatesDir.resolve(
                    LauncherUpdateRollback.backupDirName(version));
            if (backupDir != null && Files.isDirectory(backupDir)) {
                LauncherUpdateRollback.rollback(backupDir, appHome);
            }
            throw e;
        }
    }
}
