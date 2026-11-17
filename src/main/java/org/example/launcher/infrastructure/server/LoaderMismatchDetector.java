package org.example.launcher.infrastructure.server;

import java.util.List;
import java.util.Optional;

/**
 * Распознаёт в выводе игры краши из-за несоответствия загрузчика/модов: экраны
 * ошибок загрузки Forge, отсутствующие зависимости и сбои Mixin
 * означают, что моды отвергают текущую версию загрузчика, поэтому
 * лаунчер показывает подсказку попробовать более старую версию,
 * а не общий отчёт о краше.
 * <p>
 * Эвристика по построению — сопоставляются известные фрагменты, а не точные
 * версии, — поэтому вызывающая сторона считает совпадение как «вероятно,
 * несоответствие».
 */
public final class LoaderMismatchDetector {

    /** Что совпало, для строк статуса и отчётов. */
    public record MismatchEvidence(String fragment, String excerpt) {
    }

    private static final List<String> FRAGMENTS = List.of(
            "has failed to load correctly",
            "errors during loading",
            "missing or unsupported mandatory dependencies",
            "missing mandatory dependencies",
            "unsupported mandatory dependencies",
            "missing mods",
            "mixin apply failed",
            "critical injection failure",
            "mixin.injection",
            "unsupported mod",
            "modloadingexception");

    private LoaderMismatchDetector() {
    }

    /**
     * @param log объединённый вывод игры (stdout + stderr), может быть
     *            {@code null}
     * @return первый найденный фрагмент несоответствия, или пусто
     */
    public static Optional<MismatchEvidence> detect(String log) {
        if (log == null || log.isBlank()) {
            return Optional.empty();
        }
        String lower = log.toLowerCase();
        for (String fragment : FRAGMENTS) {
            int at = lower.indexOf(fragment);
            if (at >= 0) {
                int start = Math.max(0, at - 80);
                int end = Math.min(log.length(), at + fragment.length() + 80);
                String excerpt = log.substring(start, end)
                        .replaceAll("\\s+", " ").trim();
                return Optional.of(new MismatchEvidence(fragment, excerpt));
            }
        }
        return Optional.empty();
    }
}
