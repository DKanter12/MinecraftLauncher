package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.domain.model.AssetIndex;
import org.example.launcher.domain.model.DownloadInfo;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.Library;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.net.HttpDefaults;
import org.example.launcher.util.Json;
import org.example.launcher.util.OsDetector;

/**
 * Реализация {@link VersionMetadataService} на официальном JSON
 * метаданных отдельной версии Mojang.
 * <p>
 * Разбор JSON выделен в {@link #parseMetadata(String, String)}, чтобы его
 * можно было юнит-тестировать без сети. Парсер поддерживает как современный
 * структурированный формат аргументов (1.13+), так и старую строку
 * {@code minecraftArguments} (до 1.13).
 */
public class MojangVersionMetadataService implements VersionMetadataService {

    private final HttpClient httpClient;
    private final Gson gson;

    public MojangVersionMetadataService() {
        this(HttpDefaults.newClient(), HttpDefaults.newGson());
    }

    public MojangVersionMetadataService(HttpClient httpClient, Gson gson) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    @Override
    public VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException {
        String url = version.metadataUrl();
        if (url == null || url.isBlank()) {
            throw new IOException("Version " + version.id() + " has no metadata URL");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HttpDefaults.REQUEST_TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected HTTP status " + response.statusCode()
                        + " fetching metadata for " + version.id());
            }
            String body = response.body();
            // Кэшировать сырой JSON версии для офлайн-запуска уже скачанных версий.
            // Только по возможности: ошибки кэша никогда не должны ломать получение метаданных.
            try {
                Path cached = GameDirectory.defaultDirectory().versionMetadata(version.id());
                if (!Files.isRegularFile(cached)) {
                    Files.createDirectories(cached.getParent());
                    Files.writeString(cached, body, StandardCharsets.UTF_8);
                }
            } catch (Exception cacheFailure) {
                // игнорировать: офлайн-кэш необязателен
            }
            return parseMetadata(body, version.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching metadata for " + version.id(), e);
        }
    }

    /**
     * Разбирает сырую строку JSON метаданных отдельной версии.
     *
     * @param json       сырое тело JSON
     * @param fallbackId id для использования, если в JSON его нет
     * @return полностью заполненные {@link VersionMetadata}
     * @throws IOException если JSON невалиден или отсутствуют обязательные поля
     */
    public VersionMetadata parseMetadata(String json, String fallbackId) throws IOException {
        JsonObject root;
        try {
            root = gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Failed to parse version metadata JSON", e);
        }
        if (root == null) {
            throw new IOException("Version metadata JSON is empty or invalid");
        }

        String id = getStr(root, "id", fallbackId);
        String type = getStrOrNull(root, "type");
        String mainClass = getStrOrNull(root, "mainClass");
        String assets = getStrOrNull(root, "assets");

        AssetIndex assetIndex = parseAssetIndex(root);
        JavaVersion javaVersion = parseJavaVersion(root);
        DownloadInfo clientDownload = parseClientDownload(root);
        List<Library> libraries = parseLibraries(root);
        List<String> gameArgs = new ArrayList<>();
        List<String> jvmArgs = new ArrayList<>();
        String legacyArgs = null;

        if (root.has("arguments") && root.get("arguments").isJsonObject()) {
            JsonObject args = root.getAsJsonObject("arguments");
            gameArgs = parseArgumentList(args, "game");
            jvmArgs = parseArgumentList(args, "jvm");
        }

        if (root.has("minecraftArguments") && !root.get("minecraftArguments").isJsonNull()) {
            legacyArgs = root.get("minecraftArguments").getAsString();
        }

        return new VersionMetadata(id, type, mainClass, assets,
                assetIndex, javaVersion, clientDownload,
                libraries, gameArgs, jvmArgs, legacyArgs);
    }

    // ------------------------------------------------------------------
    //  Внутренние помощники разбора
    // ------------------------------------------------------------------

    private AssetIndex parseAssetIndex(JsonObject root) {
        if (!root.has("assetIndex") || !root.get("assetIndex").isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject("assetIndex");
        return new AssetIndex(
                getStrOrNull(obj, "id"),
                getStrOrNull(obj, "sha1"),
                getLong(obj, "size", 0),
                getLong(obj, "totalSize", 0),
                getStrOrNull(obj, "url"));
    }

    private JavaVersion parseJavaVersion(JsonObject root) {
        if (!root.has("javaVersion") || !root.get("javaVersion").isJsonObject()) {
            return null;
        }
        JsonObject obj = root.getAsJsonObject("javaVersion");
        return new JavaVersion(
                getStrOrNull(obj, "component"),
                getInt(obj, "majorVersion", 0));
    }

    private DownloadInfo parseClientDownload(JsonObject root) {
        if (!root.has("downloads") || !root.get("downloads").isJsonObject()) {
            return null;
        }
        JsonObject downloads = root.getAsJsonObject("downloads");
        if (!downloads.has("client") || !downloads.get("client").isJsonObject()) {
            return null;
        }
        JsonObject client = downloads.getAsJsonObject("client");
        return new DownloadInfo(
                getStrOrNull(client, "url"),
                getStrOrNull(client, "sha1"),
                getLong(client, "size", 0),
                null);
    }

    private List<Library> parseLibraries(JsonObject root) {
        if (!root.has("libraries") || !root.get("libraries").isJsonArray()) {
            return List.of();
        }
        JsonArray libsArray = root.getAsJsonArray("libraries");
        List<Library> result = new ArrayList<>(libsArray.size());

        for (JsonElement elem : libsArray) {
            if (!elem.isJsonObject()) continue;
            JsonObject libObj = elem.getAsJsonObject();

            // Пропустить библиотеки, чьи правила запрещают текущую ОС
            if (!rulesAllow(libObj)) {
                continue;
            }

            String name = getStrOrNull(libObj, "name");
            DownloadInfo artifact = null;
            Map<String, DownloadInfo> classifiers = new HashMap<>();
            Map<String, String> natives = new HashMap<>();

            // Разбор downloads.artifact + downloads.classifiers
            if (libObj.has("downloads") && libObj.get("downloads").isJsonObject()) {
                JsonObject dl = libObj.getAsJsonObject("downloads");

                if (dl.has("artifact") && dl.get("artifact").isJsonObject()) {
                    artifact = parseDownloadInfo(dl.getAsJsonObject("artifact"));
                }

                if (dl.has("classifiers") && dl.get("classifiers").isJsonObject()) {
                    JsonObject clsObj = dl.getAsJsonObject("classifiers");
                    for (var entry : clsObj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            classifiers.put(entry.getKey(),
                                    parseDownloadInfo(entry.getValue().getAsJsonObject()));
                        }
                    }
                }
            }

            // Старые библиотеки без «downloads» — скомбинировать корневой «url» + maven-путь
            if (artifact == null) {
                String libUrl = getStrOrNull(libObj, "url");
                if (libUrl != null && !libUrl.isBlank()) {
                    String libPath = mavenNameToPath(name);
                    if (libPath != null) {
                        String base = libUrl.endsWith("/") ? libUrl : libUrl + "/";
                        String fullUrl = base + libPath;
                        // Библиотеки в стиле Fabric/Quilt несут sha1/size на
                        // верхнем уровне рядом с «url» — подхватить их для
                        // загрузок с проверкой хэша
                        String sha1 = getStrOrNull(libObj, "sha1");
                        long size = getLong(libObj, "size", 0);
                        artifact = new DownloadInfo(fullUrl, sha1, size, libPath);
                    }
                }
            }

            // Разбор natives-карты (ОС -> имя классификатора)
            if (libObj.has("natives") && libObj.get("natives").isJsonObject()) {
                JsonObject natObj = libObj.getAsJsonObject("natives");
                for (var entry : natObj.entrySet()) {
                    if (!entry.getValue().isJsonNull()) {
                        natives.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }

            result.add(new Library(name, artifact, classifiers, natives));
        }

        return result;
    }

    private DownloadInfo parseDownloadInfo(JsonObject obj) {
        return new DownloadInfo(
                getStrOrNull(obj, "url"),
                getStrOrNull(obj, "sha1"),
                getLong(obj, "size", 0),
                getStrOrNull(obj, "path"));
    }

    /**
     * Преобразует Maven-координату (например,
     * {@code "com.mojang:logging:1.1.1"}) в относительный путь к файлу
     * ({@code "com/mojang/logging/1.1.1/logging-1.1.1.jar"}).
     * Используется для старых библиотек с одним корневым полем {@code url}.
     */
    private static String mavenNameToPath(String name) {
        if (name == null || name.isBlank()) return null;
        String[] parts = name.split(":");
        if (parts.length < 3) return name.replace('.', '/') + ".jar";
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /**
     * Разбирает массив аргументов из объекта «arguments». Каждый элемент
     * может быть либо обычной строкой, либо объектом с полем «value»
     * и необязательными «rules». Правила фильтруются по текущей ОС, чтобы
     * ОС-специфичные аргументы (например, JVM-флаги только для Windows)
     * включались только на подходящей платформе.
     */
    private List<String> parseArgumentList(JsonObject args, String key) {
        if (!args.has(key) || !args.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray arr = args.getAsJsonArray(key);
        List<String> result = new ArrayList<>(arr.size());

        for (JsonElement elem : arr) {
            if (elem.isJsonPrimitive()) {
                result.add(elem.getAsString());
            } else if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("value") && rulesAllow(obj)) {
                    JsonElement val = obj.get("value");
                    if (val.isJsonPrimitive()) {
                        result.add(val.getAsString());
                    } else if (val.isJsonArray()) {
                        for (JsonElement v : val.getAsJsonArray()) {
                            if (v.isJsonPrimitive()) {
                                result.add(v.getAsString());
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Вычисляет массив «rules» объекта аргумента для текущей
     * ОС. Если правил нет, аргумент всегда разрешён. Иначе должны пройти
     * все правила.
     * <p>
     * Правило с {@code "action": "allow"} проходит, когда его ОС-условие
     * совпадает (или ОС-условия нет). Правило с
     * {@code "action": "disallow"} проходит, когда его ОС-условие
     * НЕ совпадает.
     * <p>
     * Правила на основе фич (например, {@code is_demo_user}) считаются
     * несовпавшими, т.к. лаунчер эти фичи не выставляет.
     */
    private boolean rulesAllow(JsonObject argObj) {
        if (!argObj.has("rules") || !argObj.get("rules").isJsonArray()) {
            return true;
        }
        JsonArray rules = argObj.getAsJsonArray("rules");
        for (JsonElement ruleElem : rules) {
            if (!ruleElem.isJsonObject()) continue;
            JsonObject rule = ruleElem.getAsJsonObject();
            String action = getStrOrNull(rule, "action");
            if (action == null) continue;

            boolean hasOs = rule.has("os") && rule.get("os").isJsonObject();
            boolean hasFeatures = rule.has("features") && rule.get("features").isJsonObject();

            if (hasFeatures) {
                // Правила на основе фич (is_demo_user, has_custom_resolution и т.д.)
                // к нашему лаунчеру неприменимы — считать несовпавшими.
                if ("allow".equals(action)) {
                    return false;
                }
                continue;
            }

            if (hasOs) {
                JsonObject osObj = rule.getAsJsonObject("os");
                String osName = getStrOrNull(osObj, "name");
                boolean osMatches = osName != null
                        && osName.equalsIgnoreCase(OsDetector.mojangName());
                if ("allow".equals(action) && !osMatches) {
                    return false;
                }
                if ("disallow".equals(action) && osMatches) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  JSON-утилиты
    // ------------------------------------------------------------------

    private static String getStr(JsonObject obj, String key, String fallback) {
        return Json.getStringOrDefault(obj, key, fallback);
    }

    private static String getStrOrNull(JsonObject obj, String key) {
        return Json.getStringOrNull(obj, key);
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsLong();
        }
        return fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsInt();
        }
        return fallback;
    }
}
