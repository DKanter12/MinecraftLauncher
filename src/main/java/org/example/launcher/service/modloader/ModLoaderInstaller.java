package org.example.launcher.service.modloader;

import java.io.IOException;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.VersionMetadata;

/**
 * Installs a mod loader for a specific vanilla Minecraft version,
 * fully automatic (no manual installer run required).
 * <p>
 * Implementations must:
 * <ol>
 *   <li>download the necessary loader files (and dependencies);</li>
 *   <li>create a launch configuration (local version JSON following
 *       the {@code versions/{id}/{id}.json} layout);</li>
 *   <li>ensure the vanilla base is installed;</li>
 *   <li>verify the installation (hash checks).</li>
 * </ol>
 */
public interface ModLoaderInstaller {

    /**
     * Installs the given mod loader version.
     *
     * @param vanillaVersion   the vanilla manifest entry for the target
     *                         Minecraft version
     * @param vanillaMetadata  pre-fetched vanilla metadata for that version
     * @param loader           the loader version to install
     * @param gameDir          the game directory layout
     * @param progress         progress callback
     * @return install result including the created version id
     * @throws IOException on critical failures (network, IO, installer
     *                     process errors)
     */
    ModLoaderInstallResult install(MinecraftVersion vanillaVersion,
                                   VersionMetadata vanillaMetadata,
                                   ModLoaderVersion loader,
                                   GameDirectory gameDir,
                                   InstallationProgress progress) throws IOException;

    /**
     * Result of a mod loader installation.
     *
     * @param versionId   the installed modded version id
     *                    (e.g. {@code fabric-loader-0.16.9-1.21.4})
     * @param fileResult  aggregate result of the file installation
     *                    (downloads + hash verification), or {@code null}
     *                    when the loader performs its own file management
     */
    record ModLoaderInstallResult(String versionId, InstallationResult fileResult) {

        public boolean isSuccessful() {
            return fileResult == null || fileResult.isSuccess();
        }

        public String summary() {
            return versionId + ": " + (fileResult != null ? fileResult.summary() : "OK");
        }
    }
}
