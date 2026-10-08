package org.example.launcher.infrastructure.server;

/**
 * Сравнивает версии сборок с разделением точками, напр. {@code 1.0.0} и
 * {@code 1.1.0}. Каждый сегмент сравнивается численно, если обе стороны
 * числовые, иначе лексикографически; версия с дополнительными
 * сегментами новее при равном общем префиксе ({@code 1.0} &lt;
 * {@code 1.0.1}).
 */
public final class BuildVersions {

    private BuildVersions() {
    }

    /**
     * @param a первая версия, напр. {@code 1.1.0}
     * @param b вторая версия, напр. {@code 1.0.0}
     * @return отрицательное, если {@code a} старее {@code b},
     *         ноль при равенстве, положительное, если {@code a} новее
     */
    public static int compare(String a, String b) {
        String[] partsA = split(a);
        String[] partsB = split(b);
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            String segA = i < partsA.length ? partsA[i] : null;
            String segB = i < partsB.length ? partsB[i] : null;
            int cmp = compareSegment(segA, segB);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * @param candidate    версия, предлагаемая сервером, напр. {@code 1.1.0}
     * @param installed    локально установленная версия, напр. {@code 1.0.0}
     * @return true, если {@code candidate} строго новее
     *         {@code installed} — следует предложить обновление
     */
    public static boolean isNewer(String candidate, String installed) {
        return compare(candidate, installed) > 0;
    }

    private static String[] split(String version) {
        if (version == null || version.isBlank()) {
            return new String[0];
        }
        return version.trim().split("\\.");
    }

    private static int compareSegment(String a, String b) {
        if (a == null) {
            a = "0"; // отсутствующий сегмент считается нулём: "1.0" == "1.0.0" < "1.0.1"
        }
        if (b == null) {
            b = "0";
        }
        boolean numericA = isNumeric(a);
        boolean numericB = isNumeric(b);
        if (numericA && numericB) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
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
