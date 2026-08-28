package org.example.launcher.install;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Centralised definition of the local Minecraft storage layout.
 * <p>
 * All components (installer, launcher, profile manager) resolve paths
 * through this class, ensuring a consistent directory structure and
 * enabling reuse of shared libraries/assets between versions.
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

    /** Root game directory (e.g. {@code ~/.minecraft}). */
    public Path root() {
        return root;
    }

    // --- Versions ---

    public Path versionsDir() {
        return root.resolve("versions");
    }

    /** Directory for a specific version, e.g. {@code versions/1.21/}. */
    public Path versionDir(String versionId) {
        return versionsDir().resolve(versionId);
    }

    /** Client JAR path, e.g. {@code versions/1.21/1.21.jar}. */
    public Path clientJar(String versionId) {
        return versionDir(versionId).resolve(versionId + ".jar");
    }

    /** Cached version metadata JSON, e.g. {@code versions/1.21/1.21.json}. */
    public Path versionMetadata(String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    // --- Libraries (shared) ---

    public Path librariesDir() {
        return root.resolve("libraries");
    }

    /**
     * Resolves a library JAR path from its Maven-style relative path
     * (e.g. {@code "com/mojang/logging/1.1.1/logging-1.1.1.jar"}).
     */
    public Path library(String relativePath) {
        return librariesDir().resolve(relativePath.replace('/',
                root.getFileSystem().getSeparator().charAt(0)));
    }

    // --- Natives (version-specific extraction target) ---

    public Path nativesDir() {
        return root.resolve("natives");
    }

    /** Extraction directory for a specific version's natives. */
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
     * Path for an individual asset object, addressed by its SHA-1 hash
     * prefix + full hash (e.g. {@code assets/objects/ab/abcdef...}).
     */
    public Path assetObject(String hashPrefix, String hash) {
        return assetObjectsDir().resolve(hashPrefix).resolve(hash);
    }

    /** Virtual assets directory (legacy versions that need a flat copy). */
    public Path virtualAssetsDir(String indexId) {
        return assetsDir().resolve("virtual").resolve(indexId);
    }

    // --- Java runtimes (managed by launcher, future auto-install) ---

    /**
     * Directory for launcher-managed Java runtimes, e.g.
     * {@code java-runtimes/java-runtime-gamma/}.
     */
    public Path javaRuntimesDir() {
        return root.resolve("java-runtimes");
    }

    /**
     * Path for a specific managed runtime, e.g.
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

    // --- Directory creation ---

    /** Creates the full directory tree if it does not already exist. */
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
