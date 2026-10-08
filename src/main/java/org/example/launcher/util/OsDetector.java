package org.example.launcher.util;

/**
 * Определяет текущую операционную систему и отображает её на идентификатор
 * Mojang, используемый в метаданных версий (нативные библиотеки, правила ресурсов).
 */
public final class OsDetector {

    public enum Os {
        WINDOWS("windows"),
        LINUX("linux"),
        OSX("osx"),
        UNKNOWN("unknown");

        private final String mojangName;

        Os(String mojangName) {
            this.mojangName = mojangName;
        }

        public String mojangName() {
            return mojangName;
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
