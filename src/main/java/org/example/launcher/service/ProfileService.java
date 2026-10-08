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

import org.example.launcher.util.Json;

/**
 * Читает и пишет {@link org.example.launcher.domain.model.GameProfile}s
 * в локальный файл {@code launcher_profiles.json}.
 * <p>
 * Разбор JSON выделен в {@link #parseProfiles(String)} для
 * юнит-тестирования без файлового ввода-вывода.
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
     * Загружает все профили из файла профилей. Возвращает пустой
     * список, если файл отсутствует или пуст.
     */
    public List<org.example.launcher.domain.model.GameProfile> loadProfiles() throws IOException {
        if (!Files.isRegularFile(profilesFile)) {
            return List.of();
        }
        String json = Files.readString(profilesFile);
        return parseProfiles(json);
    }

    /**
     * Сохраняет заданные профили в файл профилей, перезаписывая всё
     * существующее содержимое. Родительские каталоги создаются при необходимости.
     */
    public void saveProfiles(List<org.example.launcher.domain.model.GameProfile> profiles) throws IOException {
        Path parent = profilesFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(profilesFile, serializeProfiles(profiles));
    }

    /**
     * Добавляет офлайн-профиль и сохраняет.
     */
    public org.example.launcher.domain.model.GameProfile addOfflineProfile(String name) throws IOException {
        var profiles = new ArrayList<>(loadProfiles());
        var profile = org.example.launcher.domain.model.GameProfile.offline(name);
        profiles.removeIf(p -> p.name().equals(name));
        profiles.add(profile);
        saveProfiles(profiles);
        return profile;
    }

    /**
     * Ищет профиль по имени.
     */
    public Optional<org.example.launcher.domain.model.GameProfile> findByName(String name) throws IOException {
        return loadProfiles().stream()
                .filter(p -> p.name().equals(name))
                .findFirst();
    }

    /**
     * Удаляет профиль с заданным именем и сохраняет.
     *
     * @return true, если профиль был удалён
     */
    public boolean deleteProfile(String name) throws IOException {
        var profiles = new ArrayList<>(loadProfiles());
        boolean removed = profiles.removeIf(p -> p.name().equals(name));
        if (removed) {
            saveProfiles(profiles);
        }
        return removed;
    }

    // ------------------------------------------------------------------
    //  Разбор (чистый, тестируемый)
    // ------------------------------------------------------------------

    /**
     * Разбирает сырую строку {@code launcher_profiles.json}.
     */
    public List<org.example.launcher.domain.model.GameProfile> parseProfiles(String json) throws IOException {
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

        List<org.example.launcher.domain.model.GameProfile> result = new ArrayList<>();

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
                    result.add(org.example.launcher.domain.model.GameProfile.elyBy(
                            name, uuid, token, getStrOrNull(p, "clientToken"),
                            skinUrl, skinModel, profileProps));
                } else {
                    boolean online = "Mojang".equalsIgnoreCase(type)
                            || "microsoft".equalsIgnoreCase(type);
                    result.add(new org.example.launcher.domain.model.GameProfile(
                            name, uuid, token, online));
                }
            }
        }

        return result;
    }

    /**
     * Сериализует профили в JSON.
     */
    public String serializeProfiles(List<org.example.launcher.domain.model.GameProfile> profiles) {
        JsonObject root = new JsonObject();
        JsonObject profilesObj = new JsonObject();

        for (var p : profiles) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", p.name());
            if (p.uuid().isPresent()) entry.addProperty("uuid", p.uuid().get());
            if (p.accessToken().isPresent()) entry.addProperty("accessToken", p.accessToken().get());
            if (p.clientToken().isPresent()) entry.addProperty("clientToken", p.clientToken().get());

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
    //  JSON-помощники
    // ------------------------------------------------------------------

    private static String getStr(JsonObject obj, String key, String fallback) {
        return Json.getStringOrDefault(obj, key, fallback);
    }

    private static String getStrOrNull(JsonObject obj, String key) {
        return Json.getStringOrNull(obj, key);
    }
}
