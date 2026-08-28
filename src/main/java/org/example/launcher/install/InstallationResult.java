package org.example.launcher.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate result of a full Minecraft version installation.
 */
public final class InstallationResult {

    private final int totalTasks;
    private final int downloaded;
    private final int skipped;
    private final int failed;
    private final long totalBytesDownloaded;
    private final List<DownloadResult> failedTasks;

    public InstallationResult(int totalTasks, int downloaded, int skipped,
                               int failed, long totalBytesDownloaded,
                               List<DownloadResult> failedTasks) {
        this.totalTasks = totalTasks;
        this.downloaded = downloaded;
        this.skipped = skipped;
        this.failed = failed;
        this.totalBytesDownloaded = totalBytesDownloaded;
        this.failedTasks = failedTasks != null
                ? List.copyOf(failedTasks) : List.of();
    }

    public int totalTasks() {
        return totalTasks;
    }

    public int downloaded() {
        return downloaded;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public long totalBytesDownloaded() {
        return totalBytesDownloaded;
    }

    public List<DownloadResult> failedTasks() {
        return Collections.unmodifiableList(failedTasks);
    }

    public boolean isSuccess() {
        return failed == 0;
    }

    public boolean hasFailures() {
        return failed > 0;
    }

    public String summary() {
        return String.format("Installed: %d downloaded, %d skipped, %d failed (out of %d, %s)",
                downloaded, skipped, failed, totalTasks,
                formatBytes(totalBytesDownloaded));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public String toString() {
        return summary();
    }
}
