package org.example.launcher.service.modloader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.version.ModdedVersionType;

/**
 * {@link ModLoaderInstaller} for loaders that expose a ready-made
 * version profile JSON via a meta API (Fabric and Quilt).
 * <p>
 * Flow:
 * <ol>
 *   <li>fetch the loader profile JSON
 *       ({@code {meta}/versions/loader/{mc}/{loader}/profile/json});</li>
 *   <li>merge it with the vanilla metadata
 *       (see {@link ModLoaderMetadataMerger});</li>
 *   <li>persist the original profile JSON as
 *       {@code versions/{id}/{id}.json} (with an {@code inheritsFrom}
 *       marker) — the launch configuration;</li>
 *   <li>run the standard {@link InstallationService} on the merged
 *       metadata, which downloads the vanilla base (client JAR,
 *       libraries, assets) plus all loader dependencies with SHA-1
 *       verification and skip-if-valid reuse;</li>
 *   <li>verify the result and report it.</li>
 * </ol>
 */
public class MetaProfileModLoaderInstaller implements ModLoaderInstaller {

    public static final String FABRIC_PROFILE_URL =
            "https://meta.fabricmc.net/v2/versions/loader/{mc}/{v}/profile/json";

    public static final String QUILT_PROFILE_URL =
            "https://meta.quiltmc.org/v3/versions/loader/{mc}/{v}/profile/json";

    private final String profileUrlTemplate;
    private final HttpClient httpClient;
    private final ModLoaderMetadataMerger merger;
    private final InstallationService installationService;

    public MetaProfileModLoaderInstaller(String profileUrlTemplate,
                                         HttpClient httpClient,
                                         ModLoaderMetadataMerger merger,
                                         InstallationService installationService) {
        this.profileUrlTemplate = profileUrlTemplate;
        this.httpClient = httpClient;
        this.merger = merger;
        this.installationService = installationService;
    }

    @Override
    public ModLoaderInstallResult install(MinecraftVersion vanillaVersion,
                                          VersionMetadata vanillaMetadata,
                                          ModLoaderVersion loader,
                                          GameDirectory gameDir,
                                          InstallationProgress progress) throws IOException {
        // 1. Fetch loader profile JSON
        String loaderJson = fetchProfileJson(loader.minecraftVersion(),
                loader.loaderVersion());

        // 2. Merge with vanilla metadata
        VersionMetadata merged = merger.merge(vanillaMetadata, loaderJson);
        String versionId = merged.id();

        // 3. Persist launch configuration: original profile JSON +
        //    inheritsFrom marker, standard versions/{id}/{id}.json layout
        Path jsonFile = gameDir.versionMetadata(versionId);
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, withInheritsFrom(loaderJson, vanillaMetadata.id()),
                StandardCharsets.UTF_8);

        // 4. Install files (vanilla base + loader deps, hash-verified)
        MinecraftVersion moddedVersion = new MinecraftVersion(
                versionId, ModdedVersionType.INSTANCE, null, null);
        InstallationResult files = installationService.install(
                moddedVersion, merged, gameDir, progress);

        // 5. Verify
        if (files.hasFailures()) {
            throw new IOException("Mod loader installation finished with "
                    + files.failed() + " failed downloads: "
                    + files.failedTasks().stream()
                            .map(t -> t.task().name())
                            .limit(5)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("(unknown)"));
        }
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("Launch configuration was not created: " + jsonFile);
        }

        return new ModLoaderInstallResult(versionId, files);
    }

    private String fetchProfileJson(String mcVersion, String loaderVersion)
            throws IOException {
        String url = profileUrlTemplate
                .replace("{mc}", mcVersion)
                .replace("{v}", loaderVersion);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Profile endpoint returned HTTP "
                        + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching loader profile", e);
        }
    }

    /**
     * Ensures the persisted JSON carries an {@code inheritsFrom} field
     * pointing at the vanilla version, so the installed version can be
     * re-resolved at launch time.
     */
    static String withInheritsFrom(String loaderJson, String vanillaId) {
        JsonObject root = JsonParser.parseString(loaderJson).getAsJsonObject();
        if (!root.has("inheritsFrom")) {
            root.addProperty("inheritsFrom", vanillaId);
        }
        return root.toString();
    }
}
