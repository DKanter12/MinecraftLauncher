package org.example.launcher.domain.port;

/**
 * Порт политики повторов. Значение больше не разбросано по коду —
 * один объект на весь пайплайн скачивания.
 */
public interface RetryPolicy {

    /** Сколько всего попыток делать (включая первую). */
    int maxAttempts();

    /** Стоит ли пробовать ещё раз после {@code attempt}-й неудачи (нумерация с 1). */
    default boolean shouldRetry(int attempt) {
        return attempt < maxAttempts();
    }
}
