package org.example.launcher.install;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes a single file that needs to be downloaded and verified
 * during Minecraft installation.
 * <p>
 * Each task carries:
 * <ul>
 *   <li>the remote URL to download from</li>
 *   <li>the local target path</li>
 *   <li>an optional expected SHA1 hash for verification</li>
 *   <li>the expected file size (for progress reporting)</li>
 *   <li>a human-readable category (client, library, native, asset)</li>
 * </ul>
 */
public final class DownloadTask {

    private final String url;
    private final Path targetPath;
    private final String expectedSha1;
    private final long expectedSize;
    private final String category;
    private final String name;

    public DownloadTask(String url, Path targetPath, String expectedSha1,
                        long expectedSize, String category, String name) {
        this.url = Objects.requireNonNull(url, "url");
        this.targetPath = Objects.requireNonNull(targetPath, "targetPath");
        this.expectedSha1 = expectedSha1;
        this.expectedSize = expectedSize;
        this.category = Objects.requireNonNull(category, "category");
        this.name = name != null ? name : targetPath.getFileName().toString();
    }

    public String url() {
        return url;
    }

    public Path targetPath() {
        return targetPath;
    }

    public Optional<String> expectedSha1() {
        return Optional.ofNullable(expectedSha1);
    }

    public long expectedSize() {
        return expectedSize;
    }

    public String category() {
        return category;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "DownloadTask{category='" + category + "', name='" + name
                + "', url='" + url + "', sha1=" + expectedSha1 + '}';
    }
}
