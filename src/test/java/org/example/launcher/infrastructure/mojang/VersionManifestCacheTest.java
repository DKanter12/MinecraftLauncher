package org.example.launcher.infrastructure.mojang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;

@DisplayName("VersionManifestCache: файл версий лаунчера")
class VersionManifestCacheTest {

    private static final String SAMPLE = """
            {
              "source": "mojang",
              "fetched": "2026-11-10T12:00:00+03:00",
              "versions": [
                {
                  "id": "1.21.11",
                  "type": "release",
                  "releaseTime": "2026-10-01T10:00:00+00:00",
                  "url": "https://launchermeta.mojang.com/v1/packages/abc/1.21.11.json"
                },
                {
                  "id": "26w40a",
                  "type": "snapshot",
                  "releaseTime": null,
                  "url": null
                }
              ]
            }""";

    @Test
    @DisplayName("parse читает id/тип/дату/URL из файла версий")
    void parsesSample() {
        List<MinecraftVersion> versions = VersionManifestCache.parse(SAMPLE);

        assertEquals(2, versions.size());
        MinecraftVersion release = versions.get(0);
        assertEquals("1.21.11", release.id());
        assertEquals(VersionType.RELEASE, release.type());
        assertTrue(release.releaseDate().isPresent());
        assertEquals("01.10.2026", release.releaseDate().get());
        assertEquals("https://launchermeta.mojang.com/v1/packages/abc/1.21.11.json",
                release.url());

        MinecraftVersion snapshot = versions.get(1);
        assertEquals(VersionType.SNAPSHOT, snapshot.type());
        assertTrue(snapshot.releaseDate().isEmpty());
    }

    @Test
    @DisplayName("пустые/битые записи пропускаются, битый JSON даёт пусто")
    void toleratesGarbage() {
        assertTrue(VersionManifestCache.parse(
                "{\"versions\":[{\"id\":\" \",\"type\":\"release\"}]}").isEmpty());
        assertTrue(VersionManifestCache.parse("{ nope").isEmpty());
        assertTrue(VersionManifestCache.parse("null").isEmpty());
    }

    @Test
    @DisplayName("save/load: полный round-trip в temp-каталоге")
    void roundTrip(@TempDir Path tempDir) {
        Path file = tempDir.resolve("launcher").resolve("versions.json");
        List<MinecraftVersion> versions = VersionManifestCache.parse(SAMPLE);

        assertTrue(VersionManifestCache.save(file, versions));
        assertTrue(Files.isRegularFile(file));
        assertEquals(versions, VersionManifestCache.load(file));
    }

    @Test
    @DisplayName("save пустого списка OCI отсутствующего файла: false/пусто")
    void emptyAndMissing() throws Exception {
        Path file = Path.of(System.getProperty("java.io.tmpdir"),
                "vmc-must-not-exist-" + System.nanoTime() + ".json");
        assertFalse(VersionManifestCache.save(file, List.of()));
        assertTrue(VersionManifestCache.load(file).isEmpty());
    }
}
