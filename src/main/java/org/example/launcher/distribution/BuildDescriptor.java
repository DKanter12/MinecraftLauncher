package org.example.launcher.distribution;

import java.util.List;

/**
 * The complete description of a distributed build: the summary plus
 * the full file list that makes the build reproducible on any
 * machine. This is what the launcher downloads from the server and
 * what it stores as the {@code build.json} manifest of an installed
 * build.
 *
 * @param summary the build's identity and description
 * @param files   every file the build consists of, with SHA-1 hashes
 * @param origin  where the build came from (SERVER for downloaded builds)
 */
public record BuildDescriptor(
        BuildSummary summary,
        List<BuildFileEntry> files,
        BuildOrigin origin) {

    public BuildDescriptor {
        if (summary == null) {
            throw new IllegalArgumentException("summary must not be null");
        }
        if (files == null) {
            files = List.of();
        } else {
            files = List.copyOf(files);
        }
        if (origin == null) {
            origin = BuildOrigin.SERVER;
        }
    }

    public String id() {
        return summary.id();
    }

    public String version() {
        return summary.version();
    }

    /** @return true when this manifest describes the same build id as {@code other}. */
    public boolean sameBuild(BuildDescriptor other) {
        return other != null && id().equals(other.id());
    }

    /** @return display form used in dialogs. */
    @Override
    public String toString() {
        return summary.summaryLine();
    }
}
