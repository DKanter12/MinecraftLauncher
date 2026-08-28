package org.example.launcher.service;

import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.VersionMetadata;

/**
 * Resolves which Java runtime to use for launching a given Minecraft
 * version.
 * <p>
 * Implementations query a {@link JavaDetector} for available runtimes
 * and select the best match based on the version's
 * {@link JavaVersion} requirements. If no suitable runtime is found,
 * the result indicates whether one is missing entirely or merely
 * incompatible, allowing the caller to decide whether to trigger
 * automatic installation via a {@code JavaRuntimeInstaller}.
 */
public interface JavaResolutionService {

    /**
     * Resolves a Java runtime for the given Minecraft version metadata.
     *
     * @param metadata the version to launch
     * @return resolution result (never {@code null})
     */
    JavaResolutionResult resolve(VersionMetadata metadata);

    /**
     * Resolves a Java runtime for the given minimum major version.
     * <p>
     * This overload is useful when the version metadata does not
     * specify a {@link JavaVersion} (legacy versions) and the caller
     * supplies a sensible default (e.g. 8).
     *
     * @param requiredMajor the minimum major Java version required
     * @return resolution result (never {@code null})
     */
    JavaResolutionResult resolve(int requiredMajor);
}
