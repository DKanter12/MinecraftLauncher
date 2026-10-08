package org.example.launcher.infrastructure.mojang;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.net.HttpDefaults;

/**
 * Декоратор {@link MinecraftVersionRepository} с дисковым кэшем.
 * Есть интернет — берёт у remote и обновляет кэш.
 * Нет интернета ({@code IOException}) — отдаёт кэш.
 * Нет и кэша — пробрасывает исходную ошибку наружу.
 */
public class CachedMinecraftVersionRepository implements MinecraftVersionRepository {

    private final MinecraftVersionRepository remote;
    private final Path cacheFile;
    private final Gson gson;

    public CachedMinecraftVersionRepository(MinecraftVersionRepository remote,
                                            Path cacheFile) {
        this(remote, cacheFile, HttpDefaults.newGson());
    }

    public CachedMinecraftVersionRepository(MinecraftVersionRepository remote,
                                            Path cacheFile, Gson gson) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public List<MinecraftVersion> fetchVersions() throws IOException {
        try {
            List<MinecraftVersion> fresh = remote.fetchVersions();
            writeCache(fresh);
            return fresh;
        } catch (IOException remoteError) {
            List<MinecraftVersion> cached = readCache();
            if (cached != null) {
                return cached;
            }
            throw remoteError;
        }
    }

    private void writeCache(List<MinecraftVersion> versions) {
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<CachedDto> dtos = versions.stream().map(v -> {
                CachedDto d = new CachedDto();
                d.id = v.id();
                d.type = v.type().name();
                d.releaseTime = v.releaseTime().map(Object::toString).orElse(null);
                d.metadataUrl = v.metadataUrl();
                return d;
            }).toList();
            Files.writeString(cacheFile, gson.toJson(dtos), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            // кэш best-effort: не должен ломать успешную загрузку
        }
    }

    private List<MinecraftVersion> readCache() {
        if (!Files.isRegularFile(cacheFile)) {
            return null;
        }
        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            List<CachedDto> dtos = gson.fromJson(json,
                    new TypeToken<List<CachedDto>>() {}.getType());
            if (dtos == null) {
                return null;
            }
            return dtos.stream()
                    .filter(d -> d.id != null && !d.id.isBlank())
                    .map(d -> MinecraftVersion.of(d.id, safeType(d.type),
                            d.releaseTime, d.metadataUrl))
                    .toList();
        } catch (IOException | JsonSyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static VersionType safeType(String name) {
        if (name == null) {
            return VersionType.UNKNOWN;
        }
        try {
            return VersionType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return VersionType.UNKNOWN;
        }
    }

    /** Плоская форма доменной версии для кэша. */
    private static final class CachedDto {
        String id;
        String type;
        String releaseTime;
        String metadataUrl;
    }
}
