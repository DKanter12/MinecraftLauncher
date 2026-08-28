package org.example.launcher.service;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.LaunchResult;
import org.example.launcher.model.VersionMetadata;

/**
 * Orchestrates the full Minecraft launch sequence:
 * <ol>
 *   <li>Verify that all required files are present and intact</li>
 *   <li>Resolve a suitable Java runtime</li>
 *   <li>Build launch arguments from version metadata</li>
 *   <li>Start the Java process</li>
 *   <li>Wrap the process for monitoring</li>
 * </ol>
 * <p>
 * The caller receives a {@link LaunchResult} containing either a
 * running {@link org.example.launcher.model.MinecraftProcess} or an
 * error explaining what went wrong.
 */
public interface MinecraftLaunchService {

    /**
     * Launches the given Minecraft version.
     *
     * @param metadata the version metadata
     * @param gameDir  the game directory layout
     * @param profile  the player profile
     * @return launch result (success or failure with details)
     */
    LaunchResult launch(VersionMetadata metadata,
                        GameDirectory gameDir,
                        GameProfile profile);
}
