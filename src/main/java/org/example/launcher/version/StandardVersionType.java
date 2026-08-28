package org.example.launcher.version;

/**
 * Standard Minecraft version types returned by the official Mojang manifest.
 * <p>
 * Each constant corresponds to the {@code "type"} field value in the
 * version manifest JSON. New constants can be appended here when Mojang
 * introduces additional types — existing code does not need to change.
 */
public enum StandardVersionType implements VersionType {

    RELEASE("release", "Release", true),
    SNAPSHOT("snapshot", "Snapshot", false),
    OLD_BETA("old_beta", "Beta", false),
    OLD_ALPHA("old_alpha", "Alpha", false),
    UNKNOWN("unknown", "Unknown", false);

    private final String id;
    private final String displayName;
    private final boolean stable;

    StandardVersionType(String id, String displayName, boolean stable) {
        this.id = id;
        this.displayName = displayName;
        this.stable = stable;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isStable() {
        return stable;
    }
}
