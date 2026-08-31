package org.example.launcher.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.model.GameProfile;

/**
 * Handles authentication and profile fetching via the Ely.by auth server.
 * <p>
 * Flow:
 * <ol>
 *   <li>POST to {@code https://authserver.ely.by/auth/authenticate} with
 *       username + password → returns accessToken, clientToken, selectedProfile</li>
 *   <li>GET {@code https://authserver.ely.by/session/profile/{uuid}}
 *       → returns profile with textures property (base64-encoded skin URL + model)</li>
 * </ol>
 */
public class ElyAuthService {

    private static final String AUTH_URL =
            "https://authserver.ely.by/auth/authenticate";
    private static final String SESSION_URL =
            "https://authserver.ely.by/session/profile/";

    private final Gson gson;
    private final HttpClient httpClient;

    public ElyAuthService() {
        this(new Gson(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public ElyAuthService(Gson gson, HttpClient httpClient) {
        this.gson = gson;
        this.httpClient = httpClient;
    }

    /**
     * Authenticates with Ely.by and returns a fully populated GameProfile
     * including skin data.
     *
     * @param username Ely.by username or email
     * @param password account password
     * @return authenticated GameProfile with accessToken, uuid, skin
     * @throws Exception if authentication fails
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
                .timeout(Duration.ofSeconds(30))
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
     * Refreshes the authorized session for an existing Ely.by account:
     * <ol>
     *   <li>{@code POST /auth/refresh} with the stored accessToken +
     *       clientToken → new accessToken + current profile name</li>
     *   <li>{@code GET /session/profile/{uuid}} → current skin data</li>
     * </ol>
     * The account identity is the UUID; the nick is treated as a display
     * field that can change at any time. As long as the launcher refreshes
     * regularly, the stored accessToken stays alive indefinitely.
     *
     * @param profile the existing Ely.by profile
     * @return updated GameProfile, or the original if refresh fails
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

                    HttpRequest request = HttpRequest.newBuilder(
                            URI.create(AUTH_URL.replace("/authenticate", "/refresh")))
                            .timeout(Duration.ofSeconds(15))
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
                                System.out.println("[ELY] auth refresh: current name='" + authName + "'");
                            }
                        }
                    } else {
                        System.out.println("[ELY] auth refresh failed: " + response.statusCode()
                                + " — session may have expired, re-login required");
                    }
                } catch (Exception e) {
                    System.out.println("[ELY] auth refresh error: " + e.getMessage());
                }
            }

            String trimmedUuid = uuid.replace("-", "");

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(SESSION_URL + trimmedUuid))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[ELY] session returned status " + response.statusCode() + " for uuid=" + trimmedUuid);
                return profile;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

            String currentName = getStr(root, "name");
            System.out.println("[ELY] session name='" + currentName + "' | saved name='" + profile.name() + "'");
            if (currentName == null || currentName.isBlank()) {
                currentName = profile.name();
            }

            String propertiesJson = null;
            String skinUrl = null;
            String skinModel = null;

            if (root.has("properties") && root.get("properties").isJsonArray()) {
                propertiesJson = gson.toJson(root.get("properties"));
                for (JsonElement elem : root.getAsJsonArray("properties")) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject prop = elem.getAsJsonObject();
                    if ("textures".equals(getStr(prop, "name"))) {
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
            System.out.println("[ELY] refresh exception: " + e);
            return profile;
        }
    }

    /**
     * Fetches skin URL and model from the Ely.by session server.
     */
    SkinData fetchSkinData(String uuid) throws Exception {
        String trimmedUuid = uuid.replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(SESSION_URL + trimmedUuid))
                .timeout(Duration.ofSeconds(15))
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
            if (!elem.isJsonObject()) continue;
            JsonObject prop = elem.getAsJsonObject();
            if ("textures".equals(getStr(prop, "name"))) {
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

            if (texObj.has("SKIN") && texObj.get("SKIN").isJsonObject()) {
                JsonObject skin = texObj.getAsJsonObject("SKIN");
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
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    record SkinData(String url, String model, String propertiesJson) {}
}
