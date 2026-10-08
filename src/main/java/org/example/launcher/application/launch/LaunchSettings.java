package org.example.launcher.application.launch;

import java.util.List;

/**
 * Настройки конкретного запуска.
 * Память схлопнута в один лимит {@code -Xmx}: движок не поддерживает
 * {@code -Xms} (такие аргументы отбрасываются как конфликтующие),
 * поэтому отдельных минимума/максимума здесь нет.
 *
 * @param memoryMb    лимит памяти в МБ для {@code -Xmx};
 *                    {@code <= 0} — без явного лимита
 * @param extraJvmArgs доп. JVM-аргументы запуска
 */
public record LaunchSettings(
        int memoryMb,
        List<String> extraJvmArgs) {

    public LaunchSettings {
        extraJvmArgs = extraJvmArgs == null ? List.of()
                : List.copyOf(extraJvmArgs);
    }

    /** Настройки по умолчанию: без явного лимита и доп. аргументов. */
    public static LaunchSettings defaults() {
        return new LaunchSettings(0, List.of());
    }
}
