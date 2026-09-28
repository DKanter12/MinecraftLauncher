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
 * Состояние входа на сервер лаунчера: токен сессии сохраняется,
 * пароль — никогда.
 *
 * <p>Вход выполняется один раз по логину и паролю через
 * {@link LauncherServerApi}; сервер отвечает bearer-токеном
 * (JWT-подобным) и ролью учётной записи. С этого момента лаунчер
 * хранит только токен и использует его для каждого последующего
 * запроса, поэтому пароль находится в памяти лишь на время одного вызова.</p>
 */
public class ServerAuthService {

    /** Имя файла сохраняемой сессии внутри корня хранилища лаунчера. */
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
     * Выполняет вход и сохраняет полученную сессию (только токен).
     *
     * @param login    логин учётной записи
     * @param password пароль учётной записи — только для этого вызова, не хранится
     * @return авторизованная сессия
     * @throws IOException если сервер недоступен или отклонил учётные данные
     */
    public ServerSession login(String login, String password) throws IOException {
        ServerSession session = api.login(login, password);
        persist(session);
        return session;
    }

    /**
     * Восстанавливает ранее сохранённую сессию после перезапуска лаунчера,
     * без повторного запроса учётных данных.
     *
     * @return сохранённая сессия; пусто, если её нет или токен
     *         истёк (файл истёкшего токена удаляется)
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
     * Выход: сохранённый токен удаляется. Больше ничего не меняется —
     * локальные сборки и инстансы остаются как есть.
     *
     * @throws IOException если файл сессии не удаётся удалить
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
        if (account == null || serverUrl == null) {
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
