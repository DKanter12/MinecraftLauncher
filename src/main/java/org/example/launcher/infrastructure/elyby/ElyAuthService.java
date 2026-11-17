package org.example.launcher.infrastructure.elyby;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.infrastructure.http.HttpDefaults;
import org.example.launcher.infrastructure.common.JsonStrings;

/**
 * Обрабатывает авторизацию и получение профиля через сервер авторизации Ely.by.
 * <p>
 * Поток:
 * <ol>
 *   <li>POST на {@code https://authserver.ely.by/auth/authenticate} с
 *       username + password → возвращает accessToken, clientToken, selectedProfile</li>
 *   <li>GET {@code https://authserver.ely.by/session/profile/{uuid}}
 *       → возвращает профиль со свойством textures (скин URL + модель в base64)</li>
 * </ol>
 */
public class ElyAuthService {

    private static final Logger LOG = Logger.getLogger(ElyAuthService.class.getName());

    private static final String AUTH_URL =
            "https://authserver.ely.by/auth/authenticate";
    private static final String REFRESH_URL =
            "https://authserver.ely.by/auth/refresh";
    private static final String SESSION_URL =
            "https://authserver.ely.by/session/profile/";
    private static final String TEXTURES_PROPERTY = "textures";
    private static final String SKIN_KEY = "SKIN";

    private final Gson gson;
    private final HttpClient httpClient;

    public ElyAuthService() {
        this(HttpDefaults.newGson(), HttpDefaults.newClient());
    }

    public ElyAuthService(Gson gson, HttpClient httpClient) {
        this.gson = Objects.requireNonNull(gson, "gson");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    /**
     * Авторизуется в Ely.by и возвращает полностью заполненный GameProfile,
     * включая данные скина.
     *
     * @param username имя пользователя или email Ely.by
     * @param password пароль учётной записи
     * @return авторизованный GameProfile с accessToken, uuid, скином
     * @throws Exception если авторизация не удалась
     */
    public GameProfile authenticate(String username, String password) throws Exception {
        String clientToken = UUID.randomUUID().toString().replace("-", "");

        JsonObject requestBody = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        requestBody.add("agent", agent);
        requestBody.addProperty("username", username);
        requestBody.addProperty("password", password);
        requestBody.addProperty("clientToken", clientToken);

        HttpRequest request = HttpRequest.newBuilder(URI.create(AUTH_URL))
                .timeout(HttpDefaults.REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = parseError(response.body());
            throw new RuntimeException("Ely.by auth failed: " + errorMsg);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        String accessToken = getStr(root, "accessToken");
        String returnedClientToken = getStr(root, "clientToken");

        JsonObject selectedProfile = null;
        if (root.has("selectedProfile") && root.get("selectedProfile").isJsonObject()) {
            selectedProfile = root.getAsJsonObject("selectedProfile");
        }

        if (selectedProfile == null) {
            throw new RuntimeException("Ely.by returned no selected profile");
        }

        String playerName = getStr(selectedProfile, "name");
        String uuid = getStr(selectedProfile, "id");

        SkinData skin = fetchSkinData(uuid);

        return GameProfile.elyBy(playerName, uuid, accessToken,
                returnedClientToken != null ? returnedClientToken : clientToken,
                skin.url, skin.model, skin.propertiesJson);
    }

    /**
     * Обновляет авторизованную сессию существующего аккаунта Ely.by:
     * <ol>
     *   <li>{@code POST /auth/refresh} с сохранёнными accessToken +
     *       clientToken → новый accessToken + текущее имя профиля</li>
     *   <li>{@code GET /session/profile/{uuid}} → актуальные данные скина</li>
     * </ol>
     * Идентификатором аккаунта является UUID; ник считается отображаемым
     * полем, которое может меняться в любой момент. Пока лаунчер регулярно
     * обновляется, сохранённый accessToken остаётся активным неограниченно долго.
     *
     * @param profile существующий профиль Ely.by
     * @return обновлённый GameProfile либо исходный, если обновление не удалось
     */
    public GameProfile refreshProfile(GameProfile profile) {
        if (profile == null || !profile.isElyBy() || profile.uuid().isEmpty()) {
            return profile;
        }
        try {
            String uuid = profile.uuid().get();

            String accessToken = profile.accessToken().orElse(null);
            String clientToken = profile.clientToken().orElse(null);

            if (accessToken != null && clientToken != null) {
                try {
                    JsonObject requestBody = new JsonObject();
                    requestBody.addProperty("accessToken", accessToken);
                    requestBody.addProperty("clientToken", clientToken);

                    HttpRequest request = HttpRequest.newBuilder(URI.create(REFRESH_URL))
                            .timeout(HttpDefaults.CONNECT_TIMEOUT)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                            .build();

                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        JsonObject root = JsonParser.parseString(response.body())
                                .getAsJsonObject();
                        accessToken = getStr(root, "accessToken");
                        String returnedClientToken = getStr(root, "clientToken");
                        if (returnedClientToken != null) {
                            clientToken = returnedClientToken;
                        }
                        if (root.has("selectedProfile")
                                && root.get("selectedProfile").isJsonObject()) {
                            JsonObject selected = root.getAsJsonObject("selectedProfile");
                            String authName = getStr(selected, "name");
                            if (authName != null && !authName.isBlank()) {
                                LOG.fine("Ely.by auth refresh: current name='" + authName + "'");
                            }
                        }
                    } else {
                        LOG.info("Ely.by auth refresh failed: " + response.statusCode());
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Ely.by auth refresh error", e);
                }
            }

            String trimmedUuid = uuid.replace("-", "");

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(SESSION_URL + trimmedUuid))
                    .timeout(HttpDefaults.CONNECT_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.fine("Ely.by session returned status " + response.statusCode());
                return profile;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

            String currentName = getStr(root, "name");
            LOG.fine("Ely.by session name='" + currentName + "' | saved name='" + profile.name() + "'");
            if (currentName == null || currentName.isBlank()) {
                currentName = profile.name();
            }

            String propertiesJson = null;
            String skinUrl = null;
            String skinModel = null;

            if (root.has("properties") && root.get("properties").isJsonArray()) {
                propertiesJson = gson.toJson(root.get("properties"));
                for (JsonElement elem : root.getAsJsonArray("properties")) {
                    if (!elem.isJsonObject()) {
                        continue;
                    }
                    JsonObject prop = elem.getAsJsonObject();
                    if (TEXTURES_PROPERTY.equals(getStr(prop, "name"))) {
                        String encodedValue = getStr(prop, "value");
                        if (encodedValue != null) {
                            SkinData decoded = decodeTextures(encodedValue);
                            skinUrl = decoded.url;
                            skinModel = decoded.model;
                        }
                    }
                }
            }

            return GameProfile.elyBy(currentName, uuid,
                    accessToken != null ? accessToken : profile.accessToken().orElse(null),
                    clientToken != null ? clientToken : profile.clientToken().orElse(null),
                    skinUrl, skinModel, propertiesJson);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Ely.by refresh failed, keeping cached profile", e);
            return profile;
        }
    }

    /**
     * Загружает URL и модель скина с сессионного сервера Ely.by.
     */
    SkinData fetchSkinData(String uuid) throws Exception {
        String trimmedUuid = uuid.replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(SESSION_URL + trimmedUuid))
                .timeout(HttpDefaults.CONNECT_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return new SkinData(null, null, null);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

        String propertiesJson = null;
        if (root.has("properties") && root.get("properties").isJsonArray()) {
            propertiesJson = gson.toJson(root.get("properties"));
        }

        if (!root.has("properties") || !root.get("properties").isJsonArray()) {
            return new SkinData(null, null, propertiesJson);
        }

        JsonArray properties = root.getAsJsonArray("properties");
        for (JsonElement elem : properties) {
            if (!elem.isJsonObject()) {
                continue;
            }
            JsonObject prop = elem.getAsJsonObject();
            if (TEXTURES_PROPERTY.equals(getStr(prop, "name"))) {
                String encodedValue = getStr(prop, "value");
                if (encodedValue != null) {
                    SkinData decoded = decodeTextures(encodedValue);
                    return new SkinData(decoded.url, decoded.model, propertiesJson);
                }
            }
        }

        return new SkinData(null, null, propertiesJson);
    }

    private SkinData decodeTextures(String base64Value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Value);
            JsonObject textures = JsonParser
                    .parseString(new String(decoded, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            if (!textures.has("textures") || !textures.get("textures").isJsonObject()) {
                return new SkinData(null, null, null);
            }

            JsonObject texObj = textures.getAsJsonObject("textures");

            String skinUrl = null;
            String skinModel = null;

            if (texObj.has(SKIN_KEY) && texObj.get(SKIN_KEY).isJsonObject()) {
                JsonObject skin = texObj.getAsJsonObject(SKIN_KEY);
                skinUrl = getStr(skin, "url");
                if (skin.has("metadata") && skin.get("metadata").isJsonObject()) {
                    JsonObject meta = skin.getAsJsonObject("metadata");
                    skinModel = getStr(meta, "model");
                }
            }

            return new SkinData(skinUrl, skinModel, null);
        } catch (Exception e) {
            return new SkinData(null, null, null);
        }
    }

    private String parseError(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("errorMessage")) {
                return root.get("errorMessage").getAsString();
            }
            if (root.has("error")) {
                return root.get("error").getAsString();
            }
            return body;
        } catch (Exception e) {
            return body;
        }
    }

    private static String getStr(JsonObject obj, String key) {
        return JsonStrings.getStringOrNull(obj, key);
    }

    record SkinData(String url, String model, String propertiesJson) {}
}
