package org.example.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Persists lightweight launcher preferences (e.g. last selected version,
 * last selected account) to a JSON file in the game directory.
 */
public class LauncherPreferences {

    private final Gson gson;
    private final Path prefsFile;

    public LauncherPreferences(Path prefsFile) {
        this(prefsFile, new Gson());
    }

    public LauncherPreferences(Path prefsFile, Gson gson) {
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
}
