package org.example.launcher.application.launch;

import java.util.Optional;

/**
 * Разобранный результат падения игры: что случилось и что делать.
 * Чистые данные без UI — показ решает presentation.
 *
 * @param category   распознанная категория причины
 * @param exitCode   код выхода процесса
 * @param reason     человекочитаемая причина (уже локализована)
 * @param suggestion что попробовать дальше (пусто, если подсказки нет)
 * @param logTail    обрезанный хвост лога для деталей
 * @param fullLog    путь полного лога для кнопки «Открыть лог»
 *                   (пусто, если неизвестен)
 */
public record CrashReport(
        CrashCategory category,
        int exitCode,
        String reason,
        Optional<String> suggestion,
        String logTail,
        String fullLog) {

    /** Краткая форма без пути полного лога. */
    public CrashReport(CrashCategory category, int exitCode, String reason,
                       Optional<String> suggestion, String logTail) {
        this(category, exitCode, reason, suggestion, logTail, "");
    }
}
