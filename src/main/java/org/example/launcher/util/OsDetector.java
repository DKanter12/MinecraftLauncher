package org.example.launcher.util;

/**
 * Detects the current operating system and maps it to the Mojang
 * identifier used in version metadata (library natives, asset rules).
 */
public final class OsDetector {

    public enum Os {
        WINDOWS("windows", "win"),
        LINUX("linux", "linux"),
        OSX("osx", "mac"),
        UNKNOWN("unknown", "unknown");

        private final String mojangName;
        private final String shortName;

        Os(String mojangName, String shortName) {
            this.mojangName = mojangName;
            this.shortName = shortName;
        }

        public String mojangName() {
            return mojangName;
        }

        public String shortName() {
            return shortName;
        }
    }

    private static final Os CURRENT = detect();

    private OsDetector() {
    }

    public static Os current() {
        return CURRENT;
    }

    public static String mojangName() {
        return CURRENT.mojangName;
    }

    private static Os detect() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return Os.WINDOWS;
        }
        if (osName.contains("mac") || osName.contains("osx") || osName.contains("darwin")) {
            return Os.OSX;
        }
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return Os.LINUX;
        }
        return Os.UNKNOWN;
    }
}
