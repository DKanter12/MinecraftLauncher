package org.example.launcher.model;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Describes a single library dependency required by a Minecraft version.
 * <p>
 * Each library has a main artifact (the JAR) and optionally a set of
 * classifier downloads used for natives (e.g. {@code natives-windows}).
 * The {@code natives} map links an OS name to the classifier name that
 * should be extracted for that platform.
 */
public final class Library {

    private final String name;
    private final DownloadInfo artifact;
    private final Map<String, DownloadInfo> classifiers;
    private final Map<String, String> natives;

    public Library(String name,
                   DownloadInfo artifact,
                   Map<String, DownloadInfo> classifiers,
                   Map<String, String> natives) {
        this.name = name;
        this.artifact = artifact;
        this.classifiers = classifiers != null
                ? Map.copyOf(classifiers) : Map.of();
        this.natives = natives != null
                ? Map.copyOf(natives) : Map.of();
    }

    public String name() {
        return name;
    }

    public Optional<DownloadInfo> artifact() {
        return Optional.ofNullable(artifact);
    }

    public Map<String, DownloadInfo> classifiers() {
        return Collections.unmodifiableMap(classifiers);
    }

    /**
     * Returns the native classifier download for the given OS name
     * (e.g. {@code "windows"}, {@code "linux"}, {@code "osx"}), or
     * empty if this library has no natives for that platform.
     */
    public Optional<DownloadInfo> nativeDownload(String osName) {
        String classifier = natives.get(osName);
        if (classifier == null) return Optional.empty();
        return Optional.ofNullable(classifiers.get(classifier));
    }

    public boolean hasNatives() {
        return !natives.isEmpty();
    }

    public Map<String, String> natives() {
        return Collections.unmodifiableMap(natives);
    }
}
