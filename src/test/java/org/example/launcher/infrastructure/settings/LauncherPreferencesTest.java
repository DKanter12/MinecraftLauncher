package org.example.launcher.infrastructure.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.LauncherPreferences;

@DisplayName("FileSettingsRepository")
class LauncherPreferencesTest {

    @TempDir
    Path tempDir;

    private LauncherPreferences full() {
        return new LauncherPreferences("b1.8.1", "Steve",
                "https://raw.githubusercontent.com/o/r/main/", "YANDEX",
                "https://disk.yandex.ru/d/abc", "tok", "ru");
    }

    @Test
    @DisplayName("returns empty preferences when file does not exist")
    void emptyWhenNoFile() throws IOException {
        FileSettingsRepository prefs = new FileSettingsRepository(tempDir.resolve("prefs.json"));
        assertEquals(LauncherPreferences.empty(), prefs.loadPreferences());
    }

    @Test
    @DisplayName("saves and loads all settings in one write")
    void saveAndLoad() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);

        prefs.savePreferences(full());

        assertEquals(full(), prefs.loadPreferences());
    }

    @Test
    @DisplayName("blank and null values load as null")
    void blankAndNull() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);

        prefs.savePreferences(new LauncherPreferences("", null, "  ",
                null, null, null, null));

        LauncherPreferences loaded = prefs.loadPreferences();
        assertNull(loaded.lastSelectedVersion());
        assertNull(loaded.lastSelectedAccount());
        assertNull(loaded.buildsGitUrl());
    }

    @Test
    @DisplayName("handles corrupt JSON gracefully without rewriting it")
    void corruptJson() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        Files.writeString(file, "not valid json {{{");

        FileSettingsRepository prefs = new FileSettingsRepository(file);
        assertEquals(LauncherPreferences.empty(), prefs.loadPreferences());
    }

    @Test
    @DisplayName("creates parent directories if needed")
    void createsParents() throws IOException {
        Path file = tempDir.resolve("sub").resolve("dir").resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);

        prefs.savePreferences(new LauncherPreferences("1.21", null, null,
                null, null, null, null));

        assertTrue(Files.isRegularFile(file));
        assertEquals("1.21", prefs.loadPreferences().lastSelectedVersion());
    }

    @Test
    @DisplayName("partial update keeps other settings")
    void partialUpdate() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);
        prefs.savePreferences(full());

        LauncherPreferences saved = prefs.loadPreferences();
        prefs.savePreferences(new LauncherPreferences(
                saved.lastSelectedVersion(), "Alex", saved.buildsGitUrl(),
                saved.buildsSourceMode(), saved.yandexDiskLink(),
                saved.buildsToken(), saved.language()));

        LauncherPreferences loaded = prefs.loadPreferences();
        assertEquals("Alex", loaded.lastSelectedAccount());
        assertEquals("b1.8.1", loaded.lastSelectedVersion());
        assertEquals("tok", loaded.buildsToken());
    }

    @Test
    @DisplayName("file format stays compatible with previous versions")
    void fileFormat() throws IOException {
        Path file = tempDir.resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);
        prefs.savePreferences(full());

        String json = Files.readString(file);
        assertTrue(json.contains("\"lastSelectedVersion\""));
        assertTrue(json.contains("\"buildsSourceMode\""));
        assertTrue(json.contains("\"language\""));
    }
}
