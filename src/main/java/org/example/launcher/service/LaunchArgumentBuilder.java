package org.example.launcher.service;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchArguments;
import org.example.launcher.model.VersionMetadata;

/**
 * Builds the full launch command-line for a Minecraft version.
 * <p>
 * Takes version metadata, the local game directory layout, the
 * player's profile, and the resolved Java runtime, and produces
 * a {@link LaunchArguments} ready to be executed.
 * <p>
 * The builder handles both the modern structured argument format
 * (1.13+) and the legacy {@code minecraftArguments} string (pre-1.13),
 * replacing all Mojang placeholders with concrete values.
 */
public interface LaunchArgumentBuilder {

    /**
     * Builds launch arguments for the given context, running the game
     * inside the storage root itself.
     *
     * @param metadata  the version metadata
     * @param gameDir   the game directory layout
     * @param profile   the player profile
     * @param javaRuntime the resolved Java runtime
     * @return fully resolved launch arguments
     */
    LaunchArguments build(VersionMetadata metadata,
                          GameDirectory gameDir,
                          GameProfile profile,
                          JavaRuntime javaRuntime);

    /**
     * Builds launch arguments with a separate runtime directory and
     * additional JVM arguments.
     * <p>
     * Used for modded profiles: shared files (client JAR, libraries,
     * assets, natives) are resolved from {@code gameDir} (the storage
     * root), while the game process runs inside
     * {@code runtimeDirectory} — the profile's own directory holding
     * its {@code mods/}, {@code config/}, {@code saves/} etc. The
     * {@code ${game_directory}} placeholder and the process working
     * directory point at the runtime directory; profile-specific JVM
     * arguments (e.g. {@code -Xmx4G}) are appended.
     *
     * @param metadata        the version metadata
     * @param gameDir         the storage game directory layout
     * @param profile         the player profile
     * @param javaRuntime     the resolved Java runtime
     * @param runtimeDirectory the directory the game runs in (mods,
     *                        saves, config live here)
     * @param extraJvmArgs    profile-specific JVM arguments (may be
     *                        empty)
     * @return fully resolved launch arguments
     */
    default LaunchArguments build(VersionMetadata metadata,
                                  GameDirectory gameDir,
                                  GameProfile profile,
                                  JavaRuntime javaRuntime,
                                  Path runtimeDirectory,
                                  List<String> extraJvmArgs) {
        // Default: ignore the extended context (backwards compatibility)
        return build(metadata, gameDir, profile, javaRuntime);
    }
}
