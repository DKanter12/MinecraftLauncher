package org.example.launcher.infrastructure.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

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

    public Optional<String> getLastSelectedVersion() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("lastSelectedVersion")) return Optional.empty();
        String val = root.get("lastSelectedVersion").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setLastSelectedVersion(String versionId) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("lastSelectedVersion", versionId != null ? versionId : "");
        writeRoot(root);
    }

    public Optional<String> getLastSelectedAccount() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("lastSelectedAccount")) return Optional.empty();
        String val = root.get("lastSelectedAccount").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setLastSelectedAccount(String accountName) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("lastSelectedAccount", accountName != null ? accountName : "");
        writeRoot(root);
    }

    public Optional<String> getBuildsGitUrl() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("buildsGitUrl")) return Optional.empty();
        String val = root.get("buildsGitUrl").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setBuildsGitUrl(String url) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("buildsGitUrl", url != null ? url : "");
        writeRoot(root);
    }

    public Optional<String> getBuildsSourceMode() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("buildsSourceMode")) return Optional.empty();
        String val = root.get("buildsSourceMode").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setBuildsSourceMode(String mode) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("buildsSourceMode", mode != null ? mode : "");
        writeRoot(root);
    }

    public Optional<String> getYandexDiskLink() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("yandexDiskLink")) return Optional.empty();
        String val = root.get("yandexDiskLink").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setYandexDiskLink(String link) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("yandexDiskLink", link != null ? link : "");
        writeRoot(root);
    }

    public Optional<String> getBuildsToken() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("buildsToken")) return Optional.empty();
        String val = root.get("buildsToken").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setBuildsToken(String token) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("buildsToken", token != null ? token : "");
        writeRoot(root);
    }

    public Optional<String> getLanguage() throws IOException {
        JsonObject root = readRoot();
        if (!root.has("language")) return Optional.empty();
        String val = root.get("language").getAsString();
        return (val == null || val.isBlank()) ? Optional.empty() : Optional.of(val);
    }

    public void setLanguage(String code) throws IOException {
        JsonObject root = readRoot();
        root.addProperty("language", code != null ? code : "");
        writeRoot(root);
    }
}
