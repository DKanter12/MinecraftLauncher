package org.example.launcher.service;

import java.io.IOException;

import org.example.launcher.model.VersionManifest;

/**
 * Provides access to the Minecraft version catalogue.
 * <p>
 * Implementations are free to fetch versions from Mojang, a local cache,
 * or any other source. This interface keeps the UI decoupled from the
 * concrete data provider.
 */
public interface VersionService {

    /**
     * Fetches the full version manifest.
     *
     * @return a non-{@code null} manifest containing all known versions
     * @throws IOException if the manifest could not be retrieved or parsed
     */
    VersionManifest fetchVersions() throws IOException;
}
