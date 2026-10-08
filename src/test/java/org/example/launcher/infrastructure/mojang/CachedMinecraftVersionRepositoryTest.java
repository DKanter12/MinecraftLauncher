package org.example.launcher.infrastructure.mojang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;

@DisplayName("CachedMinecraftVersionRepository")
class CachedMinecraftVersionRepositoryTest {

    private static List<MinecraftVersion> sample(String id) {
        return List.of(MinecraftVersion.of(id, VersionType.RELEASE,
                "2024-12-03T10:00:00+00:00", "https://example.com/" + id + ".json"));
    }

    private static MinecraftVersionRepository fake(List<MinecraftVersion> result,
                                                   IOException failure) {
        return () -> {
            if (failure != null) {
                throw failure;
            }
            return result;
        };
    }

    @Test
    @DisplayName("remote success is returned and written to cache")
    void cachesSuccess(@TempDir Path tempDir) throws IOException {
        Path cache = tempDir.resolve("nested").resolve("versions.json");
        var repo = new CachedMinecraftVersionRepository(
                fake(sample("1.21.4"), null), cache, new Gson());

        List<MinecraftVersion> versions = repo.fetchVersions();

        assertEquals(1, versions.size());
        assertEquals("1.21.4", versions.get(0).id());
        assertTrue(Files.isRegularFile(cache));
    }

    @Test
    @DisplayName("remote failure falls back to cache")
    void fallsBackToCache(@TempDir Path tempDir) throws IOException {
        Path cache = tempDir.resolve("versions.json");
        var priming = new CachedMinecraftVersionRepository(
                fake(sample("1.21.4"), null), cache, new Gson());
        priming.fetchVersions();

        var offline = new CachedMinecraftVersionRepository(
                fake(null, new IOException("no internet")), cache, new Gson());
        List<MinecraftVersion> versions = offline.fetchVersions();

        assertEquals(1, versions.size());
        assertEquals("1.21.4", versions.get(0).id());
        assertEquals("https://example.com/1.21.4.json",
                versions.get(0).metadataUrl());
    }

    @Test
    @DisplayName("remote failure without cache rethrows")
    void rethrowsWithoutCache(@TempDir Path tempDir) {
        var repo = new CachedMinecraftVersionRepository(
                fake(null, new IOException("no internet")),
                tempDir.resolve("versions.json"), new Gson());

        assertThrows(IOException.class, repo::fetchVersions);
    }

    @Test
    @DisplayName("remote success overwrites stale cache")
    void overwritesStaleCache(@TempDir Path tempDir) throws IOException {
        Path cache = tempDir.resolve("versions.json");
        Files.writeString(cache, "corrupted {{{");

        var repo = new CachedMinecraftVersionRepository(
                fake(sample("1.20.1"), null), cache, new Gson());
        List<MinecraftVersion> versions = repo.fetchVersions();

        assertEquals("1.20.1", versions.get(0).id());
        var reread = new CachedMinecraftVersionRepository(
                fake(null, new IOException("offline")), cache, new Gson());
        assertEquals("1.20.1", reread.fetchVersions().get(0).id());
    }
}
