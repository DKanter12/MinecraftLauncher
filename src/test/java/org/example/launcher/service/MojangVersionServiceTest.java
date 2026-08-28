package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.version.StandardVersionType;
import org.example.launcher.version.VersionTypeRegistry;
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
        return new MojangVersionService(
                "http://localhost",
                new VersionTypeRegistry());
    }

    @Test
    @DisplayName("parses version count correctly")
    void parsesVersionCount() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);
        assertEquals(3, manifest.versions().size());
    }

    @Test
    @DisplayName("parses latest release and snapshot ids")
    void parsesLatestIds() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);
        assertEquals(Optional.of("1.21"), manifest.latestReleaseId());
        assertEquals(Optional.of("1.21-snapshot1"), manifest.latestSnapshotId());
    }

    @Test
    @DisplayName("parses version id, type, metadata url and release time")
    void parsesVersionFields() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);

        MinecraftVersion v21 = manifest.versions().stream()
                .filter(v -> v.id().equals("1.21"))
                .findFirst().orElseThrow();
        assertEquals(StandardVersionType.RELEASE, v21.type());
        assertEquals("https://launchermeta.mojang.com/v1/packages/aaa/1.21.json", v21.metadataUrl());
        assertEquals("2024-06-13T10:30:00+00:00", v21.releaseTimeRaw());
        assertTrue(v21.releaseTime().isPresent());
        assertEquals(2024, v21.releaseTime().get().getYear());
    }

    @Test
    @DisplayName("maps snapshot type correctly")
    void mapsSnapshotType() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion snap = manifest.versions().stream()
                .filter(v -> v.id().equals("1.21-snapshot1"))
                .findFirst().orElseThrow();
        assertEquals(StandardVersionType.SNAPSHOT, snap.type());
        assertEquals("Snapshot", snap.type().displayName());
    }

    @Test
    @DisplayName("maps old_beta type correctly")
    void mapsOldBetaType() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion beta = manifest.versions().stream()
                .filter(v -> v.id().equals("b1.7.3"))
                .findFirst().orElseThrow();
        assertEquals(StandardVersionType.OLD_BETA, beta.type());
    }

    @Test
    @DisplayName("formattedReleaseTime returns a human-readable date")
    void formattedReleaseTime() throws IOException {
        VersionManifest manifest = createService().parseManifest(SAMPLE_MANIFEST);
        MinecraftVersion v21 = manifest.versions().get(0);
        assertNotNull(v21.formattedReleaseTime());
        assertTrue(v21.formattedReleaseTime().length() > 5);
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
        VersionManifest manifest = createService().parseManifest(json);
        MinecraftVersion v = manifest.versions().get(0);
        assertEquals(StandardVersionType.UNKNOWN, v.type());
    }
}
