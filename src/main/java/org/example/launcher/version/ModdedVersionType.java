package org.example.launcher.version;

/**
 * Version type for locally installed modded versions (Fabric, Forge,
 * NeoForge, Quilt). These versions never appear in the Mojang manifest;
 * they are produced by the launcher's mod loader installation and
 * discovered by scanning the local {@code versions/} directory.
 */
public final class ModdedVersionType implements VersionType {

    public static final ModdedVersionType INSTANCE = new ModdedVersionType();

    private ModdedVersionType() {
    }

    @Override
    public String id() {
        return "modded";
    }

    @Override
    public String displayName() {
        return "Modded";
    }

    @Override
    public boolean isStable() {
        return true;
    }
}
