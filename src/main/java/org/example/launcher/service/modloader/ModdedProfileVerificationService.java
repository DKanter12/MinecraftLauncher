package org.example.launcher.service.modloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.MinecraftLauncher;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.ModdedVersionType;

/**
 * Verifies that a game instance's installation is complete and
 * launchable, with precise, human-readable diagnostics.
 * <p>
 * For modded instances verification checks:
 * <ol>
 *   <li>the loader's version JSON exists ({@code versions/{id}/{id}.json});</li>
 *   <li>the installed Minecraft version matches the instance target
 *       ({@code inheritsFrom});</li>
 *   <li>the installed loader version matches the instance target
 *       (version id convention);</li>
 *   <li>all dependencies are present locally and intact — client JAR,
 *       every library (loader + vanilla closure) and the asset index,
 *       each verified by SHA-1 where a hash is known;</li>
 *   <li>a correct launch command can be formed (main class, game
 *       arguments, resolvable Java runtime);</li>
 *   <li>the instance's game directory exists.</li>
 * </ol>
 * <p>
 * Vanilla instances skip the loader checks (1–3); their metadata is
 * fetched from the Mojang manifest directly.
 * <p>
 * {@link #repair} reinstalls what is missing: corrupt or missing
 * shared files (client JAR, libraries, asset index) are re-downloaded
 * directly via the {@link InstallationService} over the resolved
 * metadata — skip-if-valid; the mod loader itself is only reinstalled
 * when its version JSON is missing or unresolvable, with the
 * installer URL looked up via the loader's version provider. This
 * makes instances self-healing.
 */
public class ModdedProfileVerificationService {

    /**
     * Outcome of an instance verification.
     *
     * @param ok       whether the instance is ready to launch
     * @param errors   launch-blocking problems with their causes
     * @param warnings non-blocking observations
     * @param metadata the resolved launch metadata (present when the
     *                 version chain resolved successfully)
     */
    public record VerificationReport(boolean ok,
                                     List<String> errors,
                                     List<String> warnings,
                                     Optional<VersionMetadata> metadata) {

        public boolean isRepairableByInstall() {
            return metadata.isPresent();
        }
    }

    private final ModdedVersionService moddedVersionService;
    private final MojangVersionService versionService;
    private final MojangVersionMetadataService metadataService;
    private final ModLoaderRegistry registry;
    private final MinecraftLauncher launcher;
    private final JavaResolutionService javaResolutionService;
    private final InstallationService installationService;

    public ModdedProfileVerificationService(ModdedVersionService moddedVersionService,
                                            MojangVersionService versionService,
                                            MojangVersionMetadataService metadataService,
                                            ModLoaderRegistry registry,
                                            MinecraftLauncher launcher,
                                            JavaResolutionService javaResolutionService,
                                            InstallationService installationService) {
        this.moddedVersionService = moddedVersionService;
        this.versionService = versionService;
        this.metadataService = metadataService;
        this.registry = registry;
        this.launcher = launcher;
        this.javaResolutionService = javaResolutionService;
        this.installationService = installationService;
    }

    // ------------------------------------------------------------------
    //  Verification
    // ------------------------------------------------------------------

    /**
     * Verifies the instance's installation and returns a full report.
     */
    public VerificationReport verify(ModdedProfile profile, GameDirectory storage) {
        if (profile.isVanilla()) {
            return verifyVanilla(profile, storage);
        }
        return verifyModded(profile, storage);
    }

    /**
     * Vanilla instance verification: metadata from the Mojang manifest,
     * dependency integrity, launch command and game directory.
     */
    private VerificationReport verifyVanilla(ModdedProfile profile,
                                             GameDirectory storage) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        MinecraftVersion vanillaVersion = findManifestVersion(
                profile.minecraftVersion());
        if (vanillaVersion == null) {
            errors.add("Minecraft version " + profile.minecraftVersion()
                    + " was not found in the Mojang manifest");
            errors.add("Cause: the version may be very old, a local build, "
                    + "or the manifest could not be fetched");
            return new VerificationReport(false, errors, warnings,
                    Optional.empty());
        }

        VersionMetadata metadata;
        try {
            metadata = metadataService.fetchMetadata(vanillaVersion);
        } catch (IOException e) {
            errors.add("Failed to fetch version metadata: " + e.getMessage());
            errors.add("Cause: MC " + profile.minecraftVersion()
                    + " metadata could not be downloaded");
            return new VerificationReport(false, errors, warnings,
                    Optional.empty());
        }

        // Dependencies present and intact (client JAR, libraries,
        // asset index — SHA-1 verified where known). A missing file
        // means the game is not fully installed; repair re-downloads
        // only what is missing.
        List<String> fileProblems = launcher.verifyFiles(metadata, storage);
        for (String problem : fileProblems) {
            errors.add("Missing or corrupt dependency: " + problem);
        }

        checkLaunchCommand(metadata, errors);
        checkInstanceDirectory(profile, storage, warnings);

        return new VerificationReport(errors.isEmpty(), errors, warnings,
                Optional.of(metadata));
    }

    /**
     * Modded instance verification: loader JSON, version chain,
     * merged metadata, dependency integrity, launch command and game
     * directory.
     */
    private VerificationReport verifyModded(ModdedProfile profile,
                                            GameDirectory storage) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Version JSON present?
        Path versionJson = storage.versionMetadata(profile.versionId());
        if (!Files.isRegularFile(versionJson)) {
            errors.add("Loader configuration file is missing: " + versionJson);
            errors.add("Cause: the " + profile.loaderType().displayName()
                    + " installation is incomplete — the loader needs to be "
                    + "reinstalled for this instance");
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        // 2. Version chain matches the instance?
        JsonObject json;
        try {
            json = JsonParser.parseString(
                            Files.readString(versionJson, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            errors.add("Loader configuration file is corrupt: " + versionJson);
            errors.add("Cause: " + e);
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        String installedId = json.has("id") && json.get("id").isJsonPrimitive()
                ? json.get("id").getAsString() : null;
        String inheritsFrom = json.has("inheritsFrom")
                && json.get("inheritsFrom").isJsonPrimitive()
                        ? json.get("inheritsFrom").getAsString() : null;

        if (installedId == null || !installedId.equals(profile.versionId())) {
            errors.add("Loader version mismatch: instance expects '"
                    + profile.versionId() + "' but the installation reports '"
                    + installedId + "'");
        }
        if (inheritsFrom == null) {
            errors.add("The loader configuration does not declare which "
                    + "Minecraft version it inherits from");
        } else if (!inheritsFrom.equals(profile.minecraftVersion())) {
            errors.add("Minecraft version mismatch: instance targets MC "
                    + profile.minecraftVersion() + " but the installation "
                    + "targets MC " + inheritsFrom);
        }
        String expectedId = profile.expectedVersionId();
        if (installedId != null && !installedId.equals(expectedId)) {
            errors.add("Loader version mismatch: instance expects '"
                    + expectedId + "' ("
                    + profile.loaderType().displayName() + " "
                    + profile.loaderVersion() + ") but the installation "
                    + "reports '" + installedId + "'");
        }
        if (!errors.isEmpty()) {
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        // 3. Resolve merged launch metadata (vanilla chain)
        VersionMetadata merged;
        try {
            merged = moddedVersionService.resolveMetadata(profile.versionId(), storage);
        } catch (IOException e) {
            errors.add("Failed to resolve launch metadata: " + e.getMessage());
            errors.add("Cause: the vanilla metadata for MC "
                    + profile.minecraftVersion() + " could not be fetched "
                    + "or merged with the loader configuration");
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        // 4. Dependencies present and intact (client JAR, libraries,
        //    asset index — SHA-1 verified where known)
        List<String> fileProblems = launcher.verifyFiles(merged, storage);
        for (String problem : fileProblems) {
            errors.add("Missing or corrupt dependency: " + problem);
        }

        // 5. Launch command can be formed
        checkLaunchCommand(merged, errors);

        // 6. Instance game directory
        checkInstanceDirectory(profile, storage, warnings);

        return new VerificationReport(errors.isEmpty(), errors, warnings,
                Optional.of(merged));
    }

    private void checkLaunchCommand(VersionMetadata metadata,
                                    List<String> errors) {
        if (metadata.mainClass().isEmpty()) {
            errors.add("Cannot form a launch command: the main class is missing "
                    + "from the metadata");
        }
        if (metadata.gameArguments().isEmpty()
                && metadata.legacyMinecraftArguments().isEmpty()) {
            errors.add("Cannot form a launch command: no game arguments in "
                    + "the metadata");
        }
        JavaResolutionResult javaResult = javaResolutionService.resolve(metadata);
        if (!javaResult.isFound()) {
            errors.add("No suitable Java runtime: "
                    + javaResult.reason().orElse("none found"));
        }
    }

    private void checkInstanceDirectory(ModdedProfile profile,
                                        GameDirectory storage,
                                        List<String> warnings) {
        Path instanceDir = storage.root().resolve(profile.gameDirPath());
        if (!Files.isDirectory(instanceDir)) {
            warnings.add("Instance game directory does not exist yet and will "
                    + "be created on launch: " + instanceDir);
        } else if (!Files.isDirectory(instanceDir.resolve("mods"))) {
            warnings.add("The instance's mods folder is missing and will be "
                    + "recreated on launch");
        }
    }

    // ------------------------------------------------------------------
    //  Repair
    // ------------------------------------------------------------------

    /**
     * Repairs the instance's installation by reinstalling what is
     * missing. Corrupt or missing shared files (client JAR,
     * libraries, asset index) are re-downloaded directly via the
     * installation service over the resolved metadata — skip-if-valid,
     * so intact files are not touched. The mod loader itself is only
     * reinstalled when its version JSON is missing or unresolvable;
     * the installer URL needed by installer-JAR loaders (Forge,
     * NeoForge) is then looked up via the loader's version provider.
     *
     * @param profile  the instance to repair
     * @param storage  the storage game directory
     * @param progress progress callback
     * @return the install result (version id of the repaired install)
     * @throws IOException if the repair cannot be performed (e.g. the
     *                     vanilla version is unknown or the loader is
     *                     not registered)
     */
    public ModLoaderInstaller.ModLoaderInstallResult repair(ModdedProfile profile,
                                                            GameDirectory storage,
                                                            InstallationProgress progress)
            throws IOException {
        MinecraftVersion vanillaVersion = findManifestVersion(
                profile.minecraftVersion());
        if (vanillaVersion == null) {
            throw new IOException("Cannot repair instance: vanilla MC "
                    + profile.minecraftVersion()
                    + " was not found in the Mojang manifest");
        }
        VersionMetadata vanillaMetadata = metadataService.fetchMetadata(vanillaVersion);

        if (profile.isVanilla()) {
            // Reinstall the vanilla game — skip-if-valid, so intact
            // files are not touched
            InstallationResult result = installationService.install(
                    vanillaVersion, vanillaMetadata, storage, progress);
            if (result.hasFailures()) {
                throw new IOException("Vanilla repair incomplete: "
                        + result.failed() + " downloads failed");
            }
            return new ModLoaderInstaller.ModLoaderInstallResult(
                    profile.versionId(), null);
        }

        var entry = registry.get(profile.loaderType()).orElse(null);
        if (entry == null) {
            throw new IOException(profile.loaderType().displayName()
                    + " support is not registered in this launcher");
        }

        // Light path: the loader installation itself is intact (its
        // version JSON resolves) and only shared files are missing or
        // corrupt — re-running the standard installation over the
        // merged metadata re-downloads exactly those, with
        // skip-if-valid; no loader reinstall (and no installer JAR
        // download) is needed
        if (Files.isRegularFile(storage.versionMetadata(profile.versionId()))) {
            VersionMetadata merged = null;
            try {
                merged = moddedVersionService.resolveMetadata(
                        profile.versionId(), storage);
            } catch (IOException e) {
                // Corrupt/unresolvable loader JSON → full reinstall
            }
            if (merged != null) {
                MinecraftVersion moddedVersion = new MinecraftVersion(
                        profile.versionId(), ModdedVersionType.INSTANCE,
                        null, null);
                InstallationResult result = installationService.install(
                        moddedVersion, merged, storage, progress);
                if (result.hasFailures()) {
                    throw new IOException(profile.loaderType().displayName()
                            + " repair incomplete: " + result.failed()
                            + " downloads failed");
                }
                return new ModLoaderInstaller.ModLoaderInstallResult(
                        profile.versionId(), result);
            }
        }

        // Full reinstall: rewrites the version JSON, re-runs the
        // loader installer (Forge/NeoForge) and fetches every
        // dependency — with skip-if-valid, so intact files are not
        // touched
        return entry.installer().install(vanillaVersion, vanillaMetadata,
                resolveLoaderVersion(entry, profile), storage, progress);
    }

    /**
     * Resolves the loader version to reinstall from the loader's
     * version provider — the only source of the installer URL needed
     * by installer-JAR loaders — falling back to a URL-less
     * reconstruction from the instance data when the provider is
     * unavailable (e.g. offline).
     */
    private ModLoaderVersion resolveLoaderVersion(ModLoaderRegistry.Entry entry,
                                                  ModdedProfile profile) {
        try {
            for (ModLoaderVersion v : entry.provider()
                    .fetchVersions(profile.minecraftVersion())) {
                if (v.loaderVersion().equals(profile.loaderVersion())) {
                    return v;
                }
            }
        } catch (Exception e) {
            // Provider unavailable or the version not listed — the
            // reconstruction below is the best available data
        }
        return new ModLoaderVersion(
                profile.loaderType(), profile.loaderVersion(),
                profile.minecraftVersion(), true, null);
    }

    private MinecraftVersion findManifestVersion(String minecraftVersion) {
        try {
            return versionService.fetchVersions().versions().stream()
                    .filter(v -> v.id().equals(minecraftVersion))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
