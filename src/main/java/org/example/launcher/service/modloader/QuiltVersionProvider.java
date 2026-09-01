package org.example.launcher.service.modloader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.model.ModLoaderVersion;

/**
 * {@link ModLoaderVersionProvider} for Quilt Loader, backed by the
 * official Quilt meta API ({@code https://meta.quiltmc.org}).
 * <p>
 * {@code GET /v3/versions/loader/{game_version}} returns one entry per
 * compatible loader version. The response shape mirrors the Fabric
 * meta API (Quilt loader is a Fabric fork).
 */
public class QuiltVersionProvider implements ModLoaderVersionProvider {

    public static final String DEFAULT_META_URL =
            "https://meta.quiltmc.org/v3/versions/loader/";

    /** Lists the game versions Quilt supports (releases + snapshots). */
    public static final String DEFAULT_GAME_VERSIONS_URL =
            "https://meta.quiltmc.org/v3/versions/game";

    private final String metaUrl;
    private final String gameVersionsUrl;
    private final HttpClient httpClient;

    public QuiltVersionProvider() {
        this(DEFAULT_META_URL);
    }

    public QuiltVersionProvider(String metaUrl) {
        this(metaUrl, DEFAULT_GAME_VERSIONS_URL);
    }

    public QuiltVersionProvider(String metaUrl, String gameVersionsUrl) {
        this(metaUrl, gameVersionsUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public QuiltVersionProvider(String metaUrl, String gameVersionsUrl,
                                HttpClient httpClient) {
        this.metaUrl = metaUrl.endsWith("/") ? metaUrl : metaUrl + "/";
        this.gameVersionsUrl = gameVersionsUrl;
        this.httpClient = httpClient;
    }

    @Override
    public List<ModLoaderVersion> fetchVersions(String minecraftVersion) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(metaUrl + minecraftVersion))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        String body;
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Quilt meta returned HTTP "
                        + response.statusCode() + " for MC " + minecraftVersion);
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Quilt versions", e);
        }

        List<ModLoaderVersion> versions = parseVersions(body, minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException("No Quilt Loader versions found for MC "
                    + minecraftVersion + " — the version may be unsupported");
        }
        return versions;
    }

    @Override
    public java.util.Set<String> fetchSupportedMinecraftVersions() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(gameVersionsUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        String body;
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Quilt meta returned HTTP "
                        + response.statusCode() + " for the game version list");
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Quilt game versions", e);
        }
        return parseGameVersions(body);
    }

    /**
     * Parses the Quilt meta game-version list JSON. Exposed for unit
     * testing.
     * <p>
     * Response shape: {@code [{"version":"1.21.4",…}, …]} — every
     * version Quilt can be installed on.
     */
    public java.util.Set<String> parseGameVersions(String json) throws IOException {
        java.util.Set<String> result = new java.util.HashSet<>();
        JsonElement rootElem;
        try {
            rootElem = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Quilt game version JSON", e);
        }
        if (!rootElem.isJsonArray()) {
            throw new IOException("Quilt game version JSON is not an array");
        }
        for (JsonElement elem : rootElem.getAsJsonArray()) {
            if (!elem.isJsonObject()) continue;
            String version = getStr(elem.getAsJsonObject(), "version");
            if (version != null && !version.isBlank()) {
                result.add(version);
            }
        }
        return result;
    }

    /**
     * Parses the Quilt meta loader-list JSON. Exposed for unit testing.
     * <p>
     * Response shape: {@code [{"loader": {"version": "0.26.0",
     * "stable": true, "build": 3}, "hashed": {...}}, …]}
     */
    public List<ModLoaderVersion> parseVersions(String json, String minecraftVersion)
            throws IOException {
        List<ModLoaderVersion> result = new ArrayList<>();
        JsonElement rootElem;
        try {
            rootElem = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Quilt meta JSON", e);
        }
        if (!rootElem.isJsonArray()) {
            throw new IOException("Quilt meta JSON is not an array");
        }

        for (JsonElement elem : rootElem.getAsJsonArray()) {
            if (!elem.isJsonObject()) continue;
            JsonObject entry = elem.getAsJsonObject();
            if (!entry.has("loader") || !entry.get("loader").isJsonObject()) continue;

            JsonObject loader = entry.getAsJsonObject("loader");
            String version = getStr(loader, "version");
            if (version == null || version.isBlank()) continue;
            boolean stable = loader.has("stable") && loader.get("stable").getAsBoolean();

            result.add(new ModLoaderVersion(
                    ModLoaderType.QUILT, version, minecraftVersion, stable, null));
        }
        return result;
    }

    private static String getStr(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
