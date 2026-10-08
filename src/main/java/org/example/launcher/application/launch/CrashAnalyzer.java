package org.example.launcher.application.launch;

import java.util.List;
import java.util.Optional;

import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.i18n.Lang;
import org.example.launcher.infrastructure.server.LoaderFallbackPolicy;
import org.example.launcher.infrastructure.server.LoaderMismatchDetector;

/**
 * Анализ падения игры по коду выхода и логам.
 * Чистая логика без UI и сети: на входе строки, на выходе {@link CrashReport}.
 */
public final class CrashAnalyzer {

    private static final List<String> OUT_OF_MEMORY_FRAGMENTS = List.of(
            "outofmemoryerror", "out of memory", "java heap space");

    private static final List<String> WRONG_JAVA_FRAGMENTS = List.of(
            "unsupportedclassversionerror", "unsupported major.minor");

    private static final int LOG_TAIL_LINES = 30;

    private CrashAnalyzer() {
    }

    /**
     * Разбирает падение по объединённому выводу игры.
     *
     * @param exitCode код выхода процесса
     * @param stdout   вывод stdout (может быть пустым)
     * @param stderr   вывод stderr (может быть пустым)
     */
    public static CrashReport analyze(int exitCode, String stdout, String stderr) {
        String err = stderr == null ? "" : stderr;
        String out = stdout == null ? "" : stdout;
        String log = err.isBlank() ? out : err;
        String lower = log.toLowerCase();

        if (containsAny(lower, WRONG_JAVA_FRAGMENTS)) {
            return report(CrashCategory.WRONG_JAVA, exitCode, log,
                    Lang.tr("launch.crash.reason.java", excerpt(log)),
                    Lang.tr("launch.crash.suggest.java"));
        }
        if (containsAny(lower, OUT_OF_MEMORY_FRAGMENTS)) {
            return report(CrashCategory.OUT_OF_MEMORY, exitCode, log,
                    Lang.tr("launch.crash.reason.memory"),
                    Lang.tr("launch.crash.suggest.memory"));
        }
        Optional<LoaderMismatchDetector.MismatchEvidence> mismatch =
                LoaderMismatchDetector.detect(log);
        if (mismatch.isPresent()) {
            return report(CrashCategory.WRONG_LOADER, exitCode, log,
                    Lang.tr("launch.crash.reason.loader",
                            mismatch.get().fragment()),
                    Lang.tr("launch.crash.suggest.loader"));
        }
        return report(CrashCategory.UNKNOWN, exitCode, log,
                Lang.tr("launch.crash.reason.unknown"), null);
    }

    /**
     * Подбирает следующую более старую версию загрузчика после
     * падения из-за несоответствия. Пусто, если пробовать больше нечего.
     */
    public static Optional<ModLoaderVersion> suggestOlderLoader(
            List<ModLoaderVersion> newestFirst, String failedLoaderVersion) {
        return LoaderFallbackPolicy.nextOlder(newestFirst, failedLoaderVersion);
    }

    private static CrashReport report(CrashCategory category, int exitCode,
                                      String log, String reason, String suggestion) {
        return new CrashReport(category, exitCode, reason,
                Optional.ofNullable(suggestion), tail(log, LOG_TAIL_LINES));
    }

    private static boolean containsAny(String text, List<String> fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static String excerpt(String log) {
        String flat = log.replaceAll("\\s+", " ").trim();
        return flat.length() > 160 ? flat.substring(0, 160) + "..." : flat;
    }

    static String tail(String log, int lines) {
        if (log == null || log.isBlank()) {
            return "";
        }
        List<String> all = log.lines().toList();
        return String.join("\n", all.subList(
                Math.max(0, all.size() - lines), all.size()));
    }
}
