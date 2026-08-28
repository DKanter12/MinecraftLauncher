package org.example.launcher.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed metadata for a single Minecraft version, obtained from the
 * per-version JSON referenced in the Mojang version manifest.
 * <p>
 * This is the central data structure that downstream components
 * (installer, launcher process builder) consume to install and
 * start the game.
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
     * Main class to launch, e.g.
     * {@code "net.minecraft.client.main.Main"}.
     */
    public Optional<String> mainClass() {
        return Optional.ofNullable(mainClass);
    }

    /**
     * Asset index name (legacy field, also present in modern versions).
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
     * Download descriptor for the client JAR file.
     */
    public Optional<DownloadInfo> clientDownload() {
        return Optional.ofNullable(clientDownload);
    }

    /**
     * All libraries required by this version.
     */
    public List<Library> libraries() {
        return Collections.unmodifiableList(libraries);
    }

    /**
     * Libraries that contain natives for the given OS name.
     */
    public List<Library> nativeLibraries(String osName) {
        return libraries.stream()
                .filter(lib -> lib.nativeDownload(osName).isPresent())
                .toList();
    }

    /**
     * Game (client) arguments in structured form (modern versions).
     */
    public List<String> gameArguments() {
        return Collections.unmodifiableList(gameArguments);
    }

    /**
     * JVM arguments in structured form (modern versions).
     */
    public List<String> jvmArguments() {
        return Collections.unmodifiableList(jvmArguments);
    }

    /**
     * Legacy space-separated argument string (versions before 1.13).
     * Present when {@link #gameArguments()} is empty.
     */
    public Optional<String> legacyMinecraftArguments() {
        return Optional.ofNullable(legacyMinecraftArguments);
    }

    /**
     * Whether this version uses the modern structured argument format.
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
