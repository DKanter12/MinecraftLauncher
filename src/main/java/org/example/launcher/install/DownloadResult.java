package org.example.launcher.install;

/**
 * Outcome of processing a single {@link DownloadTask}.
 */
public final class DownloadResult {

    public enum Status {
        DOWNLOADED,
        SKIPPED,
        FAILED
    }

    private final DownloadTask task;
    private final Status status;
    private final long bytesDownloaded;
    private final String errorMessage;
    private final int attempts;

    private DownloadResult(DownloadTask task, Status status,
                           long bytesDownloaded, String errorMessage,
                           int attempts) {
        this.task = task;
        this.status = status;
        this.bytesDownloaded = bytesDownloaded;
        this.errorMessage = errorMessage;
        this.attempts = attempts;
    }

    public static DownloadResult downloaded(DownloadTask task, long bytes, int attempts) {
        return new DownloadResult(task, Status.DOWNLOADED, bytes, null, attempts);
    }

    public static DownloadResult skipped(DownloadTask task) {
        return new DownloadResult(task, Status.SKIPPED, 0, null, 0);
    }

    public static DownloadResult failed(DownloadTask task, String error, int attempts) {
        return new DownloadResult(task, Status.FAILED, 0, error, attempts);
    }

    public DownloadTask task() {
        return task;
    }

    public Status status() {
        return status;
    }

    public long bytesDownloaded() {
        return bytesDownloaded;
    }

    public String errorMessage() {
        return errorMessage;
    }

    /**
     * Number of download attempts made (0 for skipped, 1+ for
     * downloaded/failed).
     */
    public int attempts() {
        return attempts;
    }

    public boolean isDownloaded() {
        return status == Status.DOWNLOADED;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    @Override
    public String toString() {
        return "DownloadResult{task=" + task.name() + ", status=" + status
                + ", attempts=" + attempts + '}';
    }
}
