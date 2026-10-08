package org.example.launcher.application.launch;

import java.util.Objects;

/**
 * Следит за процессом Minecraft: завершился ли, код завершения,
 * вывод и время работы. Блокирует вызывающий поток до выхода игры.
 */
public class GameProcessMonitor {

    public GameProcessMonitor() {
    }

    /**
     * Ждёт завершения процесса и возвращает код выхода.
     * Отдельно определяет случай, когда игра упала практически сразу.
     */
    public int monitor(GameProcess process) throws InterruptedException {
        Objects.requireNonNull(process, "process");
        return process.process().waitFor();
    }
}
