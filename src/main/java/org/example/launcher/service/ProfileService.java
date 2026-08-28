package org.example.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Reads and writes {@link org.example.launcher.model.GameProfile}s
 * to the local {@code launcher_profiles.json} file.
 * <p>
 * JSON parsing is isolated in {@link #parseProfiles(String)} for
 * unit testing without file I/O.
 */
public class ProfileService {

    private final Gson gson;
    private final Path profilesFile;

    public ProfileService(Path profilesFile) {
        this(profilesFile, new Gson());
    }

    public ProfileService(Path profilesFile, Gson gson) {
        this.profilesFile = profilesFile;
        this.gson = gson;
    }

    /**
     * Loads all profiles from the profiles file. Returns an empty
     * list if the file does not exist or is empty.
     */
    public List<org.example.launcher.model.GameProfile> loadProfiles() throws IOException {
        if (!Files.isRegularFile(profilesFile)) {
            return List.of();
        }
        String json = Files.readString(profilesFile);
        return parseProfiles(json);
    }

    /**
     * Saves the given profiles to the profiles file, overwriting any
     * existing content. Parent directories are created as needed.
     */
    public void saveProfiles(List<org.example.launcher.model.GameProfile> profiles) throws IOException {
        Path parent = profilesFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(profilesFile, serializeProfiles(profiles));
    }

    /**
     * Adds an offline profile and saves.
     */
    public org.example.launcher.model.GameProfile addOfflineProfile(String name) throws IOException {
        var profiles = new ArrayList<>(loadProfiles());
        var profile = org.example.launcher.model.GameProfile.offline(name);
        profiles.removeIf(p -> p.name().equals(name));
        profiles.add(profile);
        saveProfiles(profiles);
        return profile;
    }

    /**
     * Finds a profile by name.
     */
    public Optional<org.example.launcher.model.GameProfile> findByName(String name) throws IOException {
        return loadProfiles().stream()
                .filter(p -> p.name().equals(name))
                .findFirst();
    }

    // ------------------------------------------------------------------
    //  Parsing (pure, testable)
    // ------------------------------------------------------------------

    /**
     * Parses a raw {@code launcher_profiles.json} string.
     */
    public List<org.example.launcher.model.GameProfile> parseProfiles(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse profiles JSON", e);
        }
        if (root == null) return List.of();

        List<org.example.launcher.model.GameProfile> result = new ArrayList<>();

        if (root.has("profiles") && root.get("profiles").isJsonObject()) {
            JsonObject profilesObj = root.getAsJsonObject("profiles");
            for (var entry : profilesObj.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                String name = getStr(p, "name", entry.getKey());
                String uuid = getStrOrNull(p, "uuid");
                String token = getStrOrNull(p, "accessToken");
                String type = getStrOrNull(p, "type");
                String skinUrl = getStrOrNull(p, "skinUrl");
                String skinModel = getStrOrNull(p, "skinModel");
                String profileProps = getStrOrNull(p, "profileProperties");

                if ("ely_by".equalsIgnoreCase(type)) {
                    result.add(org.example.launcher.model.GameProfile.elyBy(
                            name, uuid, token, skinUrl, skinModel, profileProps));
                } else {
                    boolean online = "Mojang".equalsIgnoreCase(type)
                            || "microsoft".equalsIgnoreCase(type);
                    result.add(new org.example.launcher.model.GameProfile(
                            name, uuid, token, online));
                }
            }
        }

        return result;
    }

    /**
     * Serialises profiles to JSON.
     */
    public String serializeProfiles(List<org.example.launcher.model.GameProfile> profiles) {
        JsonObject root = new JsonObject();
        JsonObject profilesObj = new JsonObject();

        for (var p : profiles) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", p.name());
            if (p.uuid().isPresent()) entry.addProperty("uuid", p.uuid().get());
            if (p.accessToken().isPresent()) entry.addProperty("accessToken", p.accessToken().get());

            String type;
            if (p.isElyBy()) {
                type = "ely_by";
            } else if (p.isOnline()) {
                type = "Mojang";
            } else {
                type = "offline";
            }
            entry.addProperty("type", type);

            if (p.skinUrl().isPresent()) entry.addProperty("skinUrl", p.skinUrl().get());
            if (p.skinModel().isPresent()) entry.addProperty("skinModel", p.skinModel().get());
            if (p.profileProperties().isPresent())
                entry.addProperty("profileProperties", p.profileProperties().get());

            profilesObj.add(p.name(), entry);
        }

        root.add("profiles", profilesObj);
        root.addProperty("selectedProfile", profiles.isEmpty() ? "" : profiles.get(0).name());
        return gson.toJson(root);
    }

    // ------------------------------------------------------------------
    //  JSON helpers
    // ------------------------------------------------------------------

    private static String getStr(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    private static String getStrOrNull(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive() && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}
