package org.example.launcher.service.modloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.ModLoaderFamilyType;
import org.example.launcher.version.ModdedVersionType;

/**
 * Discovers locally installed modded versions (Fabric, Forge,
 * NeoForge, Quilt) and resolves their launch metadata.
 * <p>
 * Installed modded versions follow the standard
 * {@code versions/{id}/{id}.json} layout with an {@code inheritsFrom}
 * field pointing at the vanilla version they extend. They never
 * appear in the Mojang manifest, so this service scans the local
 * {@code versions/} directory instead.
 * <p>
 * At launch time {@link #resolveMetadata} re-resolves the inheritance
 * chain: the local loader JSON is merged with the vanilla metadata
 * fetched from Mojang (see {@link ModLoaderMetadataMerger}).
 */
public class ModdedVersionService {

    private final MojangVersionService versionService;
    private final MojangVersionMetadataService metadataService;
    private final ModLoaderMetadataMerger merger;

    public ModdedVersionService(MojangVersionService versionService,
                                MojangVersionMetadataService metadataService,
                                ModLoaderMetadataMerger merger) {
        this.versionService = versionService;
        this.metadataService = metadataService;
        this.merger = merger;
    }

    /**
     * Lists all modded versions installed in the given game directory.
     * A version counts as modded when its local JSON carries an
     * {@code inheritsFrom} field (loader profiles) or matches a known
     * loader id convention.
     */
    public List<MinecraftVersion> listInstalled(GameDirectory gameDir)
            throws IOException {
        List<MinecraftVersion> result = new ArrayList<>();
        Path versionsDir = gameDir.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }

        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            stream.forEach(dirs::add);
        }

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            String dirName = dir.getFileName().toString();
            Path json = dir.resolve(dirName + ".json");
            if (!Files.isRegularFile(json)) continue;

            try {
                JsonObject root = JsonParser.parseString(
                                Files.readString(json, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                boolean hasInherits = root.has("inheritsFrom")
                        && root.get("inheritsFrom").isJsonPrimitive();
                String id = root.has("id") && root.get("id").isJsonPrimitive()
                        ? root.get("id").getAsString() : dirName;
                if (hasInherits || loaderTypeOf(id).isPresent()) {
                    result.add(new MinecraftVersion(id,
                            ModdedVersionType.INSTANCE, null, null));
                }
            } catch (Exception ignored) {
                // Corrupt or unrelated JSON in versions/ — not a modded version
            }
        }
        return result;
    }

    /**
     * Resolves the complete launch metadata for an installed modded
     * version: reads the local loader JSON, fetches the inherited
     * vanilla metadata from the Mojang manifest and merges both.
     *
     * @param moddedVersionId the installed version id
     *                         (e.g. {@code fabric-loader-0.16.9-1.21.4})
     * @param gameDir         the game directory layout
     * @return merged, self-contained launch metadata
     * @throws IOException if the local JSON is missing/invalid or the
     *                     vanilla metadata cannot be resolved
     */
    public VersionMetadata resolveMetadata(String moddedVersionId,
                                           GameDirectory gameDir)
            throws IOException {
        Path jsonFile = gameDir.versionMetadata(moddedVersionId);
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("No local version JSON for " + moddedVersionId
                    + " (expected " + jsonFile + ")");
        }
        String loaderJson = Files.readString(jsonFile, StandardCharsets.UTF_8);

        JsonObject root;
        try {
            root = JsonParser.parseString(loaderJson).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Invalid version JSON for " + moddedVersionId, e);
        }

        String vanillaId = root.has("inheritsFrom")
                && root.get("inheritsFrom").isJsonPrimitive()
                        ? root.get("inheritsFrom").getAsString() : null;

        VersionMetadata vanilla = fetchVanillaMetadata(vanillaId);
        return merger.merge(vanilla, loaderJson);
    }

    private VersionMetadata fetchVanillaMetadata(String vanillaId)
            throws IOException {
        if (vanillaId == null || vanillaId.isBlank()) {
            throw new IOException(
                    "Version JSON has no inheritsFrom — cannot resolve vanilla base");
        }

        MinecraftVersion vanillaVersion = versionService.fetchVersions().versions()
                .stream()
                .filter(v -> v.id().equals(vanillaId))
                .findFirst()
                .orElseThrow(() -> new IOException("Vanilla version " + vanillaId
                        + " not found in the Mojang manifest"));

        return metadataService.fetchMetadata(vanillaVersion);
    }

    /**
     * Best-effort detection of the loader type behind a version id,
     * based on the standard naming conventions
     * ({@code fabric-loader-…}, {@code quilt-loader-…},
     * {@code …-forge-…}, {@code neoforge-…}).
     */
    public static Optional<ModLoaderType> loaderTypeOf(String versionId) {
        if (versionId == null) return Optional.empty();
        String id = versionId.toLowerCase();
        if (id.startsWith("fabric-loader-")) return Optional.of(ModLoaderType.FABRIC);
        if (id.startsWith("quilt-loader-")) return Optional.of(ModLoaderType.QUILT);
        if (id.contains("-forge-") || id.endsWith("-forge")) return Optional.of(ModLoaderType.FORGE);
        if (id.startsWith("neoforge-")) return Optional.of(ModLoaderType.NEOFORGE);
        return Optional.empty();
    }

    /**
     * A locally installed modded version with its loader family
     * detected from the version id, the loader version parsed from
     * the id and the vanilla base resolved from the local JSON's
     * {@code inheritsFrom} field (authoritative — NeoForge ids do
     * not contain the MC version).
     *
     * @param version          table entry for the installed version
     * @param loaderType       the loader family (Fabric, Forge, …)
     * @param loaderVersion    the loader's own version
     *                         (e.g. {@code "0.16.9"}, {@code "47.4.23"})
     * @param minecraftVersion the vanilla base version
     *                         (e.g. {@code "1.21.4"})
     */
    public record InstalledModdedVersion(MinecraftVersion version,
                                         ModLoaderType loaderType,
                                         String loaderVersion,
                                         String minecraftVersion) {

        /** The parsed info as a loader version entry. */
        public ModLoaderVersion toModLoaderVersion() {
            return new ModLoaderVersion(loaderType, loaderVersion,
                    minecraftVersion, true, null);
        }
    }

    /**
     * Lists installed modded versions together with their parsed
     * loader family, loader version and vanilla base — for the
     * unified version browser. Entries whose id cannot be attributed
     * to a known loader, whose local JSON has no {@code inheritsFrom},
     * or whose loader version cannot be parsed from the id are
     * skipped.
     */
    public List<InstalledModdedVersion> listInstalledDetailed(GameDirectory gameDir)
            throws IOException {
        List<InstalledModdedVersion> result = new ArrayList<>();
        Path versionsDir = gameDir.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }

        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            stream.forEach(dirs::add);
        }

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            String dirName = dir.getFileName().toString();
            Path json = dir.resolve(dirName + ".json");
            if (!Files.isRegularFile(json)) continue;

            try {
                JsonObject root = JsonParser.parseString(
                                Files.readString(json, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String id = root.has("id") && root.get("id").isJsonPrimitive()
                        ? root.get("id").getAsString() : dirName;
                ModLoaderType family = loaderTypeOf(id).orElse(null);
                if (family == null) continue;
                String mcBase = root.has("inheritsFrom")
                        && root.get("inheritsFrom").isJsonPrimitive()
                                ? root.get("inheritsFrom").getAsString() : null;
                if (mcBase == null || mcBase.isBlank()) continue;
                String loaderVersion = parseLoaderVersion(id, family, mcBase);
                if (loaderVersion == null || loaderVersion.isBlank()) continue;
                result.add(new InstalledModdedVersion(
                        new MinecraftVersion(id,
                                ModLoaderFamilyType.of(family), null, null),
                        family, loaderVersion, mcBase));
            } catch (Exception ignored) {
                // Corrupt or unrelated JSON in versions/ — not a modded version
            }
        }
        return result;
    }

    /**
     * Extracts the loader's own version from an installed version id,
     * given the family and the (authoritative) vanilla base version.
     */
    private static String parseLoaderVersion(String id, ModLoaderType family,
                                             String mcBase) {
        return switch (family) {
            case FABRIC -> stripAffixes(id, "fabric-loader-", "-" + mcBase);
            case QUILT -> stripAffixes(id, "quilt-loader-", "-" + mcBase);
            case FORGE -> stripAffixes(id, mcBase + "-forge-", "");
            case NEOFORGE -> stripAffixes(id, "neoforge-", "");
            default -> null;
        };
    }

    private static String stripAffixes(String id, String prefix, String suffix) {
        if (!id.startsWith(prefix) || !id.endsWith(suffix)) return null;
        int end = id.length() - suffix.length();
        if (end <= prefix.length()) return null;
        return id.substring(prefix.length(), end);
    }
}
