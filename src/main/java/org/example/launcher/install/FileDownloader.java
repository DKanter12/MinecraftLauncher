package org.example.launcher.install;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Low-level file download port.
 * <p>
 * Responsible only for downloading a single file from a URL to a local
 * path. The caller is responsible for directory creation and hash
 * verification.
 */
public interface FileDownloader {

    /**
     * Downloads a file from {@code url} to {@code targetPath}.
     * <p>
     * Parent directories are created if needed. If the target file
     * already exists it is overwritten.
     *
     * @param url        the remote URL to download from
     * @param targetPath the local file to write to
     * @return the number of bytes downloaded
     * @throws IOException if the download fails for any reason
     */
    long download(String url, Path targetPath) throws IOException;
}
