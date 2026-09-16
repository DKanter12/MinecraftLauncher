package org.example.launcher.distribution;

/**
 * Compares dot-separated build versions such as {@code 1.0.0} and
 * {@code 1.1.0}. Each segment is compared numerically when both sides
 * are numeric, otherwise lexicographically; a version with additional
 * segments is newer when the shared prefix is equal ({@code 1.0} &lt;
 * {@code 1.0.1}).
 */
public final class BuildVersions {

    private BuildVersions() {
    }

    /**
     * @param a first version, e.g. {@code 1.1.0}
     * @param b second version, e.g. {@code 1.0.0}
     * @return a negative value when {@code a} is older than {@code b},
     *         zero when equal, a positive value when {@code a} is newer
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
     * @param candidate    version offered by the server, e.g. {@code 1.1.0}
     * @param installed    locally installed version, e.g. {@code 1.0.0}
     * @return true when {@code candidate} is strictly newer than
     *         {@code installed} — an update should be offered
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
            a = "0"; // missing segment counts as zero: "1.0" == "1.0.0" < "1.0.1"
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
