package org.example.launcher.infrastructure.updater;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Подготовка обновления к установке: распаковка проверенного пакета
 * в {@code updates/<version>/} и пометка ожидающим.
 */
public class LauncherUpdateInstaller {

    private final Path storageRoot;
    private final Gson gson;

    public LauncherUpdateInstaller(Path storageRoot) {
        this(storageRoot, new Gson());
    }

    public LauncherUpdateInstaller(Path storageRoot, Gson gson) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    /**
     * Распаковывает проверенный пакет. Записи ZIP относительны
     * домашнего каталога приложения; выходящие за него отклоняются.
     *
     * @return каталог подготовки
     */
    public Path install(Path packageFile, LauncherUpdate update)
            throws IOException {
        Objects.requireNonNull(packageFile, "packageFile");
        Objects.requireNonNull(update, "update");
        Path stageDir = storageRoot.resolve(UpdateService.UPDATES_DIR)
                .resolve(sanitizeVersion(update.version()));
        deleteTree(stageDir);
        Files.createDirectories(stageDir);
        try (InputStream in = Files.newInputStream(packageFile);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = stageDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(stageDir)) {
                    throw new IOException(
                            "Update package escapes its directory: "
                                    + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zip, target);
                }
                zip.closeEntry();
            }
        }
        JsonObject pending = new JsonObject();
        pending.addProperty("version", update.version());
        Files.writeString(stageDir.resolveSibling(
                UpdateService.PENDING_FILE_NAME), gson.toJson(pending));
        Files.deleteIfExists(packageFile);
        return stageDir;
    }

    static String sanitizeVersion(String version) {
        String clean = version.replaceAll("[^A-Za-z0-9._-]+", "-");
        return clean.isBlank() ? "unknown" : clean;
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.delete(path);
            }
        }
    }
}
