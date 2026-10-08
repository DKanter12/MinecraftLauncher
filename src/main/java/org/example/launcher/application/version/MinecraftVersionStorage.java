package org.example.launcher.application.version;

import java.nio.file.Path;
import java.util.Objects;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Расположение установленных версий Minecraft.
 * Версии — общий ресурс: лежат отдельно от сборок и переиспользуются ими.
 * Библиотеки и ресурсы общие для всех версий и не дублируются.
 */
public class MinecraftVersionStorage {

    private final GameDirectory storage;

    public MinecraftVersionStorage(GameDirectory storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /** Каталог версии: {@code versions/<id>/}. */
    public Path versionDir(String versionId) {
        return storage.versionDir(versionId);
    }

    /** Метаданные версии: {@code versions/<id>/<id>.json}. */
    public Path versionJson(String versionId) {
        return storage.versionMetadata(versionId);
    }

    /** Клиент версии: {@code versions/<id>/<id>.jar}. */
    public Path clientJar(String versionId) {
        return storage.clientJar(versionId);
    }

    /** Общие библиотеки всех версий: {@code libraries/}. */
    public Path librariesDir() {
        return storage.librariesDir();
    }

    /** Общие ресурсы всех версий: {@code assets/}. */
    public Path assetsDir() {
        return storage.assetsDir();
    }

    /** Распакованные нативы версии: {@code natives/<id>/}. */
    public Path nativesDir(String versionId) {
        return storage.nativeDir(versionId);
    }
}
