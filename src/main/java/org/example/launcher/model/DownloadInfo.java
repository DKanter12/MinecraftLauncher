package org.example.launcher.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes a downloadable file (client JAR, library artifact, asset index).
 * Immutable value object.
 */
public final class DownloadInfo {

    private final String url;
    private final String sha1;
    private final long size;
    private final String path;

    public DownloadInfo(String url, String sha1, long size, String path) {
        this.url = url;
        this.sha1 = sha1;
        this.size = size;
        this.path = path;
    }

    public String url() {
        return url;
    }

    public Optional<String> sha1() {
        return Optional.ofNullable(sha1);
    }

    public long size() {
        return size;
    }

    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DownloadInfo that)) return false;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "DownloadInfo{url='" + url + "', size=" + size + '}';
    }
}
