package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildFileCategory;
import org.example.launcher.distribution.BuildFileEntry;
import org.example.launcher.distribution.BuildOrigin;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.UserRole;
import org.example.launcher.net.UrlFetcher;
import org.example.launcher.domain.model.ModLoaderType;

/**
 * {@link LauncherServerApi} на основе обычного git-репозитория
 * вместо живого сервера: администратор публикует сборки отправкой
 * файлов, лаунчер читает их по HTTPS (или через локальное
 * зеркало) — без серверного процесса и базы данных.
 * <p>
 * Раскладка репозитория (относительно базового URL, напр.
 * {@code https://raw.githubusercontent.com/OWNER/REPO/BRANCH/}):
 * <pre>
 * builds/catalog.json            — {"builds": [{id, version,
 *                                   displayName, description,
 *                                   loaderType, minecraftVersion,
 *                                   loaderVersion}, ...]}
 * builds/{id}.json               — {"summary": {...}, "files":
 *                                   [{relativePath, category, sha1,
 *                                   size}, ...]}
 * builds/files/{id}/{version}/{category-folder}/{relativePath}
 *                                — содержимое файлов
 * </pre>
 * Для приватных репозиториев передайте персональный токен доступа GitHub как
 * пароль — он путешествует как bearer-токен и хранится как
 * обычный токен сессии (а не как пароль в открытом виде).
 */
public class GitHubBuildApi implements LauncherServerApi {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String baseUrl;
    private final UrlFetcher fetcher;
    private final Gson gson;

    /**
     * @param baseUrl сырой базовый URL репозитория с завершающим слэшем, либо
     *                {@code file:} URL для тестов и локальных зеркал
     * @param fetcher фетчер с учётом прокси (включая VPN-маршрутизацию)
     */
    public GitHubBuildApi(String baseUrl, UrlFetcher fetcher) {
        this(baseUrl, fetcher, new Gson());
    }

    public GitHubBuildApi(String baseUrl, UrlFetcher fetcher, Gson gson) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.fetcher = fetcher;
        this.gson = gson;
    }

    @Override
    public ServerSession login(String login, String password) throws IOException {
        if (login == null || login.isBlank()) {
            throw new IOException("Login must not be blank");
        }
        // Проверить учётные данные (и репозиторий) сразу:
        // приватные репозитории отвергнут плохой токен здесь, а не в середине установки.
        listBuilds(new ServerSession(login, UserRole.USER,
                password != null ? password : "", baseUrl, null));
        return new ServerSession(login, UserRole.USER,
                password != null ? password : "", baseUrl, null);
    }

    @Override
    public List<BuildSummary> listBuilds(ServerSession session) throws IOException {
        String json = fetcher.fetchText(baseUrl + "builds/catalog.json",
                TIMEOUT, headers(session));
        JsonObject root = parseObject(json, "catalog");
        List<BuildSummary> builds = new ArrayList<>();
        JsonArray array = root.has("builds") && root.get("builds").isJsonArray()
                ? root.getAsJsonArray("builds") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            try {
                builds.add(parseSummary(element.getAsJsonObject()));
            } catch (IllegalArgumentException ignored) {
                // одна повреждённая запись не должна скрывать весь каталог
            }
        }
        return builds;
    }

    @Override
    public BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException {
        String json = fetcher.fetchText(baseUrl + "builds/" + buildId + ".json",
                TIMEOUT, headers(session));
        JsonObject root = parseObject(json, "build " + buildId);
        if (!root.has("summary") || !root.get("summary").isJsonObject()) {
            throw new IOException("Build " + buildId + " has no summary");
        }
        BuildSummary summary = parseSummary(root.getAsJsonObject("summary"));
        List<BuildFileEntry> files = new ArrayList<>();
        if (root.has("files") && root.get("files").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("files")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                files.add(parseFile(element.getAsJsonObject()));
            }
        }
        return new BuildDescriptor(summary, files, BuildOrigin.SERVER);
    }

    @Override
    public void downloadFile(ServerSession session, BuildDescriptor build,
                             BuildFileEntry file, Path target) throws IOException {
        String url = baseUrl + "builds/files/" + build.id() + "/"
                + build.version() + "/" + file.key();
        fetcher.download(url, target, TIMEOUT, headers(session),
                (downloaded, total) -> {
                });
    }

    private Map<String, String> headers(ServerSession session) {
        Map<String, String> headers = new HashMap<>();
        if (session != null && !session.isAnonymous()
                && baseUrl.startsWith("http")) {
            headers.put("Authorization", "Bearer " + session.token());
        }
        return headers;
    }

    private JsonObject parseObject(String json, String what) throws IOException {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) {
                throw new IOException("Empty " + what);
            }
            return root;
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid " + what + " JSON", e);
        }
    }

    private BuildSummary parseSummary(JsonObject obj) {
        ModLoaderType loaderType;
        try {
            loaderType = ModLoaderType.valueOf(required(obj, "loaderType"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown loaderType: " + obj.get("loaderType"));
        }
        return new BuildSummary(
                required(obj, "id"),
                required(obj, "version"),
                required(obj, "displayName"),
                optional(obj, "description"),
                loaderType,
                required(obj, "minecraftVersion"),
                optional(obj, "loaderVersion"));
    }

    private BuildFileEntry parseFile(JsonObject obj) {
        BuildFileCategory category;
        try {
            category = BuildFileCategory.valueOf(required(obj, "category"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown category: " + obj.get("category"));
        }
        long size = obj.has("size") && obj.get("size").isJsonPrimitive()
                ? obj.get("size").getAsLong() : 0L;
        return new BuildFileEntry(
                required(obj, "relativePath"), category,
                optional(obj, "sha1"), optional(obj, "sha256"),
                optional(obj, "md5"), size);
    }

    private static String required(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        String value = obj.get(key).getAsString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private static String optional(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }
}
