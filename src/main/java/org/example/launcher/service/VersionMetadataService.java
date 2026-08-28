package org.example.launcher.service;

import java.io.IOException;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionMetadata;

/**
 * Provides per-version metadata (client JAR, libraries, assets, arguments…).
 * <p>
 * This is the second-level fetch: the {@link VersionService} gives the
 * list of versions, and this service resolves a specific version's
 * detailed metadata from its individual JSON URL.
 */
public interface VersionMetadataService {

    /**
     * Fetches and parses the metadata for the given version.
     *
     * @param version the version to resolve (must have a non-null
     *                metadata URL)
     * @return parsed metadata, never {@code null}
     * @throws IOException if the metadata could not be retrieved or parsed
     */
    VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException;
}
