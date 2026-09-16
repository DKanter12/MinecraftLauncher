package org.example.launcher.distribution;

import org.example.launcher.service.modloader.ModLoaderType;

/**
 * The public description of a build as the launcher server hands it
 * out — everything the player needs to decide whether to install it,
 * without the file list.
 *
 * <p>A build is identified by its immutable {@code id}. Releasing a
 * new revision of the same build keeps the id and only bumps
 * {@code version} (e.g. 1.0.0 → 1.1.0), which is what drives the
 * update flow.</p>
 *
 * @param id              unique build id, also the local folder name
 * @param version         build version, dot-separated numbers
 * @param displayName     human-friendly build name
 * @param description     short description shown in the build list
 * @param loaderType      loader the build is meant to run with
 * @param minecraftVersion Minecraft version of the build
 * @param loaderVersion   loader version of the build
 */
public record BuildSummary(
        String id,
        String version,
        String displayName,
        String description,
        ModLoaderType loaderType,
        String minecraftVersion,
        String loaderVersion) {

    public BuildSummary {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (loaderType == null) {
            throw new IllegalArgumentException("loaderType must not be null");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion must not be blank");
        }
    }

    /** @return concise line for list rows, e.g. {@code Better Survival 1.1.0 · Fabric 1.21.4}. */
    public String summaryLine() {
        return displayName + " " + version + " · " + loaderType.displayName()
                + " " + minecraftVersion;
    }

    /** @return true when this entry describes the same build (same id) as {@code other}. */
    public boolean sameBuild(BuildSummary other) {
        return other != null && id.equals(other.id());
    }

    /** @return display form used in dialogs. */
    @Override
    public String toString() {
        return summaryLine();
    }
}
