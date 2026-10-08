package org.example.launcher.infrastructure.filesystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Централизованное определение раскладки локального хранилища Minecraft.
 * <p>
 * Все компоненты (установщик, лаунчер, менеджер профилей) разрешают пути
 * через этот класс, обеспечивая единообразную структуру каталогов и
 * переиспользование общих библиотек/ресурсов между версиями.
 *
 * <pre>
 * gameDir/
 *   versions/
 *     <id>/
 *       <id>.jar          ← client JAR (version-specific)
 *       <id>.json         ← version metadata (cached)
 *   libraries/
 *     com/mojang/...      ← shared between all versions
 *     org/lwjgl/...
 *   natives/
 *     <id>/               ← extracted natives (version-specific)
 *   assets/
 *     indexes/
 *       <id>.json         ← asset index files (shared)
 *     objects/
 *       ab/abcdef...      ← asset objects (shared, hash-addressed)
 *     virtual/
 *       <id>/             ← virtual assets (legacy, version-specific)
 *   launcher_profiles.json  ← player profiles
 * </pre>
 */
public final class GameDirectory {

    private final Path root;

    public GameDirectory(Path root) {
        this.root = Objects.requireNonNull(root);
    }

    public static GameDirectory defaultDirectory() {
        String home = System.getProperty("user.home");
        return new GameDirectory(Path.of(home, ".minecraft"));
    }

    /** Корневой игровой каталог (например, {@code ~/.minecraft}). */
    public Path root() {
        return root;
    }

    // --- Versions ---

    public Path versionsDir() {
        return root.resolve("versions");
    }

    /** Каталог конкретной версии, например {@code versions/1.21/}. */
    public Path versionDir(String versionId) {
        return versionsDir().resolve(versionId);
    }

    /** Путь клиентского JAR, например {@code versions/1.21/1.21.jar}. */
    public Path clientJar(String versionId) {
        return versionDir(versionId).resolve(versionId + ".jar");
    }

    /** Кэшированный JSON метаданных версии, например {@code versions/1.21/1.21.json}. */
    public Path versionMetadata(String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    // --- Libraries (shared) ---

    public Path librariesDir() {
        return root.resolve("libraries");
    }

    /**
     * Разрешает путь JAR библиотеки по её относительному пути в стиле Maven
     * (например, {@code "com/mojang/logging/1.1.1/logging-1.1.1.jar"}).
     */
    public Path library(String relativePath) {
        return librariesDir().resolve(relativePath.replace('/',
                root.getFileSystem().getSeparator().charAt(0)));
    }

    // --- Natives (version-specific extraction target) ---

    public Path nativesDir() {
        return root.resolve("natives");
    }

    /** Каталог распаковки нативных файлов конкретной версии. */
    public Path nativeDir(String versionId) {
        return nativesDir().resolve(versionId);
    }

    // --- Assets (shared, hash-addressed) ---

    public Path assetsDir() {
        return root.resolve("assets");
    }

    public Path assetIndexesDir() {
        return assetsDir().resolve("indexes");
    }

    public Path assetIndexFile(String indexId) {
        return assetIndexesDir().resolve(indexId + ".json");
    }

    public Path assetObjectsDir() {
        return assetsDir().resolve("objects");
    }

    /**
     * Путь отдельного объекта ресурсов, адресуемого по префиксу SHA-1 хэша
     * и полному хэшу (например, {@code assets/objects/ab/abcdef...}).
     */
    public Path assetObject(String hashPrefix, String hash) {
        return assetObjectsDir().resolve(hashPrefix).resolve(hash);
    }

    /** Каталог виртуальных ресурсов (устаревшие версии с плоской копией). */
    public Path virtualAssetsDir(String indexId) {
        return assetsDir().resolve("virtual").resolve(indexId);
    }

    // --- Java runtimes (managed by launcher, future auto-install) ---

    /**
     * Каталог управляемых лаунчером сред Java, например
     * {@code java-runtimes/java-runtime-gamma/}.
     */
    public Path javaRuntimesDir() {
        return root.resolve("java-runtimes");
    }

    /**
     * Путь конкретной управляемой среды, например
     * {@code java-runtimes/java-runtime-gamma/}.
     */
    public Path javaRuntimeDir(String component) {
        return javaRuntimesDir().resolve(component);
    }

    // --- Profiles ---

    public Path profilesFile() {
        return root.resolve("launcher_profiles.json");
    }

    public Path preferencesFile() {
        return root.resolve("launcher_preferences.json");
    }

    // --- Instances (per-instance game directories) ---

    /**
     * Корневой каталог игровых каталогов всех экземпляров,
     * например {@code gameDir/profiles/}.
     */
    public Path moddedProfilesRoot() {
        return root.resolve("profiles");
    }

    /**
     * Игровой каталог одного экземпляра, например
     * {@code gameDir/profiles/{instanceId}}. Хранит
     * {@code mods/}, {@code config/}, {@code resourcepacks/},
     * {@code shaderpacks/}, {@code saves/} и {@code logs/} экземпляра.
     */
    public Path moddedProfileDir(String profileId) {
        return moddedProfilesRoot().resolve(profileId);
    }

    /** Файл persistence реестра экземпляров. */
    public Path instancesFile() {
        return root.resolve("instances.json");
    }

    /**
     * Устаревший файл persistence, использовавшийся до поддержки ванильных
     * игр экземплярами. Читается как запасной вариант службой экземпляров, когда
     * {@link #instancesFile()} ещё не существует.
     */
    public Path legacyModdedProfilesFile() {
        return root.resolve("modded_profiles.json");
    }

    // --- Directory creation ---

    /** Создаёт полное дерево каталогов, если оно ещё не существует. */
    public void createDirectories() throws java.io.IOException {
        Files.createDirectories(versionsDir());
        Files.createDirectories(librariesDir());
        Files.createDirectories(nativesDir());
        Files.createDirectories(assetIndexesDir());
        Files.createDirectories(assetObjectsDir());
        Files.createDirectories(javaRuntimesDir());
    }

    @Override
    public String toString() {
        return "GameDirectory{root=" + root + '}';
    }
}
