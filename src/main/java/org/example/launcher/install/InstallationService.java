package org.example.launcher.install;

import java.io.IOException;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionMetadata;

/**
 * Orchestrates the full installation of a Minecraft version:
 * downloading the client JAR, all libraries, native libraries, and
 * all referenced assets, with hash-based skip-if-valid logic.
 * <p>
 * Shared libraries and assets are stored in common directories
 * managed by {@link GameDirectory} and are reused across versions
 * without re-downloading.
 * <p>
 * Implementations should report progress via
 * {@link InstallationProgress} and return an
 * {@link InstallationResult} summarising the outcome.
 */
public interface InstallationService {

    /**
     * Installs the given version to the specified game directory.
     *
     * @param version   the version to install (for metadata URL)
     * @param metadata  pre-fetched version metadata
     * @param gameDir   the game directory layout
     * @param progress  progress callback (use {@link InstallationProgress#NONE}
     *                  if not needed)
     * @return aggregate installation result
     * @throws IOException if a critical error prevents installation
     */
    InstallationResult install(MinecraftVersion version,
                               VersionMetadata metadata,
                               GameDirectory gameDir,
                               InstallationProgress progress) throws IOException;
}
