package org.example.launcher.service;

import java.nio.file.Path;
import java.util.List;

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
     * Launches the given Minecraft version, running the game inside
     * the storage root.
     *
     * @param metadata the version metadata
     * @param gameDir  the game directory layout
     * @param profile  the player profile
     * @return launch result (success or failure with details)
     */
    LaunchResult launch(VersionMetadata metadata,
                        GameDirectory gameDir,
                        GameProfile profile);

    /**
     * Launches the given Minecraft version inside a separate runtime
     * directory (modded profiles): shared files are resolved from
     * {@code gameDir}, while the process runs in
     * {@code runtimeDirectory} where the profile's {@code mods/},
     * {@code config/}, {@code saves/} etc. live.
     *
     * @param metadata         the version metadata
     * @param gameDir          the storage game directory layout
     * @param profile          the player profile
     * @param runtimeDirectory the directory the game runs in
     * @param extraJvmArgs     profile-specific JVM arguments (may be
     *                         empty)
     * @return launch result (success or failure with details)
     */
    default LaunchResult launch(VersionMetadata metadata,
                                GameDirectory gameDir,
                                GameProfile profile,
                                Path runtimeDirectory,
                                List<String> extraJvmArgs) {
        return launch(metadata, gameDir, profile);
    }
}
