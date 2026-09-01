package org.example.launcher.service.modloader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.launcher.install.FileDownloader;
import org.example.launcher.install.InstallationService;

/**
 * Central registry and extension point for supported mod loaders.
 * <p>
 * Each {@link ModLoaderType} is bound to a {@link ModLoaderVersionProvider}
 * (version discovery) and a {@link ModLoaderInstaller} (fully automatic
 * installation). The UI and services work exclusively through this
 * registry, so adding support for a new loader is a single
 * {@link #register} call — no UI or pipeline changes required.
 * <p>
 * {@link #createDefault} wires the four built-in loaders (Fabric,
 * Forge, NeoForge, Quilt).
 */
public final class ModLoaderRegistry {

    /** One loader's full wiring: version provider + installer. */
    public record Entry(ModLoaderType type,
                        ModLoaderVersionProvider provider,
                        ModLoaderInstaller installer) {
    }

    private final Map<ModLoaderType, Entry> entries =
            new EnumMap<>(ModLoaderType.class);

    /**
     * Registers (or replaces) the wiring for a loader type.
     */
    public void register(ModLoaderType type,
                         ModLoaderVersionProvider provider,
                         ModLoaderInstaller installer) {
        entries.put(type, new Entry(type, provider, installer));
    }

    /** The wiring for a loader type, if registered. */
    public Optional<Entry> get(ModLoaderType type) {
        return Optional.ofNullable(entries.get(type));
    }

    /** All registered loader types. */
    public List<ModLoaderType> registeredTypes() {
        return new ArrayList<>(entries.keySet());
    }

    /** All registered wirings. */
    public List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Creates a registry with the four built-in loaders wired up
     * (Fabric and Quilt via their meta APIs, Forge and NeoForge via
     * their official installer JARs).
     */
    public static ModLoaderRegistry createDefault(
            java.net.http.HttpClient httpClient,
            FileDownloader fileDownloader,
            org.example.launcher.service.JavaResolutionService javaResolutionService,
            ModLoaderMetadataMerger merger,
            InstallationService installationService) {
        ModLoaderRegistry registry = new ModLoaderRegistry();

        registry.register(ModLoaderType.FABRIC,
                new FabricVersionProvider(),
                new MetaProfileModLoaderInstaller(
                        MetaProfileModLoaderInstaller.FABRIC_PROFILE_URL,
                        httpClient, merger, installationService));

        registry.register(ModLoaderType.QUILT,
                new QuiltVersionProvider(),
                new MetaProfileModLoaderInstaller(
                        MetaProfileModLoaderInstaller.QUILT_PROFILE_URL,
                        httpClient, merger, installationService));

        registry.register(ModLoaderType.FORGE,
                new ForgeVersionProvider(),
                new InstallerJarModLoaderInstaller(
                        fileDownloader, javaResolutionService,
                        merger, installationService));

        registry.register(ModLoaderType.NEOFORGE,
                new NeoForgeVersionProvider(),
                new InstallerJarModLoaderInstaller(
                        fileDownloader, javaResolutionService,
                        merger, installationService));

        return registry;
    }
}
