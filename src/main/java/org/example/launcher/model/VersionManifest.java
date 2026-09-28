package org.example.launcher.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Разобранный манифест версий Mojang с полным списком версий и
 * идентификаторами последнего релиза / снапшота.
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

    /** Идентификатор последнего стабильного релиза, если сообщён Mojang. */
    public Optional<String> latestReleaseId() {
        return Optional.ofNullable(latestReleaseId);
    }

    /** Идентификатор последнего снапшота, если сообщён Mojang. */
    public Optional<String> latestSnapshotId() {
        return Optional.ofNullable(latestSnapshotId);
    }

    /** Немодифицируемый список всех версий манифеста. */
    public List<MinecraftVersion> versions() {
        return Collections.unmodifiableList(versions);
    }
}
