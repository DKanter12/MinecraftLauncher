package org.example.launcher.infrastructure.updater;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Одно опубликованное обновление лаунчера, как описано в
 * {@code launcher-version.json} в ветке main:
 * <pre>
 * {
 *   "version": "1.1.0",
 *   "notes": "Исправления ошибок и новая тема",
 *   "package": {
 *     "url": "https://github.com/DKanter12/LaunchBuildUseCase/releases/download/v1.1.0/launcher-win64.zip",
 *     "sha256": "&lt;hex&gt;",
 *     "size": 123456
 *   }
 * }
 * </pre>
 * Пакет — ZIP, чьи записи относительны домашнего каталога
 * приложения (jar, скрипты, ресурсы, документация) — никогда
 * файлы Minecraft и никогда данные пользователя.
 *
 * @param version    опубликованная версия лаунчера
 * @param notes      человекочитаемый журнал изменений
 * @param packageUrl URL скачивания ZIP обновления
 * @param sha256     SHA-256 ZIP в hex нижнего регистра
 * @param size       размер ZIP в байтах (0, если неизвестен)
 */
public record LauncherUpdate(
        String version,
        String notes,
        String packageUrl,
        String sha256,
        long size) {

    public LauncherUpdate {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (packageUrl == null || packageUrl.isBlank()) {
            throw new IllegalArgumentException("packageUrl must not be blank");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must not be blank");
        }
        if (notes == null) {
            notes = "";
        }
    }

    /** Разбирает JSON манифеста из ветки main. */
    public static LauncherUpdate parse(String json) throws IllegalArgumentException {
        JsonObject root;
        try {
            root = new Gson().fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid update manifest JSON", e);
        }
        if (root == null) {
            throw new IllegalArgumentException("Empty update manifest");
        }
        String version = text(root, "version");
        String notes = root.has("notes") && root.get("notes").isJsonPrimitive()
                ? root.get("notes").getAsString() : "";
        JsonObject pkg = root.has("package") && root.get("package").isJsonObject()
                ? root.getAsJsonObject("package") : null;
        if (version == null || pkg == null) {
            throw new IllegalArgumentException(
                    "Update manifest needs version + package");
        }
        String url = pkg.has("url") && pkg.get("url").isJsonPrimitive()
                ? pkg.get("url").getAsString() : null;
        String sha256 = pkg.has("sha256") && pkg.get("sha256").isJsonPrimitive()
                ? pkg.get("sha256").getAsString() : null;
        long size = pkg.has("size") && pkg.get("size").isJsonPrimitive()
                ? pkg.get("size").getAsLong() : 0L;
        if (url == null || sha256 == null) {
            throw new IllegalArgumentException(
                    "Update manifest package needs url + sha256");
        }
        return new LauncherUpdate(version, notes, url, sha256, size);
    }

    private static String text(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }
}
