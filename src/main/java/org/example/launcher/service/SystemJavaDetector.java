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
 * Default {@link JavaDetector} that scans the host system for installed
 * Java runtimes using the following strategies (in order):
 * <ol>
 *   <li><b>JAVA_HOME</b> environment variable</li>
 *   <li><b>PATH</b> — locates {@code java} on the system PATH</li>
 *   <li><b>Windows registry</b> — queries {@code HKLM\SOFTWARE\JavaSoft}
 *       (JDK / JRE entries)</li>
 *   <li><b>Common installation directories</b> — scans well-known
 *       locations per OS (e.g. {@code C:\Program Files\Java},
 *       {@code /usr/lib/jvm}, {@code /Library/Java/JavaVirtualMachines})</li>
 * </ol>
 * Duplicate runtimes (same executable path) are de-duplicated.
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

        // 3. Platform-specific detection
        switch (OsDetector.current()) {
            case WINDOWS -> {
                scanCommonLocations(runtimes);
                scanWindowsRegistry(runtimes);
            }
            case LINUX -> scanCommonLocations(runtimes);
            case OSX -> scanCommonLocations(runtimes);
            default -> { /* nothing extra */ }
        }

        // 4. Launcher-managed runtimes (~/.minecraft/java-runtimes/)
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
            // Some JRE layouts may not have bin/java; try the home itself
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
     * Runs {@code java -version} and parses the major version from stdout.
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
        } catch (IOException | InterruptedException e) {
            // Cannot probe this java — skip it
        }
        return Optional.empty();
    }

    /**
     * Parses the major version from a regex match.
     * <p>
     * Pre-Java 9 versions look like {@code 1.8.0_42} → major 8.<br>
     * Java 9+ versions look like {@code 17.0.1} or {@code 21} → major 17/21.
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
                // Resolve symlink and find the actual JAVA_HOME
                Path home = exe.getParent() != null ? exe.getParent().getParent() : null;
                if (home != null) {
                    JavaRuntime rt = detectFromPath(home, JavaRuntime.Source.PATH);
                    if (rt != null) {
                        runtimes.putIfAbsent(rt.javaExecutable(), rt);
                    }
                } else {
                    // Probe directly
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
                // Skip unreadable directories
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
                    // Skip
                }
            });
        } catch (IOException e) {
            // Skip
        }
    }

    private List<Path> commonLocations() {
        List<Path> dirs = new ArrayList<>();
        switch (OsDetector.current()) {
            case WINDOWS -> {
                dirs.add(Path.of("C:\\Program Files\\Java"));
                dirs.add(Path.of("C:\\Program Files (x86)\\Java"));
                dirs.add(Path.of(System.getenv("LOCALAPPDATA") != null
                        ? System.getenv("LOCALAPPDATA") + "\\Programs\\Eclipse Adoptium"
                        : ""));
                dirs.add(Path.of(System.getProperty("user.home", ""),
                        ".jdks"));
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
     * Queries the Windows registry for installed Java runtimes.
     * <p>
     * Uses {@code reg query} to enumerate
     * {@code HKLM\SOFTWARE\JavaSoft\Java Development Kit},
     * {@code ...\JDK}, and {@code ...\Java Runtime Environment}.
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
        } catch (IOException | InterruptedException e) {
            // Registry query failed — skip
        }
    }

    private String extractRegValue(String line) {
        int idx = line.indexOf("REG_SZ");
        if (idx < 0) return null;
        return line.substring(idx + "REG_SZ".length()).trim();
    }
}
