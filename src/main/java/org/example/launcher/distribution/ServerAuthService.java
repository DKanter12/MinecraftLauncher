package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.distribution.api.LauncherServerApi;

/**
 * Sign-in state for the launcher server: the session token is
 * persisted, the password never is.
 *
 * <p>Sign-in happens once with login and password over the
 * {@link LauncherServerApi}; the server answers with a bearer token
 * (JWT-like) and the account's role. From that moment the launcher
 * stores only the token and uses it for every subsequent request, so
 * the password exists in memory just for the duration of one call.</p>
 */
public class ServerAuthService {

    /** File name of the persisted session inside the launcher's storage root. */
    public static final String SESSION_FILE_NAME = "server_session.json";

    private static final String KEY_ACCOUNT = "accountName";
    private static final String KEY_ROLE = "role";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_SERVER_URL = "serverUrl";
    private static final String KEY_EXPIRES_AT = "expiresAt";

    private final LauncherServerApi api;
    private final Gson gson;
    private final Path sessionFile;

    public ServerAuthService(LauncherServerApi api, Path sessionFile) {
        this(api, sessionFile, new Gson());
    }

    public ServerAuthService(LauncherServerApi api, Path sessionFile, Gson gson) {
        this.api = api;
        this.sessionFile = sessionFile;
        this.gson = gson;
    }

    /**
     * Signs in and persists the resulting session (token only).
     *
     * @param login    account login
     * @param password account password — used for this call only, never stored
     * @return the authorized session
     * @throws IOException when the server is unreachable or rejects the credentials
     */
    public ServerSession login(String login, String password) throws IOException {
        ServerSession session = api.login(login, password);
        persist(session);
        return session;
    }

    /**
     * Restores a previously persisted session after a launcher
     * restart, without asking for credentials again.
     *
     * @return the stored session; empty when none exists or the token
     *         has expired (an expired token file is removed)
     */
    public Optional<ServerSession> restoreSession() {
        if (!Files.isRegularFile(sessionFile)) {
            return Optional.empty();
        }
        try {
            JsonObject root = gson.fromJson(Files.readString(sessionFile), JsonObject.class);
            if (root == null) {
                return Optional.empty();
            }
            ServerSession session = fromJson(root);
            if (session == null || session.isExpired()) {
                deleteSessionFile();
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Signs out: the stored token is discarded. Nothing else changes —
     * local builds and instances remain exactly as they are.
     *
     * @throws IOException when the session file cannot be removed
     */
    public void logout() throws IOException {
        deleteSessionFile();
    }

    private void persist(ServerSession session) throws IOException {
        Path parent = sessionFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JsonObject root = new JsonObject();
        root.addProperty(KEY_ACCOUNT, session.accountName());
        root.addProperty(KEY_ROLE, session.role().name());
        root.addProperty(KEY_TOKEN, session.token());
        root.addProperty(KEY_SERVER_URL, session.serverUrl());
        if (session.expiresAtRaw() != null) {
            root.addProperty(KEY_EXPIRES_AT, session.expiresAtRaw());
        }
        Files.writeString(sessionFile, gson.toJson(root));
    }

    private ServerSession fromJson(JsonObject root) {
        String account = stringOrNull(root, KEY_ACCOUNT);
        String roleRaw = stringOrNull(root, KEY_ROLE);
        String token = stringOrNull(root, KEY_TOKEN);
        String serverUrl = stringOrNull(root, KEY_SERVER_URL);
        if (account == null || token == null || serverUrl == null) {
            return null;
        }
        UserRole role;
        try {
            role = UserRole.valueOf(roleRaw == null ? "" : roleRaw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String expiresAt = stringOrNull(root, KEY_EXPIRES_AT);
        return new ServerSession(account, role, token, serverUrl,
                expiresAt == null || expiresAt.isBlank() ? null : expiresAt);
    }

    private String stringOrNull(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        String value = root.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private void deleteSessionFile() throws IOException {
        Files.deleteIfExists(sessionFile);
    }
}
