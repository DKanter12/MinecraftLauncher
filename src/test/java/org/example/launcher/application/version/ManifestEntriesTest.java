package org.example.launcher.application.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.application.build.BuildRequest;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.version.StandardVersionType;

@DisplayName("ManifestEntries")
class ManifestEntriesTest {

    @Test
    @DisplayName("maps domain types to legacy manifest types")
    void typeMapping() {
        assertEquals(StandardVersionType.RELEASE,
                ManifestEntries.toLegacyType(VersionType.RELEASE));
        assertEquals(StandardVersionType.SNAPSHOT,
                ManifestEntries.toLegacyType(VersionType.SNAPSHOT));
        assertEquals(StandardVersionType.OLD_BETA,
                ManifestEntries.toLegacyType(VersionType.BETA));
        assertEquals(StandardVersionType.OLD_ALPHA,
                ManifestEntries.toLegacyType(VersionType.ALPHA));
        assertEquals(StandardVersionType.UNKNOWN,
                ManifestEntries.toLegacyType(VersionType.UNKNOWN));
    }

    @Test
    @DisplayName("toManifestEntry keeps id and url")
    void entryMapping() {
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                "2025-10-07T10:00:00+00:00", "https://x/1.21.11.json");

        var entry = ManifestEntries.toManifestEntry(version);

        assertEquals("1.21.11", entry.id());
        assertEquals("https://x/1.21.11.json", entry.metadataUrl());
    }

    @Test
    @DisplayName("toRequest builds a consistent downloadable request")
    void requestMapping() {
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, "https://x/1.21.11.json");

        BuildRequest request = ManifestEntries.toRequest(version);

        assertTrue(request.isVanilla());
        assertEquals("1.21.11", request.versionId());
    }

    @Test
    @DisplayName("modded request carries core and loader version")
    void moddedRequest() {
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null, ModLoaderType.FABRIC, "0.19.5");

        BuildRequest request = ManifestEntries.toRequest(version);

        assertEquals(ModLoaderType.FABRIC, request.type());
        assertEquals("fabric-loader-0.19.5-1.21.11", request.versionId());
        assertEquals("0.19.5", request.loader().loaderVersion());
    }

    @Test
    @DisplayName("baseEntry falls back to repository when url is missing")
    void baseEntryFallback() throws IOException {
        var repo = (org.example.launcher.domain.port.MinecraftVersionRepository) () ->
                List.of(MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                        null, "https://x/1.21.11.json"));
        var withoutUrl = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                null, null);

        var entry = ManifestEntries.baseEntry(withoutUrl, repo);

        assertEquals("https://x/1.21.11.json", entry.metadataUrl());
    }

    @Test
    @DisplayName("baseEntry of unknown version fails with IOException")
    void baseEntryMissing() {
        var repo = (org.example.launcher.domain.port.MinecraftVersionRepository) List::of;
        var version = MinecraftVersion.of("9.9.9", VersionType.RELEASE,
                null, null);

        IOException error = assertThrows(IOException.class,
                () -> ManifestEntries.baseEntry(version, repo));
        assertTrue(error.getMessage().contains("9.9.9"));
    }

    @Test
    @DisplayName("domain version exposes core, url and release date")
    void domainFields() {
        var version = MinecraftVersion.of("1.21.11", VersionType.RELEASE,
                "2025-10-07T10:00:00+00:00", "https://x/1.21.11.json",
                ModLoaderType.FABRIC, "0.19.5");

        assertEquals(ModLoaderType.FABRIC, version.core());
        assertEquals("0.19.5", version.loaderVersion());
        assertEquals("https://x/1.21.11.json", version.url());
        assertEquals("07.10.2025", version.releaseDate().orElseThrow());
    }
}
