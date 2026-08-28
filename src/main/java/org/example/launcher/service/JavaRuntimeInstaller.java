package org.example.launcher.service;

import java.nio.file.Path;

import org.example.launcher.model.JavaRuntime;

/**
 * Installs Java runtimes into the launcher's managed directory.
 * <p>
 * This interface is a seam for future functionality: when
 * {@link JavaResolutionService} reports that no suitable runtime is
 * available, the launcher can invoke an installer to download and
 * extract a compatible JRE (e.g. from Mojang's
 * {@code java-runtime-manifest} or Eclipse Adoptium).
 * <p>
 * The initial implementation will be a stub; a concrete implementation
 * can be added later without changing any call sites.
 */
public interface JavaRuntimeInstaller {

    /**
     * Installs a Java runtime for the given Mojang component identifier.
     * <p>
     * Mojang's runtime manifest maps component names like
     * {@code "java-runtime-gamma"} to platform-specific downloads.
     *
     * @param component the Mojang runtime component (e.g.
     *                  {@code "java-runtime-gamma"})
     * @param targetDir the directory to install into
     * @return the installed {@link JavaRuntime}, or {@code null} if
     *         installation failed
     * @throws Exception if an unrecoverable error occurs
     */
    JavaRuntime install(String component, Path targetDir) throws Exception;

    /**
     * Installs a Java runtime with at least the given major version.
     * <p>
     * This is used when the version metadata does not carry a Mojang
     * component identifier (legacy versions).
     *
     * @param requiredMajor the minimum major version (e.g. 17)
     * @param targetDir     the directory to install into
     * @return the installed {@link JavaRuntime}, or {@code null} if
     *         installation failed
     * @throws Exception if an unrecoverable error occurs
     */
    JavaRuntime install(int requiredMajor, Path targetDir) throws Exception;
}
