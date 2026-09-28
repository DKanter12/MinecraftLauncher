package org.example.launcher.util;

import java.util.List;

/**
 * Разбор вводимых пользователем аргументов JVM (по одному на строку).
 */
public final class JvmArgs {

    private JvmArgs() {
    }

    /**
     * Разбивает произвольный текст на обрезанные непустые аргументы.
     * Принимает любой разделитель строк; никогда не возвращает {@code null}.
     *
     * @param text исходное содержимое текстового поля, может быть {@code null}
     * @return неизменяемый список аргументов
     */
    public static List<String> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
