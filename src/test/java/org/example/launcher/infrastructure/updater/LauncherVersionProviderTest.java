package org.example.launcher.infrastructure.updater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.LauncherVersion;
import org.example.launcher.infrastructure.http.UrlFetcher;

@DisplayName("LauncherVersionProvider")
class LauncherVersionProviderTest {

    private static String manifest(String version) {
        return """
                {"version": "%s", "notes": "notes here",
                 "package": {"url": "https://example.com/l.zip",
                             "sha256": "%s", "size": 10}}
                """.formatted(version, "ab".repeat(32));
    }

    @Test
    @DisplayName("maps manifest fields to LauncherVersion")
    void maps(@TempDir Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Files.writeString(manifest, manifest("1.2.0"));
        var provider = new LauncherVersionProvider(new UrlFetcher(),
                manifest.toUri().toString());

        LauncherVersion latest = provider.getLatestVersion();

        assertEquals("1.2.0", latest.version());
        assertEquals("https://example.com/l.zip", latest.downloadUrl());
        assertEquals(10, latest.fileSize());
        assertEquals(64, latest.sha256().length());
        assertEquals("notes here", latest.releaseNotes());
    }

    @Test
    @DisplayName("broken manifest fails with IOException")
    void broken(@TempDir Path dir) throws IOException {
        Path manifest = dir.resolve("manifest.json");
        Files.writeString(manifest, "{ broken");
        var provider = new LauncherVersionProvider(new UrlFetcher(),
                manifest.toUri().toString());

        assertThrows(IOException.class, provider::getLatestVersion);
    }

    @Test
    @DisplayName("unreachable server fails with IOException")
    void offline() {
        var provider = new LauncherVersionProvider(new UrlFetcher(),
                "http://127.0.0.1:1/manifest.json");

        assertThrows(IOException.class, provider::getLatestVersion);
    }
}
