package org.example.launcher.service;

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
     * Builds launch arguments for the given context.
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
}
