package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.model.GameProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ProfileService")
class ProfileServiceTest {

    private static final String SAMPLE_JSON = """
            {
              "profiles": {
                "Steve": {
                  "name": "Steve",
                  "type": "offline"
                },
                "Alex": {
                  "name": "Alex",
                  "uuid": "00000000-0000-0000-0000-000000000000",
                  "accessToken": "token123",
                  "type": "Mojang"
                }
              },
              "selectedProfile": "Steve"
            }
            """;

    @Test
    @DisplayName("parseProfiles parses offline and online profiles")
    void parsesProfiles() throws IOException {
        ProfileService service = new ProfileService(Path.of("ignored"));
        List<GameProfile> profiles = service.parseProfiles(SAMPLE_JSON);

        assertEquals(2, profiles.size());

        GameProfile steve = profiles.stream().filter(p -> p.name().equals("Steve")).findFirst().orElseThrow();
        assertFalse(steve.isOnline());
        assertTrue(steve.uuid().isEmpty());
        assertTrue(steve.accessToken().isEmpty());

        GameProfile alex = profiles.stream().filter(p -> p.name().equals("Alex")).findFirst().orElseThrow();
        assertTrue(alex.isOnline());
        assertTrue(alex.uuid().isPresent());
        assertTrue(alex.accessToken().isPresent());
    }

    @Test
    @DisplayName("parseProfiles returns empty list for empty JSON")
    void parsesEmptyJson() throws IOException {
        ProfileService service = new ProfileService(Path.of("ignored"));
        assertTrue(service.parseProfiles("").isEmpty());
        assertTrue(service.parseProfiles(null).isEmpty());
    }

    @Test
    @DisplayName("parseProfiles returns empty list for missing profiles field")
    void parsesMissingProfiles() throws IOException {
        ProfileService service = new ProfileService(Path.of("ignored"));
        assertTrue(service.parseProfiles("{}").isEmpty());
    }

    @Test
    @DisplayName("parseProfiles throws on invalid JSON")
    void throwsOnInvalidJson() {
        ProfileService service = new ProfileService(Path.of("ignored"));
        assertThrows(IOException.class, () -> service.parseProfiles("{ broken"));
    }

    @Test
    @DisplayName("serializeProfiles produces valid JSON that can be re-parsed")
    void serializeAndReparse() throws IOException {
        ProfileService service = new ProfileService(Path.of("ignored"));

        List<GameProfile> original = List.of(
                GameProfile.offline("TestPlayer"),
                new GameProfile("Premium", "uuid-123", "token-abc", true)
        );

        String json = service.serializeProfiles(original);
        List<GameProfile> reparsed = service.parseProfiles(json);

        assertEquals(2, reparsed.size());
        assertEquals("TestPlayer", reparsed.get(0).name());
        assertFalse(reparsed.get(0).isOnline());
        assertEquals("Premium", reparsed.get(1).name());
        assertTrue(reparsed.get(1).isOnline());
        assertTrue(reparsed.get(1).uuid().isPresent());
    }

    @Test
    @DisplayName("loadProfiles returns empty list when file does not exist")
    void loadNonExistent(@TempDir Path dir) throws IOException {
        ProfileService service = new ProfileService(dir.resolve("nonexistent.json"));
        assertTrue(service.loadProfiles().isEmpty());
    }

    @Test
    @DisplayName("saveProfiles creates file and loadProfiles reads it back")
    void saveAndLoad(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("launcher_profiles.json");
        ProfileService service = new ProfileService(file);

        service.saveProfiles(List.of(GameProfile.offline("MyPlayer")));

        assertTrue(Files.isRegularFile(file));

        List<GameProfile> loaded = service.loadProfiles();
        assertEquals(1, loaded.size());
        assertEquals("MyPlayer", loaded.get(0).name());
        assertFalse(loaded.get(0).isOnline());
    }

    @Test
    @DisplayName("addOfflineProfile adds a new profile and saves")
    void addOfflineProfile(@TempDir Path dir) throws IOException {
        ProfileService service = new ProfileService(dir.resolve("launcher_profiles.json"));

        service.addOfflineProfile("NewPlayer");

        List<GameProfile> loaded = service.loadProfiles();
        assertEquals(1, loaded.size());
        assertEquals("NewPlayer", loaded.get(0).name());
        assertFalse(loaded.get(0).isOnline());
    }

    @Test
    @DisplayName("findByName returns the profile if it exists")
    void findByName(@TempDir Path dir) throws IOException {
        ProfileService service = new ProfileService(dir.resolve("launcher_profiles.json"));
        service.addOfflineProfile("Finder");

        assertTrue(service.findByName("Finder").isPresent());
        assertTrue(service.findByName("NonExistent").isEmpty());
    }
}
