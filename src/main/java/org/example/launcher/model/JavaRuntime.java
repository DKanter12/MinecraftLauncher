package org.example.launcher.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a Java runtime discovered on the local system.
 * <p>
 * Instances are produced by {@code JavaDetector} implementations and
 * consumed by {@code JavaResolutionService} to select the appropriate
 * runtime for launching a given Minecraft version.
 */
public final class JavaRuntime {

    /**
     * Where the runtime was discovered.
     */
    public enum Source {
        /** Discovered via the {@code JAVA_HOME} environment variable. */
        JAVA_HOME,
        /** Discovered by scanning the system {@code PATH}. */
        PATH,
        /** Discovered via the Windows registry. */
        REGISTRY,
        /** Discovered in a well-known installation directory. */
        COMMON_LOCATION,
        /** Installed by this launcher into the managed runtimes directory. */
        MANAGED,
        /** Provided explicitly by the user. */
        CUSTOM
    }

    private final Path javaExecutable;
    private final int majorVersion;
    private final Source source;
    private final String vendor;

    public JavaRuntime(Path javaExecutable, int majorVersion, Source source, String vendor) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable);
        this.majorVersion = majorVersion;
        this.source = Objects.requireNonNull(source);
        this.vendor = vendor;
    }

    public JavaRuntime(Path javaExecutable, int majorVersion, Source source) {
        this(javaExecutable, majorVersion, source, null);
    }

    /** Absolute path to the {@code java} (or {@code java.exe}) executable. */
    public Path javaExecutable() {
        return javaExecutable;
    }

    /** Major Java version, e.g. {@code 17} or {@code 21}. */
    public int majorVersion() {
        return majorVersion;
    }

    public Source source() {
        return source;
    }

    public Optional<String> vendor() {
        return Optional.ofNullable(vendor);
    }

    /**
     * Whether this runtime satisfies the given minimum major version.
     */
    public boolean satisfies(int requiredMajor) {
        return majorVersion >= requiredMajor;
    }

    @Override
    public String toString() {
        return "JavaRuntime{exe=" + javaExecutable + ", major=" + majorVersion
                + ", source=" + source
                + (vendor != null ? ", vendor='" + vendor + "'" : "") + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JavaRuntime other)) return false;
        return majorVersion == other.majorVersion
                && source == other.source
                && javaExecutable.equals(other.javaExecutable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaExecutable, majorVersion, source);
    }
}
