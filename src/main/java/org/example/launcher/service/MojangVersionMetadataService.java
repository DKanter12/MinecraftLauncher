package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.Library;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.util.OsDetector;

/**
 * {@link VersionMetadataService} backed by the official Mojang per-version
 * metadata JSON.
 * <p>
 * JSON parsing is isolated in {@link #parseMetadata(String, String)} so it
 * can be unit-tested without network access. The parser handles both the
 * modern structured argument format (1.13+) and the legacy
 * {@code minecraftArguments} string (pre-1.13).
 */
public class MojangVersionMetadataService implements VersionMetadataService {

    private final HttpClient httpClient;
    private final Gson gson;

    public MojangVersionMetadataService() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                new Gson());
    }

    public MojangVersionMetadataService(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException {
        String url = version.metadataUrl();
        if (url == null || url.isBlank()) {
            throw new IOException("Version " + version.id() + " has no metadata URL");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected HTTP status " + response.statusCode()
                        + " fetching metadata for " + version.id());
            }
            return parseMetadata(response.body(), version.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching metadata for " + version.id(), e);
        }
    }

    /**
     * Parses a raw per-version metadata JSON string.
     *
     * @param json       the raw JSON body
     * @param fallbackId id to use when the JSON does not contain one
     * @return a fully populated {@link VersionMetadata}
     * @throws IOException if the JSON is invalid or missing required fields
     */
    public VersionMetadata parseMetadata(String json, String fallbackId) throws IOException {
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse version metadata JSON", e);
        }
        if (root == null) {
            throw new IOException("Version metadata JSON is empty or invalid");
        }

        String id = getStr(root, "id", fallbackId);
        String type = getStrOrNull(root, "type");
        String mainClass = getStrOrNull(root, "mainClass");
        String assets = getStrOrNull(root, "assets");

        AssetIndex assetIndex = parseAssetIndex(root);
        JavaVersion javaVersion = parseJavaVersion(root);
        DownloadInfo clientDownload = parseClientDownload(root);
        List<Library> libraries = parseLibraries(root);
        List<String> gameArgs = new ArrayList<>();
        List<String> jvmArgs = new ArrayList<>();
        String legacyArgs = null;

        if (root.has("arguments") && root.get("arguments").isJsonObject()) {
            JsonObject args = root.getAsJsonObject("arguments");
            gameArgs = parseArgumentList(args, "game");
            jvmArgs = parseArgumentList(args, "jvm");
        }

        if (root.has("minecraftArguments") && !root.get("minecraftArguments").isJsonNull()) {
            legacyArgs = root.get("minecraftArguments").getAsString();
        }

        return new VersionMetadata(id, type, mainClass, assets,
                assetIndex, javaVersion, clientDownload,
                libraries, gameArgs, jvmArgs, legacyArgs);
    }

    // ------------------------------------------------------------------
    //  Internal parsing helpers
    // ------------------------------------------------------------------

    private AssetIndex parseAssetIndex(JsonObject root) {
        if (!root.has("assetIndex") || !root.get("assetIndex").isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject("assetIndex");
        return new AssetIndex(
                getStrOrNull(obj, "id"),
                getStrOrNull(obj, "sha1"),
                getLong(obj, "size", 0),
                getLong(obj, "totalSize", 0),
                getStrOrNull(obj, "url"));
    }

    private JavaVersion parseJavaVersion(JsonObject root) {
        if (!root.has("javaVersion") || !root.get("javaVersion").isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject("javaVersion");
        return new JavaVersion(
                getStrOrNull(obj, "component"),
                getInt(obj, "majorVersion", 0));
    }

    private DownloadInfo parseClientDownload(JsonObject root) {
        if (!root.has("downloads") || !root.get("downloads").isJsonObject()) {
            return null;
        }
        JsonObject downloads = root.getAsJsonObject("downloads");
        if (!downloads.has("client") || !downloads.get("client").isJsonObject()) {
            return null;
        }
        JsonObject client = downloads.getAsJsonObject("client");
        return new DownloadInfo(
                getStrOrNull(client, "url"),
                getStrOrNull(client, "sha1"),
                getLong(client, "size", 0),
                null);
    }

    private List<Library> parseLibraries(JsonObject root) {
        if (!root.has("libraries") || !root.get("libraries").isJsonArray()) {
            return List.of();
        }
        JsonArray libsArray = root.getAsJsonArray("libraries");
        List<Library> result = new ArrayList<>(libsArray.size());

        for (JsonElement elem : libsArray) {
            if (!elem.isJsonObject()) continue;
            JsonObject libObj = elem.getAsJsonObject();

            // Skip libraries whose rules disallow the current OS
            if (!rulesAllow(libObj)) {
                continue;
            }

            String name = getStrOrNull(libObj, "name");
            DownloadInfo artifact = null;
            Map<String, DownloadInfo> classifiers = new HashMap<>();
            Map<String, String> natives = new HashMap<>();

            // Parse downloads.artifact + downloads.classifiers
            if (libObj.has("downloads") && libObj.get("downloads").isJsonObject()) {
                JsonObject dl = libObj.getAsJsonObject("downloads");

                if (dl.has("artifact") && dl.get("artifact").isJsonObject()) {
                    artifact = parseDownloadInfo(dl.getAsJsonObject("artifact"));
                }

                if (dl.has("classifiers") && dl.get("classifiers").isJsonObject()) {
                    JsonObject clsObj = dl.getAsJsonObject("classifiers");
                    for (var entry : clsObj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            classifiers.put(entry.getKey(),
                                    parseDownloadInfo(entry.getValue().getAsJsonObject()));
                        }
                    }
                }
            }

            // Legacy libraries without "downloads" — combine root "url" + maven path
            if (artifact == null) {
                String libUrl = getStrOrNull(libObj, "url");
                if (libUrl != null && !libUrl.isBlank()) {
                    String libPath = mavenNameToPath(name);
                    if (libPath != null) {
                        String base = libUrl.endsWith("/") ? libUrl : libUrl + "/";
                        String fullUrl = base + libPath;
                        // Fabric/Quilt-style libraries carry sha1/size at the
                        // top level alongside "url" — pick them up for
                        // hash-verified downloads
                        String sha1 = getStrOrNull(libObj, "sha1");
                        long size = getLong(libObj, "size", 0);
                        artifact = new DownloadInfo(fullUrl, sha1, size, libPath);
                    }
                }
            }

            // Parse natives map (OS -> classifier name)
            if (libObj.has("natives") && libObj.get("natives").isJsonObject()) {
                JsonObject natObj = libObj.getAsJsonObject("natives");
                for (var entry : natObj.entrySet()) {
                    if (!entry.getValue().isJsonNull()) {
                        natives.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }

            result.add(new Library(name, artifact, classifiers, natives));
        }

        return result;
    }

    private DownloadInfo parseDownloadInfo(JsonObject obj) {
        return new DownloadInfo(
                getStrOrNull(obj, "url"),
                getStrOrNull(obj, "sha1"),
                getLong(obj, "size", 0),
                getStrOrNull(obj, "path"));
    }

    /**
     * Converts a Maven coordinate name (e.g.
     * {@code "com.mojang:logging:1.1.1"}) to a relative file path
     * ({@code "com/mojang/logging/1.1.1/logging-1.1.1.jar"}).
     * Used for legacy libraries that only have a root {@code url} field.
     */
    private static String mavenNameToPath(String name) {
        if (name == null || name.isBlank()) return null;
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace('.', '/') + ".jar";
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /**
     * Parses an argument array from the "arguments" object. Each element
     * can be either a plain string or an object with a "value" field
     * and optional "rules". Rules are filtered by the current OS so
     * that OS-specific arguments (e.g. Windows-only JVM flags) are
     * only included on the matching platform.
     */
    private List<String> parseArgumentList(JsonObject args, String key) {
        if (!args.has(key) || !args.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray arr = args.getAsJsonArray(key);
        List<String> result = new ArrayList<>(arr.size());

        for (JsonElement elem : arr) {
            if (elem.isJsonPrimitive()) {
                result.add(elem.getAsString());
            } else if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("value") && rulesAllow(obj)) {
                    JsonElement val = obj.get("value");
                    if (val.isJsonPrimitive()) {
                        result.add(val.getAsString());
                    } else if (val.isJsonArray()) {
                        for (JsonElement v : val.getAsJsonArray()) {
                            if (v.isJsonPrimitive()) {
                                result.add(v.getAsString());
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Evaluates the "rules" array of an argument object against the
     * current OS. If no rules are present, the argument is always
     * allowed. Otherwise all rules must pass.
     * <p>
     * A rule with {@code "action": "allow"} passes when its OS
     * condition matches (or has no OS condition). A rule with
     * {@code "action": "disallow"} passes when its OS condition
     * does NOT match.
     * <p>
     * Feature-based rules (e.g. {@code is_demo_user}) are treated
     * as not matching, since the launcher does not set those features.
     */
    private boolean rulesAllow(JsonObject argObj) {
        if (!argObj.has("rules") || !argObj.get("rules").isJsonArray()) {
            return true;
        }
        JsonArray rules = argObj.getAsJsonArray("rules");
        for (JsonElement ruleElem : rules) {
            if (!ruleElem.isJsonObject()) continue;
            JsonObject rule = ruleElem.getAsJsonObject();
            String action = getStrOrNull(rule, "action");
            if (action == null) continue;

            boolean hasOs = rule.has("os") && rule.get("os").isJsonObject();
            boolean hasFeatures = rule.has("features") && rule.get("features").isJsonObject();

            if (hasFeatures) {
                // Feature-based rules (is_demo_user, has_custom_resolution, etc.)
                // are not applicable to our launcher — treat as not matching.
                if ("allow".equals(action)) {
                    return false;
                }
                continue;
            }

            if (hasOs) {
                JsonObject osObj = rule.getAsJsonObject("os");
                String osName = getStrOrNull(osObj, "name");
                boolean osMatches = osName != null
                        && osName.equalsIgnoreCase(OsDetector.mojangName());
                if ("allow".equals(action) && !osMatches) {
                    return false;
                }
                if ("disallow".equals(action) && osMatches) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  JSON utility helpers
    // ------------------------------------------------------------------

    private static String getStr(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    private static String getStrOrNull(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsLong();
        }
        return fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsInt();
        }
        return fallback;
    }
}
