package org.example.launcher.infrastructure.mojang;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.net.HttpDefaults;

/**
 * Реализация {@link MinecraftVersionRepository} на официальном манифесте Mojang.
 * Mojang-DTO живут здесь же как приватные классы и наружу не выходят:
 * за границей — только доменная модель.
 */
public class MojangMinecraftVersionRepository implements MinecraftVersionRepository {

    public static final String DEFAULT_MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String manifestUrl;
    private final HttpClient httpClient;
    private final Gson gson;

    public MojangMinecraftVersionRepository() {
        this(DEFAULT_MANIFEST_URL, HttpDefaults.newClient(), HttpDefaults.newGson());
    }

    public MojangMinecraftVersionRepository(String manifestUrl,
                                            HttpClient httpClient, Gson gson) {
        this.manifestUrl = Objects.requireNonNull(manifestUrl, "manifestUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public List<MinecraftVersion> fetchVersions() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(manifestUrl))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
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
     * Разбирает сырой JSON манифеста в доменные версии.
     * Видимость на уровне пакета — для юнит-тестов без сети.
     */
    List<MinecraftVersion> parseManifest(String json) throws IOException {
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
            if (v.id == null || v.id.isBlank()) {
                continue;
            }
            versions.add(MinecraftVersion.of(
                    v.id, VersionType.fromMojangId(v.type), v.releaseTime, v.url));
        }
        return versions;
    }

    // ---- внутренние Gson-DTO, зеркалящие структуру манифеста Mojang ----

    private static final class ManifestDto {
        List<VersionDto> versions;
    }

    private static final class VersionDto {
        String id;
        String type;
        String url;
        String time;
        String releaseTime;
    }
}
