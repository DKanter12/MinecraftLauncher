package org.example.launcher.install;

/**
 * Callback interface for receiving installation progress updates.
 * <p>
 * The UI implements this to show a progress bar, current file name,
 * and category during the installation process.
 */
public interface InstallationProgress {

    /**
     * Called once the task list has been built, before any downloads start.
     *
     * @param totalTasks  total number of files to process
     * @param totalBytes  total expected download size in bytes (may be approximate)
     */
    void onStart(int totalTasks, long totalBytes);

    /**
     * Called before a file is processed (either downloaded or skipped).
     *
     * @param taskIndex    0-based index of the current task
     * @param task         the task about to be processed
     */
    void onFileStart(int taskIndex, DownloadTask task);

    /**
     * Called after a file has been processed.
     *
     * @param taskIndex  0-based index of the completed task
     * @param result     the download result
     */
    void onFileComplete(int taskIndex, DownloadResult result);

    /**
     * Called when the entire installation finishes (successfully or not).
     *
     * @param result  aggregate installation result
     */
    void onComplete(InstallationResult result);

    /**
     * No-op implementation for callers that don't need progress updates.
     */
    InstallationProgress NONE = new InstallationProgress() {
        @Override public void onStart(int totalTasks, long totalBytes) {}
        @Override public void onFileStart(int taskIndex, DownloadTask task) {}
        @Override public void onFileComplete(int taskIndex, DownloadResult result) {}
        @Override public void onComplete(InstallationResult result) {}
    };
}
