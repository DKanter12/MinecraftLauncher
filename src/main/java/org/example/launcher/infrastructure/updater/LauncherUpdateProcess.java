package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Отдельный процесс обновления: работающий лаунчер не может заменить
 * собственный заблокированный файл, поэтому замену выполняет скрипт
 * после выхода лаунчера, а затем запускает новую версию.
 */
public final class LauncherUpdateProcess {

    private LauncherUpdateProcess() {
    }

    /**
     * Записывает скрипт обновляльщика и запускает его отдельно.
     * Вызывающая сторона обязана сразу выйти, чтобы освободить файлы.
     *
     * @return путь скрипта обновляльщика
     */
    public static Path applyUpdate(UpdateService service,
                                   UpdateApplier.ApplyPlan plan, long pid)
            throws IOException {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(plan, "plan");
        Path updater = UpdateApplier.writeUpdater(service, plan);
        UpdateApplier.launchAndExit(updater, pid);
        return updater;
    }
}
