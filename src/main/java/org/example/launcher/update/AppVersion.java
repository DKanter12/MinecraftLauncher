package org.example.launcher.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Собственная версия лаунчера: что сейчас запущено и как она
 * соотносится с публикуемой в ветке main.
 * <p>
 * Запущенная версия берётся из {@code version.txt} рядом с
 * приложением (записывается обновляльщиком при каждом применении) и откатывается
 * к встроенной версии {@link #BUILT_IN} для запусков при разработке.
 */
public final class AppVersion {

    /** Версия, вшитая в эту сборку. */
    public static final String BUILT_IN = "v1.0";

    private AppVersion() {
    }

    /** @return запущенная версия лаунчера. */
    public static String current() {
        Path home = appHome();
        if (home != null) {
            Path marker = home.resolve("version.txt");
            try {
                if (Files.isRegularFile(marker)) {
                    String version = Files.readString(marker).trim();
                    if (!version.isEmpty()) {
                        return version;
                    }
                }
            } catch (Exception ignored) {
                // откат к встроенной версии
            }
        }
        return BUILT_IN;
    }

    /**
     * @return домашний каталог приложения (папка с запущенным jar или образом установки),
     *         или {@code null}, когда его нельзя определить (напр. запуски из IDE/Gradle)
     */
    public static Path appHome() {
        try {
            Path code = Paths.get(AppVersion.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(code)) {
                Path dir = code.getParent();
                if (dir != null
                        && "lib".equals(dir.getFileName().toString())
                        && dir.getParent() != null
                        && Files.isDirectory(dir.getParent().resolve("bin"))) {
                    return dir.getParent();
                }
                return dir;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Сравнивает версии с разделителями точка/дефис, допуская ведущий
     * {@code v} ({@code v1.10} &gt; {@code 1.9.0} &gt;
     * {@code 1.0-SNAPSHOT}).
     *
     * @return отрицательное, если {@code a} старее, ноль при равенстве,
     *         положительное, если {@code a} новее
     */
    public static int compare(String a, String b) {
        String[] partsA = split(a);
        String[] partsB = split(b);
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            String segA = i < partsA.length ? partsA[i] : "0";
            String segB = i < partsB.length ? partsB[i] : "0";
            int cmp = compareSegment(segA, segB);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /** @return true, если {@code candidate} строго новее. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    private static String[] split(String version) {
        if (version == null) {
            return new String[0];
        }
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            return new String[0];
        }
        return clean.split("[.\\-]");
    }

    private static int compareSegment(String a, String b) {
        boolean numericA = isNumeric(a);
        boolean numericB = isNumeric(b);
        if (numericA && numericB) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        if (numericA != numericB) {
            // Простое число бьёт тег: 1.0 > 1.0-SNAPSHOT,
            // а 1.0.1 > 1.0 сохраняется за счёт недостающего «0».
            return numericA ? 1 : -1;
        }
        return a.compareTo(b);
    }

    private static boolean isNumeric(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
