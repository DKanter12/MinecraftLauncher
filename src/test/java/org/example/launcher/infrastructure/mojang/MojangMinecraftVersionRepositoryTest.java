package org.example.launcher.infrastructure.mojang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.infrastructure.http.HttpDefaults;

@DisplayName("MojangMinecraftVersionRepository")
class MojangMinecraftVersionRepositoryTest {

    private static final String SAMPLE = """
            {
              "versions": [
                {"id": "1.21.4", "type": "release",
                 "url": "https://example.com/1.21.4.json",
                 "time": "2024-12-03T10:00:00+00:00",
                 "releaseTime": "2024-12-03T10:00:00+00:00"},
                {"id": "25w14a", "type": "snapshot",
                 "url": "https://example.com/25w14a.json",
                 "time": "2025-04-01T10:00:00+00:00",
                 "releaseTime": "2025-04-01T10:00:00+00:00"},
                {"id": "b1.7.3", "type": "old_beta",
                 "url": "https://example.com/b1.7.3.json",
                 "time": "2011-07-08T10:00:00+00:00",
                 "releaseTime": "2011-07-08T10:00:00+00:00"},
                {"id": "future-1", "type": "experimental",
                 "url": "https://example.com/future-1.json",
                 "time": "2026-01-01T00:00:00+00:00",
                 "releaseTime": "not-a-date"},
                {"id": "  ", "type": "release",
                 "url": "https://example.com/blank.json",
                 "releaseTime": "2024-01-01T00:00:00+00:00"}
              ]
            }
            """;

    private MojangMinecraftVersionRepository repository() {
        HttpClient client = HttpDefaults.newClient();
        return new MojangMinecraftVersionRepository(
                "https://example.invalid/manifest.json", client, new Gson());
    }

    @Test
    @DisplayName("parseManifest maps Mojang types to domain enum, keeps order")
    void parsesTypes() throws IOException {
        List<MinecraftVersion> versions = repository().parseManifest(SAMPLE);

        assertEquals(4, versions.size());
        assertEquals("1.21.4", versions.get(0).id());
        assertEquals(VersionType.RELEASE, versions.get(0).type());
        assertEquals(VersionType.SNAPSHOT, versions.get(1).type());
        assertEquals(VersionType.BETA, versions.get(2).type());
        assertEquals("https://example.com/1.21.4.json", versions.get(0).metadataUrl());
    }

    @Test
    @DisplayName("parseManifest maps unknown types to UNKNOWN and keeps bad dates empty")
    void toleratesUnknowns() throws IOException {
        List<MinecraftVersion> versions = repository().parseManifest(SAMPLE);

        assertEquals(VersionType.UNKNOWN, versions.get(3).type());
        assertTrue(versions.get(3).releaseTime().isEmpty());
        assertTrue(versions.get(0).releaseTime().isPresent());
    }

    @Test
    @DisplayName("parseManifest rejects broken manifests")
    void rejectsBroken() {
        assertThrows(IOException.class,
                () -> repository().parseManifest("not json {{{"));
        assertThrows(IOException.class,
                () -> repository().parseManifest("{}"));
        assertThrows(IOException.class,
                () -> repository().parseManifest("{\"versions\": null}"));
    }
}
