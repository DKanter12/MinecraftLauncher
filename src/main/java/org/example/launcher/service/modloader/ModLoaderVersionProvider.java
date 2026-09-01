package org.example.launcher.service.modloader;

import java.io.IOException;
import java.util.List;

import org.example.launcher.model.ModLoaderVersion;

/**
 * Provides the list of available mod loader versions that are
 * compatible with a given Minecraft version.
 * <p>
 * Every implementation guarantees that each returned
 * {@link ModLoaderVersion} is installable for the requested Minecraft
 * version — the UI can therefore display the list unfiltered.
 * <p>
 * This is the main extension point for adding new mod loaders.
 */
public interface ModLoaderVersionProvider {

    /**
     * Fetches all loader versions compatible with the given Minecraft
     * version, newest first.
     *
     * @param minecraftVersion the Minecraft version id (e.g. {@code "1.21.4"})
     * @return compatible loader versions, never {@code null}
     * @throws IOException if the version list could not be fetched
     */
    List<ModLoaderVersion> fetchVersions(String minecraftVersion) throws IOException;

    /**
     * Fetches the set of Minecraft versions this loader supports, as
     * reported by the loader's own metadata service — the
     * authoritative answer to "does this loader exist for that
     * version" (e.g. NeoForge only exists for MC 1.20.1 and newer;
     * Fabric and Quilt start at 1.14). One lightweight call, instead
     * of one {@link #fetchVersions} call per version.
     *
     * @return the supported Minecraft version ids, or {@code null}
     *         when the provider cannot determine the set — callers
     *         then fall back to their own assumption
     * @throws IOException if the set could not be fetched
     */
    default java.util.Set<String> fetchSupportedMinecraftVersions() throws IOException {
        return null;
    }
}
