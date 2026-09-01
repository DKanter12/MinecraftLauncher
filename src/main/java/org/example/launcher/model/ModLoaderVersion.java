package org.example.launcher.model;

import java.util.Objects;
import java.util.Optional;

import org.example.launcher.service.modloader.ModLoaderType;

/**
 * A single mod loader version, resolved for one specific Minecraft
 * version (e.g. Fabric Loader 0.16.9 for Minecraft 1.21.4).
 * <p>
 * Instances are produced by {@link org.example.launcher.service.modloader.ModLoaderVersionProvider}
 * implementations, which guarantee compatibility: every entry in the
 * list returned for a Minecraft version is installable for that version.
 *
 * @param loaderType     the loader family (Fabric, Forge, …)
 * @param loaderVersion  the loader's own version (e.g. {@code "0.16.9"},
 *                       {@code "47.4.10"})
 * @param minecraftVersion the Minecraft version this entry was resolved for
 * @param stable         whether the version is marked stable/recommended
 * @param installerUrl   direct URL of the installer JAR (Forge/NeoForge),
 *                       or {@code null} for meta-API based loaders
 *                       (Fabric/Quilt)
 */
public record ModLoaderVersion(
        ModLoaderType loaderType,
        String loaderVersion,
        String minecraftVersion,
        boolean stable,
        String installerUrl) {

    public ModLoaderVersion {
        Objects.requireNonNull(loaderType, "loaderType");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    /**
     * The installer JAR URL wrapped as an Optional
     * ({@code Optional.empty()} for meta-API based loaders).
     */
    public Optional<String> installerUrlOpt() {
        return Optional.ofNullable(installerUrl);
    }

    /**
     * The resulting installed version id, following each loader's
     * convention:
     * <ul>
     *   <li>Fabric: {@code fabric-loader-0.16.9-1.21.4}</li>
     *   <li>Quilt: {@code quilt-loader-0.26.0-1.21.4}</li>
     *   <li>Forge: {@code 1.20.1-47.4.10}</li>
     *   <li>NeoForge: {@code neoforge-21.4.147}</li>
     * </ul>
     */
    public String installedVersionId() {
        return switch (loaderType) {
            case VANILLA -> minecraftVersion;
            case FABRIC -> "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
            case QUILT -> "quilt-loader-" + loaderVersion + "-" + minecraftVersion;
            case FORGE -> minecraftVersion + "-forge-" + loaderVersion;
            case NEOFORGE -> "neoforge-" + loaderVersion;
        };
    }

    @Override
    public String toString() {
        return loaderType.displayName() + " " + loaderVersion
                + " (MC " + minecraftVersion + ")";
    }
}
