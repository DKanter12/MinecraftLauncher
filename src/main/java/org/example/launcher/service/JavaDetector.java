package org.example.launcher.service;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.model.JavaRuntime;

/**
 * Detects Java runtime installations available on the host system.
 * <p>
 * Implementations scan environment variables, system paths, platform-specific
 * registries, and common installation directories to build a list of
 * available runtimes. This is the read-only "sensor" side of Java
 * resolution; the actual matching logic lives in
 * {@link JavaResolutionService}.
 * <p>
 * This interface is designed to be testable — implementations that
 * shell out to {@code java -version} or query the Windows registry can
 * be replaced with stubs in tests.
 */
public interface JavaDetector {

    /**
     * Scans the system for all discoverable Java runtimes.
     * <p>
     * The returned list is not ordered by preference; the
     * {@link JavaResolutionService} is responsible for selecting the
     * best match.
     *
     * @return a list of discovered runtimes (may be empty, never {@code null})
     */
    List<JavaRuntime> detectInstalledRuntimes();

    /**
     * Probes a single directory that is expected to be a JDK/JRE root
     * (i.e. it contains a {@code bin/java} or {@code bin/java.exe}).
     *
     * @param homeDir the candidate {@code JAVA_HOME}-style directory
     * @param source  the discovery source to attach to the result
     * @return a {@link JavaRuntime} if the directory is valid, or
     *         {@code null} if no executable was found or the version
     *         could not be determined
     */
    JavaRuntime detectFromPath(Path homeDir, JavaRuntime.Source source);
}
