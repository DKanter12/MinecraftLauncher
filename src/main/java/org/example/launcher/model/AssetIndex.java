package org.example.launcher.model;

import java.util.Optional;

/**
 * Reference to the asset index used by a Minecraft version.
 */
public final class AssetIndex {

    private final String id;
    private final String sha1;
    private final long size;
    private final long totalSize;
    private final String url;

    public AssetIndex(String id, String sha1, long size, long totalSize, String url) {
        this.id = id;
        this.sha1 = sha1;
        this.size = size;
        this.totalSize = totalSize;
        this.url = url;
    }

    public String id() {
        return id;
    }

    public Optional<String> sha1() {
        return Optional.ofNullable(sha1);
    }

    public long size() {
        return size;
    }

    public long totalSize() {
        return totalSize;
    }

    public String url() {
        return url;
    }

    @Override
    public String toString() {
        return "AssetIndex{id='" + id + "', url='" + url + "'}";
    }
}
