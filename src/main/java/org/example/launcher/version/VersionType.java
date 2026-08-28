package org.example.launcher.version;

/**
 * Represents a type of Minecraft version (e.g. Release, Snapshot).
 * <p>
 * Implementations may be enum constants, records, or any other class,
 * which makes the system extensible — new version types can be added
 * without modifying existing code. Register custom implementations
 * via {@link VersionTypeRegistry#register(VersionType)}.
 */
public interface VersionType {

    /**
     * Machine-readable identifier as it appears in the Mojang manifest
     * (e.g. {@code "release"}, {@code "snapshot"}).
     */
    String id();

    /**
     * Human-readable name for display in the UI.
     */
    String displayName();

    /**
     * Whether versions of this type are considered stable / production-ready.
     */
    boolean isStable();
}
