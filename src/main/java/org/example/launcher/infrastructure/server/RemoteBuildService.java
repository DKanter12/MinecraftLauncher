package org.example.launcher.infrastructure.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.infrastructure.server.api.LauncherServerApi;
import org.example.launcher.domain.model.ModLoaderType;

/**
 * Устанавливает и обновляет сборки, распространяемые администратором через
 * сервер лаунчера, и показывает установленные локально.
 *
 * <p>Серверные сборки лежат в той же папке {@code builds} инстанса,
 * что и локально сохранённые сборки — по одному подкаталогу на id сборки —
 * но дополнительно содержат манифест {@code build.json} (источник
 * SERVER). Установка одной сборки никогда не затрагивает другую: они
 * остаются раздельными и переключаемыми, а переключение ничего
 * повторно не скачивает.</p>
 *
 * <p>Обновления используют схему id сборки + версия: тот же id с более
 * высокой версией переиспользует существующую папку и синхронизирует её
 * по диффу — файлы с совпадающим SHA-1 остаются как есть, изменённые или новые
 * файлы скачиваются, файлы, исчезнувшие из манифеста,
 * удаляются.</p>
 */
public class RemoteBuildService {

    /** Файл манифеста, помечающий папку сборки как распространённую с сервера. */
    public static final String MANIFEST_FILE_NAME = "build.json";

    /** Должен соответствовать раскладке локальной папки {@code builds}. */
    private static final String BUILDS_DIR = "builds";

    private final LauncherServerApi api;
    private final Gson gson;

    public RemoteBuildService(LauncherServerApi api) {
        this(api, new Gson());
    }

    public RemoteBuildService(LauncherServerApi api, Gson gson) {
        this.api = api;
        this.gson = gson;
    }

    /**
     * Перечисляет опубликованные администратором сборки, отсортировав по отображаемому имени.
     *
     * @param session вошедшая сессия
     * @return краткие описания сборок, никогда {@code null}
     * @throws IOException при сетевых ошибках
     */
    public List<BuildSummary> catalog(ServerSession session) throws IOException {
        List<BuildSummary> builds = api.listBuilds(session);
        List<BuildSummary> sorted = new ArrayList<>(builds);
        sorted.sort(Comparator.comparing(BuildSummary::displayName, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    /**
     * Устанавливает сборку в инстанс: получает дескриптор и
     * синхронизирует папку сборки. Безопасно вызывать повторно для той же версии
     * (идемпотентно — совпадающие файлы пропускаются) или для более новой версии
     * (работает как обновление).
     *
     * @param session вошедшая сессия
     * @param summary устанавливаемая сборка, напр. из каталога
     * @param gameDir игровая директория инстанса
     * @return установленный дескриптор
     * @throws IOException при сетевых ошибках или повреждённых загрузках
     */
    public BuildDescriptor install(ServerSession session, BuildSummary summary, Path gameDir) throws IOException {
        return syncBuild(session, gameDir, api.fetchBuild(session, summary.id()));
    }

    /**
     * Обновляет сборку до версии, которую сервер сейчас публикует
     * для её id. Ничего не делает, если локальная папка уже
     * соответствует манифесту.
     *
     * @param session вошедшая сессия
     * @param gameDir игровая директория инстанса
     * @param buildId уникальный id сборки
     * @return обновлённый дескриптор
     * @throws IOException при сетевых ошибках или повреждённых загрузках
     */
    public BuildDescriptor update(ServerSession session, Path gameDir, String buildId) throws IOException {
        return syncBuild(session, gameDir, api.fetchBuild(session, buildId));
    }

    /**
     * Опубликованные администратором сборки, которых в этом инстансе ещё нет —
     * новые папки на стороне источника, определяемые по id. Пользователь
     * сам выбирает, что скачать.
     *
     * @param session вошедшая сессия
     * @param gameDir игровая директория инстанса
     * @return описания из каталога без локальной установки, никогда {@code null}
     * @throws IOException при сетевых ошибках
     */
    public List<BuildSummary> newBuilds(ServerSession session, Path gameDir) throws IOException {
        Set<String> installed = new HashSet<>();
        for (BuildDescriptor descriptor : installedBuilds(gameDir)) {
            installed.add(descriptor.id());
        }
        List<BuildSummary> fresh = new ArrayList<>();
        for (BuildSummary summary : catalog(session)) {
            if (!installed.contains(summary.id())) {
                fresh.add(summary);
            }
        }
        return fresh;
    }

    /**
     * @param gameDir  игровая директория инстанса
     * @param buildId  уникальный id сборки
     * @return установленный манифест этой сборки, если есть
     */
    public Optional<BuildDescriptor> installedBuild(Path gameDir, String buildId) {
        return readManifest(buildDir(gameDir, buildId));
    }

    /**
     * @param gameDir игровая директория инстанса
     * @return все установленные в неё сборки, распространённые с сервера
     * @throws IOException если папку сборок не удаётся прочитать
     */
    public List<BuildDescriptor> installedBuilds(Path gameDir) throws IOException {
        Path builds = gameDir.resolve(BUILDS_DIR);
        if (!Files.isDirectory(builds)) {
            return List.of();
        }
        List<BuildDescriptor> installed = new ArrayList<>();
        try (var stream = Files.list(builds)) {
            for (Path dir : stream.filter(Files::isDirectory).sorted().toList()) {
                readManifest(dir).ifPresent(installed::add);
            }
        }
        return installed;
    }

    /**
     * @param installed  локально установленный манифест
     * @param catalogSum версия, которую сервер сейчас публикует
     * @return true, если версия сервера строго новее и
     *         следует предложить обновление
     */
    public boolean updateAvailable(BuildDescriptor installed, BuildSummary catalogSum) {
        if (installed == null || catalogSum == null || !installed.id().equals(catalogSum.id())) {
            return false;
        }
        return BuildVersions.isNewer(catalogSum.version(), installed.version());
    }

    /** @return локальная папка сборки: {@code gameDir/builds/{buildId}}. */
    public Path buildDir(Path gameDir, String buildId) {
        return gameDir.resolve(BUILDS_DIR).resolve(sanitizeId(buildId));
    }

    private BuildDescriptor syncBuild(ServerSession session, Path gameDir, BuildDescriptor descriptor) throws IOException {
        Path buildDir = buildDir(gameDir, descriptor.id());
        BuildDescriptor previous = readManifest(buildDir).orElse(null);
        Files.createDirectories(buildDir);

        Set<String> wantedKeys = new HashSet<>();
        for (BuildFileEntry file : descriptor.files()) {
            wantedKeys.add(file.key());
            Path target = buildDir.resolve(file.category().folder()).resolve(file.relativePath());
            if (Files.isRegularFile(target) && checksumMatches(target, file)) {
                continue; // уже есть и не изменился — без скачивания
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            api.downloadFile(session, descriptor, file, target);
            if (!checksumMatches(target, file)) {
                throw new IOException("Corrupt download for build " + descriptor.id()
                        + ": " + file.key());
            }
        }

        if (previous != null) {
            for (BuildFileEntry old : previous.files()) {
                if (!wantedKeys.contains(old.key())) {
                    Path stale = buildDir.resolve(old.category().folder()).resolve(old.relativePath());
                    Files.deleteIfExists(stale);
                }
            }
        }

        writeManifest(buildDir, descriptor);
        return descriptor;
    }

    /**
     * Проверяет файл по самому сильному доступному
     * хешу записи (SHA-256, затем SHA-1, затем MD5).
     */
    private boolean checksumMatches(Path file, BuildFileEntry entry) throws IOException {
        if (entry.sha256() != null && !entry.sha256().isBlank()) {
            return computeDigest(file, "SHA-256").equalsIgnoreCase(entry.sha256());
        }
        if (entry.sha1() != null && !entry.sha1().isBlank()) {
            return computeDigest(file, "SHA-1").equalsIgnoreCase(entry.sha1());
        }
        if (entry.md5() != null && !entry.md5().isBlank()) {
            return computeDigest(file, "MD5").equalsIgnoreCase(entry.md5());
        }
        throw new IOException("No checksum to verify " + entry.key());
    }

    private static String computeDigest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(algorithm + " is not available", e);
        }
    }

    private Optional<BuildDescriptor> readManifest(Path buildDir) {
        Path manifest = buildDir.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        try {
            JsonObject root = gson.fromJson(Files.readString(manifest), JsonObject.class);
            if (root == null) {
                return Optional.empty();
            }
            BuildDescriptor descriptor = fromJson(root);
            return Optional.ofNullable(descriptor);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private void writeManifest(Path buildDir, BuildDescriptor descriptor) throws IOException {
        JsonObject root = new JsonObject();
        JsonObject summaryJson = new JsonObject();
        summaryJson.addProperty("id", descriptor.summary().id());
        summaryJson.addProperty("version", descriptor.summary().version());
        summaryJson.addProperty("displayName", descriptor.summary().displayName());
        summaryJson.addProperty("description", descriptor.summary().description());
        summaryJson.addProperty("loaderType", descriptor.summary().loaderType().name());
        summaryJson.addProperty("minecraftVersion", descriptor.summary().minecraftVersion());
        summaryJson.addProperty("loaderVersion", descriptor.summary().loaderVersion());
        root.add("summary", summaryJson);
        root.addProperty("origin", descriptor.origin().name());

        JsonArray files = new JsonArray();
        for (BuildFileEntry file : descriptor.files()) {
            JsonObject fileJson = new JsonObject();
            fileJson.addProperty("relativePath", file.relativePath());
            fileJson.addProperty("category", file.category().name());
            if (file.sha1() != null) {
                fileJson.addProperty("sha1", file.sha1());
            }
            if (file.sha256() != null) {
                fileJson.addProperty("sha256", file.sha256());
            }
            if (file.md5() != null) {
                fileJson.addProperty("md5", file.md5());
            }
            fileJson.addProperty("size", file.size());
            files.add(fileJson);
        }
        root.add("files", files);
        Files.writeString(buildDir.resolve(MANIFEST_FILE_NAME), gson.toJson(root));
    }

    private BuildDescriptor fromJson(JsonObject root) {
        JsonObject summaryJson = root.getAsJsonObject("summary");
        if (summaryJson == null) {
            return null;
        }
        ModLoaderType loaderType;
        try {
            loaderType = ModLoaderType.valueOf(required(summaryJson, "loaderType"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        BuildSummary summary = new BuildSummary(
                required(summaryJson, "id"),
                required(summaryJson, "version"),
                required(summaryJson, "displayName"),
                optional(summaryJson, "description"),
                loaderType,
                required(summaryJson, "minecraftVersion"),
                optional(summaryJson, "loaderVersion"));

        List<BuildFileEntry> files = new ArrayList<>();
        JsonArray filesJson = root.getAsJsonArray("files");
        if (filesJson != null) {
            for (var element : filesJson) {
                JsonObject fileJson = element.getAsJsonObject();
                BuildFileCategory category;
                try {
                    category = BuildFileCategory.valueOf(required(fileJson, "category"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                files.add(new BuildFileEntry(
                        required(fileJson, "relativePath"),
                        category,
                        optional(fileJson, "sha1"),
                        optional(fileJson, "sha256"),
                        optional(fileJson, "md5"),
                        fileJson.has("size") ? fileJson.get("size").getAsLong() : 0L));
            }
        }

        BuildOrigin origin = BuildOrigin.SERVER;
        String originRaw = optional(root, "origin");
        if (originRaw != null) {
            try {
                origin = BuildOrigin.valueOf(originRaw);
            } catch (IllegalArgumentException ignored) {
                // оставить значение SERVER по умолчанию
            }
        }
        return new BuildDescriptor(summary, files, origin);
    }

    private static String required(JsonObject obj, String key) {
        String value = optional(obj, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private static String optional(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String sanitizeId(String buildId) {
        String sanitized = buildId.toLowerCase().replaceAll("[^a-z0-9_-]+", "-");
        return sanitized.isBlank() ? "build" : sanitized;
    }
}
