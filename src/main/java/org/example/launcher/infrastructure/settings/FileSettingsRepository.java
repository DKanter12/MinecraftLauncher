package org.example.launcher.infrastructure.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.domain.model.LauncherPreferences;

/**
 * Сохраняет лёгкие настройки лаунчера (например, последнюю выбранную версию,
 * последний выбранный аккаунт) в JSON-файл в игровом каталоге.
 */
public class FileSettingsRepository {

    private final Gson gson;
    private final Path prefsFile;

    public FileSettingsRepository(Path prefsFile) {
        this(prefsFile, new Gson());
    }

    public FileSettingsRepository(Path prefsFile, Gson gson) {
        this.prefsFile = prefsFile;
        this.gson = gson;
    }

    private JsonObject readRoot() throws IOException {
        if (!Files.isRegularFile(prefsFile)) {
            return new JsonObject();
        }
        String json = Files.readString(prefsFile);
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            return root != null ? root : new JsonObject();
        } catch (JsonSyntaxException e) {
            return new JsonObject();
        }
    }

    private void writeRoot(JsonObject root) throws IOException {
        Path parent = prefsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(prefsFile, gson.toJson(root));
    }

    /**
     * Читает все настройки разом. Отсутствующие и пустые значения —
     * {@code null}. Повреждённый файл читается как пустые настройки,
     * сам файл при чтении не перезаписывается.
     */
    public LauncherPreferences loadPreferences() throws IOException {
        JsonObject root = readRoot();
        return new LauncherPreferences(
                str(root, "lastSelectedVersion"),
                str(root, "lastSelectedAccount"),
                str(root, "buildsGitUrl"),
                str(root, "buildsSourceMode"),
                str(root, "yandexDiskLink"),
                str(root, "buildsToken"),
                str(root, "language"));
    }

    /**
     * Сохраняет все настройки разом одной записью (вместо семи
     * чтений-записей). Пустые значения пишутся как {@code ""},
     * формат файла совместим со старыми версиями.
     */
    public void savePreferences(LauncherPreferences prefs) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("lastSelectedVersion", orEmpty(prefs.lastSelectedVersion()));
        root.addProperty("lastSelectedAccount", orEmpty(prefs.lastSelectedAccount()));
        root.addProperty("buildsGitUrl", orEmpty(prefs.buildsGitUrl()));
        root.addProperty("buildsSourceMode", orEmpty(prefs.buildsSourceMode()));
        root.addProperty("yandexDiskLink", orEmpty(prefs.yandexDiskLink()));
        root.addProperty("buildsToken", orEmpty(prefs.buildsToken()));
        root.addProperty("language", orEmpty(prefs.language()));
        writeRoot(root);
    }

    private static String str(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return null;
        }
        String val = root.get(key).getAsString();
        return (val == null || val.isBlank()) ? null : val;
    }

    private static String orEmpty(String val) {
        return val != null ? val : "";
    }
}
