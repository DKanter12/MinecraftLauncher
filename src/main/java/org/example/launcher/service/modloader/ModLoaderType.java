package org.example.launcher.service.modloader;

/**
 * Identifies a supported game (instance) type.
 * <p>
 * {@link #VANILLA} is a pseudo loader type used by the unified
 * instance model: an instance of type VANILLA plays the unmodified
 * game, while the other types are mod loaders requiring a matching
 * {@link ModLoaderVersionProvider} and {@link ModLoaderInstaller}
 * registered in {@link ModLoaderRegistry}.
 */
public enum ModLoaderType {

    VANILLA("Vanilla"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
    QUILT("Quilt");

    private final String displayName;

    ModLoaderType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
