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
import org.example.launcher.service.modloader.ModLoaderType;
import org.example.launcher.util.Json;

/**
 * Поставщик {@link ModLoaderVersionProvider} для Fabric Loader на официальном
 * Fabric meta API ({@code https://meta.fabricmc.net}).
 * <p>
 * {@code GET /v2/versions/loader/{game_version}} возвращает по одной записи на
 * совместимую версию загрузчика, каждая с версией загрузчика, сборкой и
 * флагом стабильности плюс целевые intermediary-мэппинги.
 */
public class FabricVersionProvider implements ModLoaderVersionProvider {

    public static final String DEFAULT_META_URL =
            "https://meta.fabricmc.net/v2/versions/loader/";

    /** Перечисляет версии игры, поддерживаемые Fabric (релизы + снапшоты). */
    public static final String DEFAULT_GAME_VERSIONS_URL =
            "https://meta.fabricmc.net/v2/versions/game";

    private final String metaUrl;
    private final String gameVersionsUrl;
    private final HttpClient httpClient;

    public FabricVersionProvider() {
        this(DEFAULT_META_URL);
    }

    public FabricVersionProvider(String metaUrl) {
        this(metaUrl, DEFAULT_GAME_VERSIONS_URL);
    }

    public FabricVersionProvider(String metaUrl, String gameVersionsUrl) {
        this(metaUrl, gameVersionsUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public FabricVersionProvider(String metaUrl, String gameVersionsUrl,
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
                throw new IOException("Fabric meta returned HTTP "
                        + response.statusCode() + " for MC " + minecraftVersion);
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Fabric versions", e);
        }

        List<ModLoaderVersion> versions = parseVersions(body, minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException("No Fabric Loader versions found for MC "
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
                throw new IOException("Fabric meta returned HTTP "
                        + response.statusCode() + " for the game version list");
            }
            body = response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Fabric game versions", e);
        }
        return parseGameVersions(body);
    }

    /**
     * Разбирает JSON списка игровых версий Fabric meta. Выставлен для юнит-
     * тестирования.
     * <p>
     * Форма ответа: {@code [{"version":"1.21.4","stable":true},
     * …]} — каждая версия, на которую ставится Fabric.
     */
    public java.util.Set<String> parseGameVersions(String json) throws IOException {
        java.util.Set<String> result = new java.util.HashSet<>();
        JsonElement rootElem;
        try {
            rootElem = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Fabric game version JSON", e);
        }
        if (!rootElem.isJsonArray()) {
            throw new IOException("Fabric game version JSON is not an array");
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
     * Разбирает JSON списка загрузчиков Fabric meta. Выставлен для юнит-тестирования.
     * <p>
     * Форма ответа: {@code [{"loader": {"version": "0.16.9",
     * "stable": true, "build": 10}, "intermediary": {...}}, …]}
     */
    public List<ModLoaderVersion> parseVersions(String json, String minecraftVersion)
            throws IOException {
        List<ModLoaderVersion> result = new ArrayList<>();
        JsonElement rootElem;
        try {
            rootElem = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse Fabric meta JSON", e);
        }
        if (!rootElem.isJsonArray()) {
            throw new IOException("Fabric meta JSON is not an array");
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
                    ModLoaderType.FABRIC, version, minecraftVersion, stable, null));
        }
        return result;
    }

    private static String getStr(JsonObject obj, String key) {
        return Json.getStringOrNull(obj, key);
    }
}
