package org.example.launcher.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.VersionMetadata;

/**
 * Default {@link JavaResolutionService}.
 * <p>
 * Delegates runtime discovery to a {@link JavaDetector} and selects
 * the runtime with the lowest major version that still satisfies the
 * requirement (to avoid unnecessarily running on a much newer JDK).
 * <p>
 * If the version metadata does not carry a {@link JavaVersion}, a
 * configurable fallback major version is used (default: 8, since
 * legacy Minecraft versions were designed for Java 8).
 * <p>
 * A user-specified custom Java path can be set via
 * {@link #setCustomJavaPath(Path)}; when set, the detector probes
 * that path and the resulting runtime is included in the candidate
 * list alongside any system-detected runtimes.
 */
public class DefaultJavaResolutionService implements JavaResolutionService {

    private final JavaDetector detector;
    private final int fallbackMajor;
    private Path customJavaPath;

    public DefaultJavaResolutionService(JavaDetector detector) {
        this(detector, 8);
    }

    public DefaultJavaResolutionService(JavaDetector detector, int fallbackMajor) {
        this.detector = detector;
        this.fallbackMajor = fallbackMajor;
    }

    /**
     * Sets a user-specified Java executable path to include in
     * future resolution calls. Pass {@code null} to clear.
     *
     * @param javaExe path to a {@code java}/{@code java.exe} executable
     */
    public void setCustomJavaPath(Path javaExe) {
        this.customJavaPath = javaExe;
    }

    @Override
    public JavaResolutionResult resolve(VersionMetadata metadata) {
        int required = metadata.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(fallbackMajor);
        return resolve(required);
    }

    @Override
    public JavaResolutionResult resolve(int requiredMajor) {
        List<JavaRuntime> runtimes = new ArrayList<>(detector.detectInstalledRuntimes());

        // Add custom Java path if set
        if (customJavaPath != null) {
            JavaRuntime custom = detector.detectFromPath(
                    customJavaPath.getParent().getParent(),
                    JavaRuntime.Source.CUSTOM);
            if (custom != null) {
                runtimes.add(custom);
            }
        }

        if (runtimes.isEmpty()) {
            return JavaResolutionResult.notFound(
                    "No Java runtime was found on this system.");
        }

        // Old Minecraft versions (beta, alpha, 1.6–1.12) use
        // net.minecraft.launchwrapper.Launch which casts the system
        // ClassLoader to URLClassLoader — that cast fails on Java 9+.
        // For those versions (majorVersion <= 8) we must use Java 8
        // exactly, not just "8 or higher".
        boolean exactMatch = requiredMajor <= 8;

        var compatible = runtimes.stream()
                .filter(rt -> exactMatch
                        ? rt.majorVersion() == requiredMajor
                        : rt.satisfies(requiredMajor))
                .sorted(Comparator.comparingInt(JavaRuntime::majorVersion))
                .toList();

        if (compatible.isEmpty()) {
            if (exactMatch) {
                int bestAvailable = runtimes.stream()
                        .mapToInt(JavaRuntime::majorVersion)
                        .max().orElse(0);
                return JavaResolutionResult.incompatible(
                        "This version requires Java " + requiredMajor
                        + " exactly (launchwrapper is incompatible with Java 9+)."
                        + " The best available runtime is Java " + bestAvailable
                        + ". Please install Java 8 (e.g. from adoptium.net).");
            }
            int bestAvailable = runtimes.stream()
                    .mapToInt(JavaRuntime::majorVersion)
                    .max().orElse(0);
            return JavaResolutionResult.incompatible(
                    "Java " + requiredMajor + "+ is required, but the best "
                    + "available runtime is Java " + bestAvailable + ".");
        }

        return JavaResolutionResult.found(compatible.get(0));
    }
}
