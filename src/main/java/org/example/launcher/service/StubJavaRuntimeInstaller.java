package org.example.launcher.service;

import java.nio.file.Path;

import org.example.launcher.model.JavaRuntime;

/**
 * Stub implementation of {@link JavaRuntimeInstaller}.
 * <p>
 * Always reports failure. This exists so that the launcher can be wired
 * up with the full Java resolution + installation pipeline now, and a
 * real implementation can replace this class later without changing any
 * call sites.
 */
public class StubJavaRuntimeInstaller implements JavaRuntimeInstaller {

    @Override
    public JavaRuntime install(String component, Path targetDir) {
        throw new UnsupportedOperationException(
                "Automatic Java installation is not yet implemented. "
                + "Please install Java " + component + " manually.");
    }

    @Override
    public JavaRuntime install(int requiredMajor, Path targetDir) {
        throw new UnsupportedOperationException(
                "Automatic Java installation is not yet implemented. "
                + "Please install Java " + requiredMajor + "+ manually.");
    }
}
