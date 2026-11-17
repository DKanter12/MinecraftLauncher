package org.example.launcher.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Разобранные метаданные одной версии Minecraft из
 * JSON версии, на который ссылается манифест версий Mojang.
 * <p>
 * Центральная структура данных, которую последующие компоненты
 * (установщик, построитель процесса запуска) используют для установки и
 * запуска игры.
 */
public final class VersionMetadata {

    private final String id;
    private final String type;
    private final String mainClass;
    private final String assets;
    private final AssetIndex assetIndex;
    private final JavaVersion javaVersion;
    private final DownloadInfo clientDownload;
    private final List<Library> libraries;
    private final List<String> gameArguments;
    private final List<String> jvmArguments;
    private final String legacyMinecraftArguments;

    public VersionMetadata(String id,
                           String type,
                           String mainClass,
                           String assets,
                           AssetIndex assetIndex,
                           JavaVersion javaVersion,
                           DownloadInfo clientDownload,
                           List<Library> libraries,
                           List<String> gameArguments,
                           List<String> jvmArguments,
                           String legacyMinecraftArguments) {
        this.id = id;
        this.type = type;
        this.mainClass = mainClass;
        this.assets = assets;
        this.assetIndex = assetIndex;
        this.javaVersion = javaVersion;
        this.clientDownload = clientDownload;
        this.libraries = libraries != null ? List.copyOf(libraries) : List.of();
        this.gameArguments = gameArguments != null ? List.copyOf(gameArguments) : List.of();
        this.jvmArguments = jvmArguments != null ? List.copyOf(jvmArguments) : List.of();
        this.legacyMinecraftArguments = legacyMinecraftArguments;
    }

    public String id() {
        return id;
    }

    public Optional<String> type() {
        return Optional.ofNullable(type);
    }

    /**
     * Главный класс для запуска, например
     * {@code "net.minecraft.client.main.Main"}.
     */
    public Optional<String> mainClass() {
        return Optional.ofNullable(mainClass);
    }

    /**
     * Имя индекса ресурсов (устаревшее поле, присутствует и в современных версиях).
     */
    public Optional<String> assets() {
        return Optional.ofNullable(assets);
    }

    public Optional<AssetIndex> assetIndex() {
        return Optional.ofNullable(assetIndex);
    }

    public Optional<JavaVersion> javaVersion() {
        return Optional.ofNullable(javaVersion);
    }

    /**
     * Дескриптор загрузки клиентского JAR-файла.
     */
    public Optional<DownloadInfo> clientDownload() {
        return Optional.ofNullable(clientDownload);
    }

    /**
     * Все библиотеки, требуемые этой версии.
     */
    public List<Library> libraries() {
        return Collections.unmodifiableList(libraries);
    }

    /**
     * Библиотеки, содержащие нативные файлы для заданного имени ОС.
     */
    public List<Library> nativeLibraries(String osName) {
        return libraries.stream()
                .filter(lib -> lib.nativeDownload(osName).isPresent())
                .toList();
    }

    /**
     * Игровые (клиентские) аргументы в структурированной форме (современные версии).
     */
    public List<String> gameArguments() {
        return Collections.unmodifiableList(gameArguments);
    }

    /**
     * Аргументы JVM в структурированной форме (современные версии).
     */
    public List<String> jvmArguments() {
        return Collections.unmodifiableList(jvmArguments);
    }

    /**
     * Устаревшая строка аргументов через пробел (версии до 1.13).
     * Присутствует, когда {@link #gameArguments()} пуст.
     */
    public Optional<String> legacyMinecraftArguments() {
        return Optional.ofNullable(legacyMinecraftArguments);
    }

    /**
     * Признак использования современного структурированного формата аргументов.
     */
    public boolean hasStructuredArguments() {
        return !gameArguments.isEmpty() || !jvmArguments.isEmpty();
    }

    @Override
    public String toString() {
        return "VersionMetadata{id='" + id + "', type='" + type
                + "', libraries=" + libraries.size() + '}';
    }
}
