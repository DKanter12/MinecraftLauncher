package org.example.launcher.model;

/**
 * Describes a single asset object within the asset index.
 * <p>
 * Assets are addressed by their SHA1 hash. The first two characters
 * of the hash form the subdirectory in both the remote URL and the
 * local storage path.
 */
public final class AssetObject {

    final String hash;
    final long size;

    public AssetObject(String hash, long size) {
        this.hash = hash;
        this.size = size;
    }

    public String hash() {
        return hash;
    }

    public long size() {
        return size;
    }

    /**
     * Two-character prefix used for directory partitioning, e.g.
     * {@code "ab"} for hash {@code "abcdef..."}.
     */
    public String hashPrefix() {
        return hash != null && hash.length() >= 2 ? hash.substring(0, 2) : "00";
    }

    @Override
    public String toString() {
        return "AssetObject{hash='" + hash + "', size=" + size + '}';
    }
}
