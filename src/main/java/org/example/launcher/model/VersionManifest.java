package org.example.launcher.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parsed Mojang version manifest containing the full version list and the
 * identifiers of the latest release / snapshot.
 */
public final class VersionManifest {

    private final String latestReleaseId;
    private final String latestSnapshotId;
    private final List<MinecraftVersion> versions;

    public VersionManifest(String latestReleaseId,
                           String latestSnapshotId,
                           List<MinecraftVersion> versions) {
        this.latestReleaseId = latestReleaseId;
        this.latestSnapshotId = latestSnapshotId;
        this.versions = List.copyOf(versions);
    }

    /** Identifier of the latest stable release, if reported by Mojang. */
    public Optional<String> latestReleaseId() {
        return Optional.ofNullable(latestReleaseId);
    }

    /** Identifier of the latest snapshot, if reported by Mojang. */
    public Optional<String> latestSnapshotId() {
        return Optional.ofNullable(latestSnapshotId);
    }

    /** Unmodifiable list of all versions in the manifest. */
    public List<MinecraftVersion> versions() {
        return Collections.unmodifiableList(versions);
    }
}
