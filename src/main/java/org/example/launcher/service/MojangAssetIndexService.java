package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;
import org.example.launcher.model.AssetObject;
import org.example.launcher.net.HttpDefaults;
import org.example.launcher.util.Json;

/**
 * Реализация {@link AssetIndexService} на официальном JSON asset-индекса Mojang.
 * <p>
 * Разбор JSON выделен в {@link #parseIndex(String)} для юнит-тестирования.
 */
public class MojangAssetIndexService implements AssetIndexService {

    private final HttpClient httpClient;
    private final Gson gson;

    public MojangAssetIndexService() {
        this(HttpDefaults.newClient(), HttpDefaults.newGson());
    }

    public MojangAssetIndexService(HttpClient httpClient, Gson gson) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public AssetIndexContent fetchIndex(AssetIndex assetIndex) throws IOException {
        String url = assetIndex.url();
        if (url == null || url.isBlank()) {
            throw new IOException("Asset index has no URL");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HttpDefaults.REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fetching asset index");
            }
            return parseIndex(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching asset index", e);
        }
    }

    /**
     * Разбирает сырую строку JSON asset-индекса.
     */
    public AssetIndexContent parseIndex(String json) throws IOException {
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse asset index JSON", e);
        }
        if (root == null) {
            throw new IOException("Asset index JSON is empty or invalid");
        }

        boolean virtual = root.has("virtual")
                && root.get("virtual").isJsonPrimitive()
                && root.get("virtual").getAsBoolean();

        Map<String, AssetObject> objects = new HashMap<>();
        if (root.has("objects") && root.get("objects").isJsonObject()) {
            JsonObject objs = root.getAsJsonObject("objects");
            for (var entry : objs.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject obj = entry.getValue().getAsJsonObject();
                String hash = getStrOrNull(obj, "hash");
                long size = getLong(obj, "size", 0);
                objects.put(entry.getKey(), new AssetObject(hash, size));
            }
        }

        return new AssetIndexContent(objects, virtual);
    }

    private static String getStrOrNull(JsonObject obj, String key) {
        return Json.getStringOrNull(obj, key);
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsLong();
        }
        return fallback;
    }
}
