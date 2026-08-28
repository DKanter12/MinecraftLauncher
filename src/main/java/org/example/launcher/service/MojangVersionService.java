package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.version.VersionType;
import org.example.launcher.version.VersionTypeRegistry;

/**
 * {@link VersionService} implementation backed by the official Mojang
 * version manifest ({@code version_manifest.json}).
 * <p>
 * The manifest URL, HTTP client and type registry are all injectable,
 * which keeps the class easy to test and configure. JSON parsing is
 * isolated in {@link #parseManifest(String)} so it can be unit-tested
 * without network access.
 */
public class MojangVersionService implements VersionService {

    public static final String DEFAULT_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final String manifestUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    private final VersionTypeRegistry typeRegistry;

    public MojangVersionService() {
        this(DEFAULT_MANIFEST_URL, new VersionTypeRegistry());
    }

    public MojangVersionService(String manifestUrl, VersionTypeRegistry typeRegistry) {
        this(manifestUrl,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                new Gson(),
                typeRegistry);
    }

    public MojangVersionService(String manifestUrl,
                               HttpClient httpClient,
                               Gson gson,
                               VersionTypeRegistry typeRegistry) {
        this.manifestUrl = manifestUrl;
        this.httpClient = httpClient;
        this.gson = gson;
        this.typeRegistry = typeRegistry;
    }

    @Override
    public VersionManifest fetchVersions() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(manifestUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected HTTP status " + response.statusCode()
                        + " fetching version manifest from " + manifestUrl);
            }
            return parseManifest(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching version manifest", e);
        }
    }

    /**
     * Parses a raw manifest JSON string into a {@link VersionManifest}.
     * Exposed as package-private for unit testing.
     */
    VersionManifest parseManifest(String json) throws IOException {
        ManifestDto dto;
        try {
            dto = gson.fromJson(json, ManifestDto.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse version manifest JSON", e);
        }
        if (dto == null || dto.versions == null) {
            throw new IOException("Version manifest is missing required fields");
        }

        List<MinecraftVersion> versions = new ArrayList<>(dto.versions.size());
        for (VersionDto v : dto.versions) {
            VersionType type = typeRegistry.resolve(v.type);
            versions.add(new MinecraftVersion(v.id, type, v.releaseTime, v.url));
        }

        String latestRelease = (dto.latest != null) ? dto.latest.release : null;
        String latestSnapshot = (dto.latest != null) ? dto.latest.snapshot : null;
        return new VersionManifest(latestRelease, latestSnapshot, versions);
    }

    // ---- internal Gson DTOs mirroring the Mojang manifest structure ----

    static final class ManifestDto {
        LatestDto latest;
        List<VersionDto> versions;
    }

    static final class LatestDto {
        String release;
        String snapshot;
    }

    static final class VersionDto {
        String id;
        String type;
        String url;
        String time;
        String releaseTime;
    }
}
