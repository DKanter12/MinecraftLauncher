package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;
import org.example.launcher.model.AssetObject;

/**
 * {@link AssetIndexService} backed by the official Mojang asset index JSON.
 * <p>
 * JSON parsing is isolated in {@link #parseIndex(String)} for unit testing.
 */
public class MojangAssetIndexService implements AssetIndexService {

    private final HttpClient httpClient;
    private final Gson gson;

    public MojangAssetIndexService() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build(),
                new Gson());
    }

    public MojangAssetIndexService(HttpClient httpClient, Gson gson) {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public AssetIndexContent fetchIndex(AssetIndex assetIndex) throws IOException {
        String url = assetIndex.url();
        if (url == null || url.isBlank()) {
            throw new IOException("Asset index has no URL");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
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
     * Parses a raw asset index JSON string.
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
}
