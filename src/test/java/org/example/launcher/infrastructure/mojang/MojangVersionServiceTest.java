package org.example.launcher.infrastructure.mojang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MojangVersionService manifest parsing")
class MojangVersionServiceTest {

    private static final String SAMPLE_MANIFEST = """
            {
              "latest": {
                "release": "1.21",
                "snapshot": "1.21-snapshot1"
              },
              "versions": [
                {
                  "id": "1.21",
                  "type": "release",
                  "url": "https://launchermeta.mojang.com/v1/packages/aaa/1.21.json",
                  "time": "2024-06-13T14:30:23+00:00",
                  "releaseTime": "2024-06-13T10:30:00+00:00"
                },
                {
                  "id": "1.21-snapshot1",
                  "type": "snapshot",
                  "url": "https://launchermeta.mojang.com/v1/packages/bbb/1.21-snapshot1.json",
                  "time": "2024-06-10T14:30:23+00:00",
                  "releaseTime": "2024-06-10T10:30:00+00:00"
                },
                {
                  "id": "b1.7.3",
                  "type": "old_beta",
                  "url": "https://launchermeta.mojang.com/v1/packages/ccc/b1.7.3.json",
                  "time": "2011-06-30T00:00:00+00:00",
                  "releaseTime": "2011-06-30T00:00:00+00:00"
                }
              ]
            }
            """;

    private MojangVersionService createService() {
        return new MojangVersionService("http://localhost");
    }

    @Test
    @DisplayName("parses version count correctly")
    void parsesVersionCount() throws IOException {
        List<MinecraftVersion> versions = createService().parseManifest(SAMPLE_MANIFEST);
        assertEquals(3, versions.size());
    }

    @Test
    @DisplayName("parses version id, type, metadata url and release time")
    void parsesVersionFields() throws IOException {
        List<MinecraftVersion> versions = createService().parseManifest(SAMPLE_MANIFEST);

        MinecraftVersion v21 = versions.stream()
                .filter(v -> v.id().equals("1.21"))
                .findFirst().orElseThrow();
        assertEquals(VersionType.RELEASE, v21.type());
        assertEquals("https://launchermeta.mojang.com/v1/packages/aaa/1.21.json", v21.metadataUrl());
        assertEquals("https://launchermeta.mojang.com/v1/packages/aaa/1.21.json", v21.url());
        assertTrue(v21.releaseTime().isPresent());
        assertEquals(2024, v21.releaseTime().get().getYear());
        assertTrue(v21.isVanilla());
    }

    @Test
    @DisplayName("maps snapshot type correctly")
    void mapsSnapshotType() throws IOException {
        List<MinecraftVersion> versions = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion snap = versions.stream()
                .filter(v -> v.id().equals("1.21-snapshot1"))
                .findFirst().orElseThrow();
        assertEquals(VersionType.SNAPSHOT, snap.type());
    }

    @Test
    @DisplayName("maps old_beta type correctly")
    void mapsOldBetaType() throws IOException {
        List<MinecraftVersion> versions = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion beta = versions.stream()
                .filter(v -> v.id().equals("b1.7.3"))
                .findFirst().orElseThrow();
        assertEquals(VersionType.BETA, beta.type());
    }

    @Test
    @DisplayName("releaseDate returns a human-readable date")
    void releaseDate() throws IOException {
        List<MinecraftVersion> versions = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion v21 = versions.get(0);
        assertTrue(v21.releaseDate().isPresent());
        assertTrue(v21.releaseDate().get().length() > 5);
    }

    @Test
    @DisplayName("throws on invalid JSON")
    void throwsOnInvalidJson() {
        assertThrows(IOException.class, () -> createService().parseManifest("{ not valid json"));
    }

    @Test
    @DisplayName("throws on manifest missing versions array")
    void throwsOnMissingVersions() {
        assertThrows(IOException.class, () -> createService().parseManifest("{\"latest\":{}}"));
    }

    @Test
    @DisplayName("handles unknown type string by falling back to UNKNOWN")
    void handlesUnknownType() throws IOException {
        String json = """
                {
                  "latest": { "release": "x", "snapshot": "y" },
                  "versions": [
                    { "id": "exp1", "type": "experimental", "url": "http://x", "time": "t", "releaseTime": "t" }
                  ]
                }
                """;
        List<MinecraftVersion> versions = createService().parseManifest(json);
        MinecraftVersion v = versions.get(0);
        assertEquals(VersionType.UNKNOWN, v.type());
    }
}
