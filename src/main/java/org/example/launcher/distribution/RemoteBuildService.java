package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.distribution.api.LauncherServerApi;
import org.example.launcher.install.Sha1ChecksumVerifier;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Installs and updates the builds the administrator distributes via
 * the launcher server, and exposes what is installed locally.
 *
 * <p>Server builds live in the same {@code builds} folder of an
 * instance as locally saved builds — one sub-folder per build id —
 * but additionally carry a {@code build.json} manifest (origin
 * SERVER). Installing one build never touches another build: they
 * stay separate and switchable, and switching never re-downloads
 * anything.</p>
 *
 * <p>Updates use the build id + version scheme: the same id with a
 * higher version re-uses the existing folder and syncs it with a
 * diff — files whose SHA-1 matches are kept as-is, changed or new
 * files are downloaded, files that disappeared from the manifest are
 * removed.</p>
 */
public class RemoteBuildService {

    /** Manifest file that marks a build folder as server-distributed. */
    public static final String MANIFEST_FILE_NAME = "build.json";

    /** Must stay in sync with {@link org.example.launcher.service.BuildService}. */
    private static final String BUILDS_DIR = "builds";

    private final LauncherServerApi api;
    private final Gson gson;

    public RemoteBuildService(LauncherServerApi api) {
        this(api, new Gson());
    }

    public RemoteBuildService(LauncherServerApi api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    /**
     * Lists the builds the administrator published, sorted by display name.
     *
     * @param session the signed-in session
     * @return build summaries, never {@code null}
     * @throws IOException on network errors
     */
    public List<BuildSummary> catalog(ServerSession session) throws IOException {
        List<BuildSummary> builds = api.listBuilds(session);
        List<BuildSummary> sorted = new ArrayList<>(builds);
        sorted.sort(Comparator.comparing(BuildSummary::displayName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * Installs a build into an instance: fetches the descriptor and
     * syncs the build folder. Safe to call again for the same version
     * (idempotent — matching files are skipped) or a newer version
     * (acts as an update).
     *
     * @param session the signed-in session
     * @param summary the build to install, e.g. from the catalog
     * @param gameDir the instance's game directory
     * @return the installed descriptor
     * @throws IOException on network errors or corrupt downloads
     */
    public BuildDescriptor install(ServerSession session, BuildSummary summary, Path gameDir) throws IOException {
        return syncBuild(session, gameDir, api.fetchBuild(session, summary.id()));
    }

    /**
     * Updates a build to whatever version the server currently
     * publishes for its id. A no-op when the local folder already
     * matches the manifest.
     *
     * @param session the signed-in session
     * @param gameDir the instance's game directory
     * @param buildId unique build id
     * @return the updated descriptor
     * @throws IOException on network errors or corrupt downloads
     */
    public BuildDescriptor update(ServerSession session, Path gameDir, String buildId) throws IOException {
        return syncBuild(session, gameDir, api.fetchBuild(session, buildId));
    }

    /**
     * @param gameDir  the instance's game directory
     * @param buildId  unique build id
     * @return the installed manifest of that build, when present
     */
    public Optional<BuildDescriptor> installedBuild(Path gameDir, String buildId) {
        return readManifest(buildDir(gameDir, buildId));
    }

    /**
     * @param gameDir the instance's game directory
     * @return all server-distributed builds installed in it
     * @throws IOException when the builds folder cannot be read
     */
    public List<BuildDescriptor> installedBuilds(Path gameDir) throws IOException {
        Path builds = gameDir.resolve(BUILDS_DIR);
        if (!Files.isDirectory(builds)) {
            return List.of();
        }
        List<BuildDescriptor> installed = new ArrayList<>();
        try (var stream = Files.list(builds)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                readManifest(dir).ifPresent(installed::add);
            }
        }
        return installed;
    }

    /**
     * @param installed  the locally installed manifest
     * @param catalogSum the version the server currently publishes
     * @return true when the server's version is strictly newer and an
     *         update should be offered
     */
    public boolean updateAvailable(BuildDescriptor installed, BuildSummary catalogSum) {
        if (installed == null || catalogSum == null || !installed.id().equals(catalogSum.id())) {
            return false;
        }
        return BuildVersions.isNewer(catalogSum.version(), installed.version());
    }

    /** @return the local folder of a build: {@code gameDir/builds/{buildId}}. */
    public Path buildDir(Path gameDir, String buildId) {
        return gameDir.resolve(BUILDS_DIR).resolve(sanitizeId(buildId));
    }

    private BuildDescriptor syncBuild(ServerSession session, Path gameDir, BuildDescriptor descriptor) throws IOException {
        Path buildDir = buildDir(gameDir, descriptor.id());
        BuildDescriptor previous = readManifest(buildDir).orElse(null);
        Files.createDirectories(buildDir);

        Set<String> wantedKeys = new HashSet<>();
        for (BuildFileEntry file : descriptor.files()) {
            wantedKeys.add(file.key());
            Path target = buildDir.resolve(file.category().folder()).resolve(file.relativePath());
            if (Files.isRegularFile(target) && sha1Matches(target, file.sha1())) {
                continue; // already present and unchanged — no download
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            api.downloadFile(session, descriptor, file, target);
            if (!sha1Matches(target, file.sha1())) {
                throw new IOException("Corrupt download for build " + descriptor.id()
                        + ": " + file.key());
            }
        }

        if (previous != null) {
            for (BuildFileEntry old : previous.files()) {
                if (!wantedKeys.contains(old.key())) {
                    Path stale = buildDir.resolve(old.category().folder()).resolve(old.relativePath());
                    Files.deleteIfExists(stale);
                }
            }
        }

        writeManifest(buildDir, descriptor);
        return descriptor;
    }

    private boolean sha1Matches(Path file, String expectedSha1) throws IOException {
        try {
            return Sha1ChecksumVerifier.computeSha1(file).equalsIgnoreCase(expectedSha1);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is not available", e);
        }
    }

    private Optional<BuildDescriptor> readManifest(Path buildDir) {
        Path manifest = buildDir.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        try {
            JsonObject root = gson.fromJson(Files.readString(manifest), JsonObject.class);
            if (root == null) {
                return Optional.empty();
            }
            BuildDescriptor descriptor = fromJson(root);
            return Optional.ofNullable(descriptor);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private void writeManifest(Path buildDir, BuildDescriptor descriptor) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject summaryJson = new JsonObject();
        summaryJson.addProperty("id", descriptor.summary().id());
        summaryJson.addProperty("version", descriptor.summary().version());
        summaryJson.addProperty("displayName", descriptor.summary().displayName());
        summaryJson.addProperty("description", descriptor.summary().description());
        summaryJson.addProperty("loaderType", descriptor.summary().loaderType().name());
        summaryJson.addProperty("minecraftVersion", descriptor.summary().minecraftVersion());
        summaryJson.addProperty("loaderVersion", descriptor.summary().loaderVersion());
        root.add("summary", summaryJson);
        root.addProperty("origin", descriptor.origin().name());

        JsonArray files = new JsonArray();
        for (BuildFileEntry file : descriptor.files()) {
            JsonObject fileJson = new JsonObject();
            fileJson.addProperty("relativePath", file.relativePath());
            fileJson.addProperty("category", file.category().name());
            fileJson.addProperty("sha1", file.sha1());
            fileJson.addProperty("size", file.size());
            files.add(fileJson);
        }
        root.add("files", files);
        Files.writeString(buildDir.resolve(MANIFEST_FILE_NAME), gson.toJson(root));
    }

    private BuildDescriptor fromJson(JsonObject root) {
        JsonObject summaryJson = root.getAsJsonObject("summary");
        if (summaryJson == null) {
            return null;
        }
        ModLoaderType loaderType;
        try {
            loaderType = ModLoaderType.valueOf(required(summaryJson, "loaderType"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        BuildSummary summary = new BuildSummary(
                required(summaryJson, "id"),
                required(summaryJson, "version"),
                required(summaryJson, "displayName"),
                optional(summaryJson, "description"),
                loaderType,
                required(summaryJson, "minecraftVersion"),
                optional(summaryJson, "loaderVersion"));

        List<BuildFileEntry> files = new ArrayList<>();
        JsonArray filesJson = root.getAsJsonArray("files");
        if (filesJson != null) {
            for (var element : filesJson) {
                JsonObject fileJson = element.getAsJsonObject();
                BuildFileCategory category;
                try {
                    category = BuildFileCategory.valueOf(required(fileJson, "category"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                files.add(new BuildFileEntry(
                        required(fileJson, "relativePath"),
                        category,
                        required(fileJson, "sha1"),
                        fileJson.has("size") ? fileJson.get("size").getAsLong() : 0L));
            }
        }

        BuildOrigin origin = BuildOrigin.SERVER;
        String originRaw = optional(root, "origin");
        if (originRaw != null) {
            try {
                origin = BuildOrigin.valueOf(originRaw);
            } catch (IllegalArgumentException ignored) {
                // keep SERVER default
            }
        }
        return new BuildDescriptor(summary, files, origin);
    }

    private static String required(JsonObject obj, String key) {
        String value = optional(obj, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private static String optional(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String sanitizeId(String buildId) {
        String sanitized = buildId.toLowerCase().replaceAll("[^a-z0-9_-]+", "-");
        return sanitized.isBlank() ? "build" : sanitized;
    }
}
