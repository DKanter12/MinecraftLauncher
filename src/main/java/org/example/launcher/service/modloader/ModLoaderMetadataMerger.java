package org.example.launcher.service.modloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;

/**
 * Merges a mod loader's version JSON (the {@code inheritsFrom}-style
 * profile produced by the Fabric/Quilt meta API or by the
 * Forge/NeoForge installers) with the vanilla Minecraft metadata it
 * inherits from, producing a single self-contained
 * {@link VersionMetadata} for the existing install and launch pipeline.
 * <p>
 * Merge rules (matching the official launcher's inheritance semantics):
 * <ul>
 *   <li>{@code id} — the loader profile's id (e.g.
 *       {@code fabric-loader-0.16.9-1.21.4})</li>
 *   <li>{@code mainClass} — loader's (this is what makes it a modded
 *       launch)</li>
 *   <li>{@code libraries} — loader's libraries first, then vanilla's
 *       (shared libraries are deduplicated by name)</li>
 *   <li>{@code arguments} — vanilla's arguments followed by the
 *       loader's (official {@code inheritsFrom} concatenation
 *       semantics). Loader profiles carry only their own additions:
 *       Forge/NeoForge add {@code -p}/fml arguments but rely on the
 *       vanilla {@code -cp ${classpath}} and {@code --username} etc.;
 *       Fabric/Quilt carry empty argument lists</li>
 *   <li>{@code assetIndex}, {@code assets}, {@code javaVersion},
 *       {@code clientDownload} — loader's value when present,
 *       otherwise vanilla's (loader profiles normally omit these)</li>
 * </ul>
 */
public class ModLoaderMetadataMerger {

    private final MojangVersionMetadataService parser;

    public ModLoaderMetadataMerger(MojangVersionMetadataService parser) {
        this.parser = parser;
    }

    /**
     * Merges the loader profile JSON with the vanilla metadata.
     *
     * @param vanilla    vanilla Minecraft metadata for the inherited version
     * @param loaderJson the loader profile JSON (raw string)
     * @return merged, self-contained metadata
     * @throws IOException if the loader JSON cannot be parsed
     */
    public VersionMetadata merge(VersionMetadata vanilla, String loaderJson)
            throws IOException {
        VersionMetadata loader = parser.parseMetadata(loaderJson, vanilla.id());

        String id = loader.id();
        String mainClass = loader.mainClass().orElse(vanilla.mainClass().orElse(null));

        List<org.example.launcher.model.Library> libraries =
                mergeLibraries(loader.libraries(), vanilla.libraries());

        // Official inheritsFrom semantics: the parent's arguments come
        // first, the child's (loader's) are appended. Forge/NeoForge
        // rely on the vanilla -cp ${classpath} entry to provide the
        // legacy class path for BootstrapLauncher.
        List<String> gameArgs = concat(vanilla.gameArguments(), loader.gameArguments());
        List<String> jvmArgs = concat(vanilla.jvmArguments(), loader.jvmArguments());

        String legacyArgs = loader.legacyMinecraftArguments()
                .orElseGet(() -> vanilla.legacyMinecraftArguments().orElse(null));

        org.example.launcher.model.AssetIndex assetIndex = loader.assetIndex()
                .orElse(vanilla.assetIndex().orElse(null));
        String assets = loader.assets().orElse(vanilla.assets().orElse(null));
        JavaVersion javaVersion = loader.javaVersion()
                .orElse(vanilla.javaVersion().orElse(null));
        org.example.launcher.model.DownloadInfo clientDownload = loader.clientDownload()
                .orElse(vanilla.clientDownload().orElse(null));

        return new VersionMetadata(
                id,
                loader.type().orElse(vanilla.type().orElse(null)),
                mainClass,
                assets,
                assetIndex,
                javaVersion,
                clientDownload,
                libraries,
                gameArgs,
                jvmArgs,
                legacyArgs);
    }

    /**
     * Combines loader and vanilla libraries: loader libraries win on
     * name conflicts (loaders replace/override specific vanilla
     * libraries such as bridged auth patches), vanilla libraries are
     * appended otherwise.
     */
    private List<org.example.launcher.model.Library> mergeLibraries(
            List<org.example.launcher.model.Library> loaderLibs,
            List<org.example.launcher.model.Library> vanillaLibs) {
        List<org.example.launcher.model.Library> result = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();

        for (org.example.launcher.model.Library lib : loaderLibs) {
            if (seenNames.add(lib.name())) {
                result.add(lib);
            }
        }
        for (org.example.launcher.model.Library lib : vanillaLibs) {
            if (seenNames.add(lib.name())) {
                result.add(lib);
            }
        }
        return result;
    }

    /** Base list first, additions appended (order-preserving). */
    private static List<String> concat(List<String> base, List<String> additions) {
        if (additions.isEmpty()) return base;
        List<String> result = new ArrayList<>(base.size() + additions.size());
        result.addAll(base);
        result.addAll(additions);
        return result;
    }
}
