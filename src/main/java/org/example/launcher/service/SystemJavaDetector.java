package org.example.launcher.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.util.OsDetector;

/**
 * Реализация {@link JavaDetector} по умолчанию, сканирующая систему хоста
 * в поисках установленных рантаймов Java следующими способами (по порядку):
 * <ol>
 *   <li>переменная окружения <b>JAVA_HOME</b></li>
 *   <li><b>PATH</b> — поиск {@code java} в системном PATH</li>
 *   <li><b>реестр Windows</b> — запрос {@code HKLM\SOFTWARE\JavaSoft}
 *       (записи JDK / JRE)</li>
 *   <li><b>Типовые каталоги установок</b> — сканирование общеизвестных
 *       мест для каждой ОС (например, {@code C:\Program Files\Java},
 *       {@code /usr/lib/jvm}, {@code /Library/Java/JavaVirtualMachines})</li>
 * </ol>
 * Дублирующиеся рантаймы (один и тот же путь к исполняемому файлу) удаляются.
 */
public class SystemJavaDetector implements JavaDetector {

    private static final Pattern JAVA_VERSION_PATTERN =
            Pattern.compile("version \"(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    @Override
    public List<JavaRuntime> detectInstalledRuntimes() {
        Map<Path, JavaRuntime> runtimes = new LinkedHashMap<>();

        // 1. JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            JavaRuntime rt = detectFromPath(Path.of(javaHome), JavaRuntime.Source.JAVA_HOME);
            if (rt != null) {
                runtimes.putIfAbsent(rt.javaExecutable(), rt);
            }
        }

        // 2. PATH
        findOnPath(runtimes);

        // 3. Платформенный поиск
        switch (OsDetector.current()) {
            case WINDOWS -> {
                scanCommonLocations(runtimes);
                scanWindowsRegistry(runtimes);
            }
            case LINUX -> scanCommonLocations(runtimes);
            case OSX -> scanCommonLocations(runtimes);
            default -> { /* ничего дополнительно */ }
        }

        // 4. Управляемые лаунчером рантаймы (~/.minecraft/java-runtimes/)
        scanManagedRuntimes(runtimes);

        return new ArrayList<>(runtimes.values());
    }

    @Override
    public JavaRuntime detectFromPath(Path homeDir, JavaRuntime.Source source) {
        if (homeDir == null) {
            return null;
        }

        Path binDir = homeDir.resolve("bin");
        String exeName = OsDetector.current() == OsDetector.Os.WINDOWS ? "java.exe" : "java";
        Path javaExe = binDir.resolve(exeName);

        if (!Files.isRegularFile(javaExe)) {
            // В некоторых раскладках JRE может не быть bin/java; пробуем сам home
            javaExe = homeDir.resolve(exeName);
            if (!Files.isRegularFile(javaExe)) {
                return null;
            }
        }

        Optional<Integer> version = probeVersion(javaExe);
        if (version.isEmpty()) {
            return null;
        }

        return new JavaRuntime(javaExe.toAbsolutePath().normalize(), version.get(), source);
    }

    /**
     * Запускает {@code java -version} и извлекает major-версию из stdout.
     */
    private Optional<Integer> probeVersion(Path javaExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    javaExe.toString(), "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = JAVA_VERSION_PATTERN.matcher(line);
                    if (m.find()) {
                        int major = parseMajor(m);
                        if (major > 0) {
                            process.waitFor();
                            return Optional.of(major);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Не удалось опросить этот java — пропустить его
        }
        return Optional.empty();
    }

    /**
     * Извлекает major-версию из совпадения регулярного выражения.
     * <p>
     * Версии до Java 9 выглядят как {@code 1.8.0_42} → major 8.<br>
     * Версии Java 9+ выглядят как {@code 17.0.1} или {@code 21} → major 17/21.
     */
    private int parseMajor(Matcher m) {
        int first = Integer.parseInt(m.group(1));
        if (first == 1 && m.group(2) != null) {
            return Integer.parseInt(m.group(2));
        }
        return first;
    }

    private void findOnPath(Map<Path, JavaRuntime> runtimes) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return;
        }

        String separator = OsDetector.current() == OsDetector.Os.WINDOWS ? ";" : ":";
        String exeName = OsDetector.current() == OsDetector.Os.WINDOWS ? "java.exe" : "java";

        for (String dir : pathEnv.split(separator)) {
            if (dir.isBlank()) continue;
            Path exe = Path.of(dir, exeName);
            if (Files.isRegularFile(exe)) {
                // Разрешить симлинк и найти фактический JAVA_HOME
                Path home = exe.getParent() != null ? exe.getParent().getParent() : null;
                if (home != null) {
                    JavaRuntime rt = detectFromPath(home, JavaRuntime.Source.PATH);
                    if (rt != null) {
                        runtimes.putIfAbsent(rt.javaExecutable(), rt);
                    }
                } else {
                    // Опросить напрямую
                    Optional<Integer> ver = probeVersion(exe);
                    ver.ifPresent(v -> {
                        JavaRuntime rt = new JavaRuntime(exe.toAbsolutePath().normalize(), v,
                                JavaRuntime.Source.PATH);
                        runtimes.putIfAbsent(rt.javaExecutable(), rt);
                    });
                }
            }
        }
    }

    private void scanCommonLocations(Map<Path, JavaRuntime> runtimes) {
        List<Path> dirs = commonLocations();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (var entries = Files.list(dir)) {
                entries.filter(Files::isDirectory)
                        .forEach(home -> {
                            JavaRuntime rt = detectFromPath(home,
                                    JavaRuntime.Source.COMMON_LOCATION);
                            if (rt != null) {
                                runtimes.putIfAbsent(rt.javaExecutable(), rt);
                            }
                        });
            } catch (IOException e) {
                // Пропустить нечитаемые каталоги
            }
        }
    }

    private void scanManagedRuntimes(Map<Path, JavaRuntime> runtimes) {
        Path runtimesDir = GameDirectory.defaultDirectory().javaRuntimesDir();
        if (!Files.isDirectory(runtimesDir)) return;
        try (var entries = Files.list(runtimesDir)) {
            entries.filter(Files::isDirectory).forEach(componentDir -> {
                try (var sub = Files.list(componentDir)) {
                    sub.filter(Files::isDirectory).forEach(home -> {
                        JavaRuntime rt = detectFromPath(home,
                                JavaRuntime.Source.MANAGED);
                        if (rt != null) {
                            runtimes.putIfAbsent(rt.javaExecutable(), rt);
                        }
                    });
                } catch (IOException e) {
                    // Пропустить
                }
            });
        } catch (IOException e) {
            // Пропустить
        }
    }

    private List<Path> commonLocations() {
        List<Path> dirs = new ArrayList<>();
        switch (OsDetector.current()) {
            case WINDOWS -> {
                dirs.add(Path.of("C:\\Program Files\\Java"));
                dirs.add(Path.of("C:\\Program Files (x86)\\Java"));
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.isBlank()) {
                    dirs.add(Path.of(localAppData, "Programs", "Eclipse Adoptium"));
                }
                dirs.add(Path.of(System.getProperty("user.home", ""), ".jdks"));
            }
            case LINUX -> {
                dirs.add(Path.of("/usr/lib/jvm"));
                dirs.add(Path.of("/usr/java"));
                dirs.add(Path.of(System.getProperty("user.home", ""),
                        ".sdkman", "candidates", "java"));
            }
            case OSX -> {
                dirs.add(Path.of("/Library/Java/JavaVirtualMachines"));
                dirs.add(Path.of("/System/Library/Java/JavaVirtualMachines"));
            }
            default -> { }
        }
        return dirs;
    }

    /**
     * Опрашивает реестр Windows в поисках установленных рантаймов Java.
     * <p>
     * Использует {@code reg query} для перечисления
     * {@code HKLM\SOFTWARE\JavaSoft\Java Development Kit},
     * {@code ...\JDK} и {@code ...\Java Runtime Environment}.
     */
    private void scanWindowsRegistry(Map<Path, JavaRuntime> runtimes) {
        String[] registryPaths = {
                "HKLM\\SOFTWARE\\JavaSoft\\Java Development Kit",
                "HKLM\\SOFTWARE\\JavaSoft\\JDK",
                "HKLM\\SOFTWARE\\JavaSoft\\Java Runtime Environment",
                "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\Java Development Kit",
                "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\JDK",
                "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\Java Runtime Environment"
        };

        for (String regPath : registryPaths) {
            scanRegistryKey(regPath, runtimes);
        }
    }

    private void scanRegistryKey(String regPath, Map<Path, JavaRuntime> runtimes) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", regPath, "/s");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("JavaHome") || line.startsWith("InstallationDirectory")) {
                        String value = extractRegValue(line);
                        if (value != null && !value.isBlank()) {
                            JavaRuntime rt = detectFromPath(Path.of(value),
                                    JavaRuntime.Source.REGISTRY);
                            if (rt != null) {
                                runtimes.putIfAbsent(rt.javaExecutable(), rt);
                            }
                        }
                    }
                }
            }
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // Запрос к реестру не удался — пропустить
        }
    }

    private String extractRegValue(String line) {
        int idx = line.indexOf("REG_SZ");
        if (idx < 0) return null;
        return line.substring(idx + "REG_SZ".length()).trim();
    }
}
