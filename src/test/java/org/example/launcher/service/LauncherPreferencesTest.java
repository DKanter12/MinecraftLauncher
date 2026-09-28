package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LauncherPreferences")
class LauncherPreferencesTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("returns empty when file does not exist")
    void emptyWhenNoFile() throws IOException {
        LauncherPreferences prefs = new LauncherPreferences(tempDir.resolve("prefs.json"));
        assertTrue(prefs.getLastSelectedVersion().isEmpty());
    }

    @Test
    @DisplayName("saves and loads last selected version")
    void saveAndLoad() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion("b1.8.1");

        assertEquals("b1.8.1", prefs.getLastSelectedVersion().orElseThrow());
    }

    @Test
    @DisplayName("overwrites previous value")
    void overwrite() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion("1.21");
        prefs.setLastSelectedVersion("1.20.4");

        assertEquals("1.20.4", prefs.getLastSelectedVersion().orElseThrow());
    }

    @Test
    @DisplayName("returns empty for blank value")
    void blankValue() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion("");

        assertTrue(prefs.getLastSelectedVersion().isEmpty());
    }

    @Test
    @DisplayName("returns empty for null value")
    void nullValue() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion(null);

        assertTrue(prefs.getLastSelectedVersion().isEmpty());
    }

    @Test
    @DisplayName("handles corrupt JSON gracefully")
    void corruptJson() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        Files.writeString(file, "not valid json {{{");

        LauncherPreferences prefs = new LauncherPreferences(file);
        assertTrue(prefs.getLastSelectedVersion().isEmpty());
    }

    @Test
    @DisplayName("creates parent directories if needed")
    void createsParents() throws IOException {
        Path file = tempDir.resolve("sub").resolve("dir").resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion("1.21");

        assertTrue(Files.isRegularFile(file));
        assertEquals("1.21", prefs.getLastSelectedVersion().orElseThrow());
    }

    @Test
    @DisplayName("saves and loads last selected account")
    void saveAndLoadAccount() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedAccount("Steve");

        assertEquals("Steve", prefs.getLastSelectedAccount().orElseThrow());
    }

    @Test
    @DisplayName("version and account persist together")
    void bothPersistTogether() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        prefs.setLastSelectedVersion("1.21");
        prefs.setLastSelectedAccount("Alex");

        assertEquals("1.21", prefs.getLastSelectedVersion().orElseThrow());
        assertEquals("Alex", prefs.getLastSelectedAccount().orElseThrow());
    }

    @Test
    @DisplayName("builds git url round-trip")
    void buildsGitUrlRoundTrip() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        assertTrue(prefs.getBuildsGitUrl().isEmpty());

        prefs.setBuildsGitUrl("https://raw.githubusercontent.com/o/r/main/");

        assertEquals("https://raw.githubusercontent.com/o/r/main/",
                prefs.getBuildsGitUrl().orElseThrow());

        prefs.setBuildsGitUrl("");

        assertTrue(prefs.getBuildsGitUrl().isEmpty());
    }

    @Test
    @DisplayName("builds source settings round-trip")
    void buildsSourceRoundTrip() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        assertTrue(prefs.getBuildsSourceMode().isEmpty());
        assertTrue(prefs.getYandexDiskLink().isEmpty());
        assertTrue(prefs.getBuildsToken().isEmpty());

        prefs.setBuildsSourceMode("YANDEX");
        prefs.setYandexDiskLink("https://disk.yandex.ru/d/abc");
        prefs.setBuildsToken("tok");

        assertEquals("YANDEX", prefs.getBuildsSourceMode().orElseThrow());
        assertEquals("https://disk.yandex.ru/d/abc",
                prefs.getYandexDiskLink().orElseThrow());
        assertEquals("tok", prefs.getBuildsToken().orElseThrow());
    }

    @Test
    @DisplayName("language round-trip")
    void languageRoundTrip() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        LauncherPreferences prefs = new LauncherPreferences(file);

        assertTrue(prefs.getLanguage().isEmpty());

        prefs.setLanguage("ru");

        assertEquals("ru", prefs.getLanguage().orElseThrow());

        prefs.setLanguage("");

        assertTrue(prefs.getLanguage().isEmpty());
    }
}
