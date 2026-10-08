package org.example.launcher.distribution.api;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * {@link LauncherServerApi} на основе папки Яндекс Диска
 * вместо живого сервера: администратор публикует сборки
 * загрузкой папок, лаунчер читает их через REST
 * API Диска — без серверного процесса и базы данных.
 *
 * <p>Правило распознавания: каждый прямой подкаталог корня, содержащий
 * папку {@code mods} или {@code config} (либо jar-файлы модов),
 * считается сборкой; имя её папки — имя сборки. Папка может также
 * содержать манифест {@code build.json} (той же формы, что локальный),
 * фиксирующий id, версию, описание и координаты Minecraft/загрузчика,
 * — без него они синтезируются (версия из времени изменения папки,
 * поэтому изменённые папки читаются как обновления).</p>
 * <p>Два режима доступа:</p>
 * <ul>
 *   <li><b>Приватный диск</b> — OAuth-токен плюс папка сборок
 *       (напр. {@code /Builds}).</li>
 *   <li><b>Публичная папка</b> — только ссылка на общую папку
 *       (напр. {@code https://disk.yandex.ru/d/...}); пути
 *       относительны общего корня, токен не нужен.</li>
 * </ul>
 *
 * <p>Аутентификация — OAuth-токен Яндекса, передаётся как пароль
 * при входе и хранится как обычный токен сессии (а не как пароль
 * в открытом виде). Трафик идёт через настроенный VPN/прокси.</p>
 */
public class YandexDiskBuildApi implements LauncherServerApi {

    /** Корень REST API Диска; в тестах подменяется локальной заглушкой. */
    public static final String DEFAULT_API_BASE =
            "https://cloud-api.yandex.net";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int PAGE_LIMIT = 1000;
    private static final int MAX_FILES_PER_BUILD = 10000;

    private final String oauthToken;
    private final String rootFolder;
    private final String publicKey;
    private final String apiBase;
    private final UrlFetcher fetcher;
    private final Gson gson;

    /**
     * @param oauthToken  OAuth-токен Яндекса (может быть пустым для публичных
     *                    папок, доступных без авторизации)
     * @param rootFolder  папка сборок на диске, напр. {@code /Builds}
     * @param fetcher     фетчер с учётом прокси (включая VPN-маршрутизацию)
     */
    public YandexDiskBuildApi(String oauthToken, String rootFolder,
                              UrlFetcher fetcher) {
        this(oauthToken, normalizeFolder(rootFolder), null, fetcher,
                DEFAULT_API_BASE, new Gson());
    }

    /**
     * Публичная общая папка: нужна только ссылка на неё.
     *
     * @param publicKey общая ссылка, напр.
     *                  {@code https://disk.yandex.ru/d/TiaArgV_VfAmQA}
     * @param fetcher   фетчер с учётом прокси (включая VPN-маршрутизацию)
     */
    public static YandexDiskBuildApi publicFolder(String publicKey,
                                                  UrlFetcher fetcher) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("publicKey must not be blank");
        }
        return new YandexDiskBuildApi("", "/", publicKey.trim(), fetcher,
                DEFAULT_API_BASE, new Gson());
    }

    YandexDiskBuildApi(String oauthToken, String rootFolder, UrlFetcher fetcher,
                       String apiBase, Gson gson) {
        this(oauthToken, normalizeFolder(rootFolder), null, fetcher, apiBase,
                gson);
    }

    YandexDiskBuildApi(String oauthToken, String rootFolder, String publicKey,
                       UrlFetcher fetcher, String apiBase, Gson gson) {
        if (rootFolder == null || rootFolder.isBlank()) {
            throw new IllegalArgumentException("rootFolder must not be blank");
        }
        this.oauthToken = oauthToken != null ? oauthToken : "";
        this.rootFolder = rootFolder;
        this.publicKey = publicKey;
        this.apiBase = apiBase.endsWith("/") ? apiBase : apiBase + "/";
        this.fetcher = fetcher;
        this.gson = gson;
    }

    private static String normalizeFolder(String rootFolder) {
        String root = rootFolder == null || rootFolder.isBlank() ? "/"
                : rootFolder;
        if (!root.startsWith("/")) {
            root = "/" + root;
        }
        while (root.endsWith("/") && root.length() > 1) {
            root = root.substring(0, root.length() - 1);
        }
        return root;
    }

    /** @return true, если читается публичная общая папка. */
    private boolean isPublic() {
        return publicKey != null;
    }

    /** Логический путь сборки внутри видимого корня. */
    private String buildPath(String buildId) {
        return isPublic() ? "/" + buildId : rootFolder + "/" + buildId;
    }

    @Override
    public ServerSession login(String login, String password) throws IOException {
        if (login == null || login.isBlank()) {
            throw new IOException("Login must not be blank");
        }
        String token = password != null ? password : "";
        // Проверить токен (и папку) сразу: неверный
        // токен упадёт здесь, а не в середине установки.
        YandexDiskBuildApi probe = isPublic()
                ? new YandexDiskBuildApi(token, "/", publicKey, fetcher,
                        apiBase, gson)
                : new YandexDiskBuildApi(token, rootFolder, fetcher, apiBase,
                        gson);
        probe.listRoot(null);
        return new ServerSession(login, UserRole.USER, token,
                isPublic() ? publicKey : "yandex:" + rootFolder, null);
    }

    @Override
    public List<BuildSummary> listBuilds(ServerSession session) throws IOException {
        List<DiskItem> folders = listRoot(session);
        List<BuildSummary> builds = new ArrayList<>();
        for (DiskItem folder : folders) {
            if (folder == null || !folder.dir() || folder.name() == null) {
                continue;
            }
            // Дочерние пути пересобираются из имён: форматы путей API
            // различаются для приватных и публичных эндпоинтов.
            String folderLogical = (isPublic() ? "" : rootFolder) + "/"
                    + folder.name();
            try {
                BuildSummary summary = summarize(session, folderLogical,
                        folder);
                if (summary != null) {
                    builds.add(summary);
                }
            } catch (IllegalArgumentException ignored) {
                // одна нечитаемая папка не должна скрывать каталог
            }
        }
        return builds;
    }

    @Override
    public BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException {
        String folderLogical = buildPath(buildId);
        String folderModified = folderModified(session, folderLogical);
        List<DiskFile> files = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(folderLogical);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (DiskItem item : listPath(session, current)) {
                if (item == null || item.name() == null) {
                    continue;
                }
                String child = current.equals("/") ? "/" + item.name()
                        : current + "/" + item.name();
                if (item.dir()) {
                    queue.add(child);
                } else if (files.size() < MAX_FILES_PER_BUILD) {
                    DiskFile file = toBuildFile(folderLogical, child, item);
                    if (file != null) {
                        files.add(file);
                    }
                }
            }
        }
        BuildSummary summary = readManifest(session, folderLogical,
                buildId, modifiedVersion(folderModified));
        List<BuildFileEntry> entries = new ArrayList<>();
        for (DiskFile file : files) {
            entries.add(new BuildFileEntry(file.relativePath, file.category,
                    null, file.sha256, file.md5, file.size));
        }
        return new BuildDescriptor(summary, entries, BuildOrigin.SERVER);
    }

    @Override
    public void downloadFile(ServerSession session, BuildDescriptor build,
                             BuildFileEntry file, Path target) throws IOException {
        String diskPath = buildPath(build.id()) + "/"
                + categoryRoot(file) + file.relativePath();
        String href = downloadUrl(diskPath, headers(session));
        fetcher.download(href, target, TIMEOUT, headers(session),
                (downloaded, total) -> {
                });
    }

    // ------------------------------------------------------------------
    //  Описания и распознавание
    // ------------------------------------------------------------------

    private BuildSummary summarize(ServerSession session,
                                      String folderLogical, DiskItem folder)
            throws IOException {
        List<DiskItem> children = listPath(session, folderLogical);
        boolean hasMods = false;
        boolean hasConfigs = false;
        for (DiskItem child : children) {
            String name = child.name().toLowerCase();
            if (child.dir() && name.equals("mods")) {
                hasMods = true;
            } else if (child.dir()
                    && (name.equals("config") || name.equals("configs"))) {
                hasConfigs = true;
            } else if (!child.dir() && name.endsWith(".jar")) {
                hasMods = true;
            }
        }
        if (!hasMods && !hasConfigs) {
            return null; // не папка сборки
        }
        return readManifest(session, folder.path(), folder.name(),
                modifiedVersion(folder.modified()));
    }

    /**
     * {@code build.json} в папке имеет приоритет, если есть; иначе
     * описание синтезируется из имени папки и времени.
     */
    private BuildSummary readManifest(ServerSession session, String folderPath,
                                      String folderName, String fallbackVersion)
            throws IOException {
        String manifestPath = folderPath + "/build.json";
        if (resourceExists(manifestPath, headers(session))) {
            try {
                String href = downloadUrl(manifestPath, headers(session));
                String json = fetcher.fetchText(href, TIMEOUT,
                        headers(session));
                JsonObject root = gson.fromJson(json, JsonObject.class);
                JsonObject summary = root != null
                        && root.has("summary")
                        && root.get("summary").isJsonObject()
                        ? root.getAsJsonObject("summary") : root;
                if (summary != null) {
                    return parseManifestSummary(summary, folderName,
                            fallbackVersion);
                }
            } catch (RuntimeException e) {
                // откат к синтезированному описанию
            }
        }
        String minecraft = parseMinecraftVersion(folderName);
        if (minecraft == null) {
            minecraft = "unknown";
        }
        return new BuildSummary(folderName, fallbackVersion, folderName,
                "Yandex Disk build", ModLoaderType.VANILLA, minecraft, "");
    }

    private BuildSummary parseManifestSummary(JsonObject summary,
                                              String folderName,
                                              String fallbackVersion) {
        String id = optional(summary, "id");
        if (id == null) {
            id = folderName;
        }
        String version = optional(summary, "version");
        if (version == null) {
            version = fallbackVersion;
        }
        String displayName = optional(summary, "displayName");
        if (displayName == null) {
            displayName = folderName;
        }
        ModLoaderType loaderType = ModLoaderType.VANILLA;
        String loaderRaw = optional(summary, "loaderType");
        if (loaderRaw != null) {
            try {
                loaderType = ModLoaderType.valueOf(loaderRaw);
            } catch (IllegalArgumentException ignored) {
                // оставить значение VANILLA по умолчанию
            }
        }
        String minecraft = optional(summary, "minecraftVersion");
        if (minecraft == null) {
            minecraft = parseMinecraftVersion(folderName);
        }
        if (minecraft == null) {
            minecraft = "unknown";
        }
        return new BuildSummary(id, version, displayName,
                optional(summary, "description"), loaderType, minecraft,
                optional(summary, "loaderVersion"));
    }

    /** Версия MC из имени папки по лучшему усилию, напр. «Pack 1.20.1». */
    static String parseMinecraftVersion(String folderName) {
        if (folderName == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
                .matcher(folderName);
        String found = null;
        while (matcher.find()) {
            found = matcher.group();
        }
        return found;
    }

    /**
     * Сравнимая версия из времени изменения на Диске
     * ({@code 2026-09-21T10:00:00+00:00} →
     * {@code 2026.09.21.10.00.00}), чтобы изменённые папки читались как
     * обновления.
     */
    static String modifiedVersion(String modified) {
        if (modified == null) {
            return "0.0.0";
        }
        String digits = modified.replaceAll("\\D", "");
        if (digits.length() < 14) {
            return "0.0.0";
        }
        digits = digits.substring(0, 14);
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + "."
                + digits.substring(6, 8) + "." + digits.substring(8, 10)
                + "." + digits.substring(10, 12) + "."
                + digits.substring(12, 14);
    }

    private DiskFile toBuildFile(String folderLogical, String childLogical,
                                   DiskItem item) {
        String prefix = folderLogical.endsWith("/") ? folderLogical
                : folderLogical + "/";
        if (!childLogical.startsWith(prefix)) {
            return null;
        }
        String relative = childLogical.substring(prefix.length())
                .replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank() || relative.contains("..")) {
            return null;
        }
        if (relative.equalsIgnoreCase("build.json")
                && !relative.contains("/")) {
            return null; // сам манифест, а не игровой файл
        }
        int slash = relative.indexOf('/');
        String top = (slash < 0 ? relative : relative.substring(0, slash))
                .toLowerCase();
        String rest = slash < 0 ? relative : relative.substring(slash + 1);
        BuildFileCategory category;
        if (top.equals("mods")) {
            category = BuildFileCategory.MODS;
        } else if (top.equals("config") || top.equals("configs")) {
            category = BuildFileCategory.CONFIGS;
        } else {
            category = BuildFileCategory.RESOURCES;
            rest = relative;
        }
        if (item.md5() == null && item.sha256() == null) {
            return null; // нечем проверять целостность
        }
        return new DiskFile(rest, category, item.sha256(), item.md5(),
                item.size());
    }

    private String categoryRoot(BuildFileEntry file) {
        // записи RESOURCES уже содержат полный относительный путь
        return file.category() == BuildFileCategory.RESOURCES ? ""
                : file.category().folder() + "/";
    }

    // ------------------------------------------------------------------
    //  REST API Диска
    // ------------------------------------------------------------------

    private record DiskItem(String name, String path, boolean dir,
                            String modified, String md5, String sha256,
                            long size) {
    }

    private record DiskFile(String relativePath, BuildFileCategory category,
                            String sha256, String md5, long size) {
    }

    private Map<String, String> headers(ServerSession session) {
        String token = session != null && session.token() != null
                && !session.token().isBlank() ? session.token() : oauthToken;
        Map<String, String> headers = new HashMap<>();
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", "OAuth " + token);
        }
        return headers;
    }

    private List<DiskItem> listRoot(ServerSession session) throws IOException {
        return listPath(session, isPublic() ? "/" : rootFolder);
    }

    private String resourcesEndpoint(String subPath, String fields) {
        String base = isPublic()
                ? apiBase + "v1/disk/public/resources?public_key="
                        + encode(publicKey) + "&path=" + encode(subPath)
                : apiBase + "v1/disk/resources?path=" + encode(subPath);
        return fields == null ? base : base + "&fields=" + fields;
    }

    private String downloadEndpoint(String subPath) {
        if (isPublic()) {
            return apiBase + "v1/disk/public/resources/download?public_key="
                    + encode(publicKey) + "&path=" + encode(subPath);
        }
        return apiBase + "v1/disk/resources/download?path="
                + encode(subPath);
    }

    private static final String LIST_FIELDS = "_embedded.total,"
            + "_embedded.offset,_embedded.items.name,_embedded.items.type,"
            + "_embedded.items.path,_embedded.items.modified,"
            + "_embedded.items.md5,_embedded.items.sha256,_embedded.items.size";

    private List<DiskItem> listPath(ServerSession session, String path) throws IOException {
        List<DiskItem> items = new ArrayList<>();
        int offset = 0;
        while (true) {
            String url = resourcesEndpoint(path, null)
                    + "&limit=" + PAGE_LIMIT + "&offset=" + offset
                    + "&fields=" + LIST_FIELDS;
            String json = fetcher.fetchText(url, TIMEOUT,
                    headers(session));
            JsonObject root = parseObject(json, "listing of " + path);
            JsonObject embedded = root.has("_embedded")
                    && root.get("_embedded").isJsonObject()
                    ? root.getAsJsonObject("_embedded") : new JsonObject();
            int total = embedded.has("total")
                    && embedded.get("total").isJsonPrimitive()
                    ? embedded.get("total").getAsInt() : 0;
            JsonArray array = embedded.has("items")
                    && embedded.get("items").isJsonArray()
                    ? embedded.getAsJsonArray("items") : new JsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    items.add(parseItem(element.getAsJsonObject()));
                }
            }
            offset += array.size();
            if (array.size() == 0 || offset >= total) {
                break;
            }
        }
        return items;
    }

    private String folderModified(ServerSession session, String folderPath) {
        try {
            String url = resourcesEndpoint(folderPath, "modified");
            JsonObject root = parseObject(
                    fetcher.fetchText(url, TIMEOUT, headers(session)),
                    "folder " + folderPath);
            return optional(root, "modified");
        } catch (IOException e) {
            return null;
        }
    }

    private boolean resourceExists(String path, Map<String, String> headers) {
        try {
            String url = resourcesEndpoint(path, "name");
            fetcher.fetchText(url, TIMEOUT, headers);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String downloadUrl(String path, Map<String, String> headers)
            throws IOException {
        String url = downloadEndpoint(path);
        String json = fetcher.fetchText(url, TIMEOUT, headers);
        JsonObject root = parseObject(json, "download link for " + path);
        if (!root.has("href") || !root.get("href").isJsonPrimitive()) {
            throw new IOException("No download link for " + path);
        }
        return root.get("href").getAsString();
    }

    private DiskItem parseItem(JsonObject obj) {
        String type = optional(obj, "type");
        String path = optional(obj, "path");
        if (path != null && path.startsWith("disk:")) {
            path = path.substring("disk:".length());
        }
        return new DiskItem(
                optional(obj, "name"),
                path,
                "dir".equalsIgnoreCase(type),
                optional(obj, "modified"),
                optional(obj, "md5"),
                optional(obj, "sha256"),
                obj.has("size") && obj.get("size").isJsonPrimitive()
                        ? obj.get("size").getAsLong() : 0L);
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

    private static String optional(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String folderName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String encode(String path) {
        return URLEncoder.encode(path, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
