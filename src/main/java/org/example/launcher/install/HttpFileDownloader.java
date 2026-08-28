package org.example.launcher.install;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * {@link FileDownloader} backed by {@link HttpClient}.
 * <p>
 * Downloads to a temporary file first, then atomically moves it to the
 * target path. This avoids leaving partial/corrupt files on disk when
 * a download is interrupted.
 */
public class HttpFileDownloader implements FileDownloader {

    private static final String TEMP_SUFFIX = ".part";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpFileDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public HttpFileDownloader(HttpClient httpClient) {
        this(httpClient, DEFAULT_TIMEOUT);
    }

    public HttpFileDownloader(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    @Override
    public long download(String url, Path targetPath) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(targetPath, "targetPath");

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = targetPath.resolveSibling(
                targetPath.getFileName() + TEMP_SUFFIX);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<Path> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode()
                        + " downloading " + url);
            }

            long size = Files.size(tempFile);
            Files.move(tempFile, targetPath,
                    StandardCopyOption.REPLACE_EXISTING);
            return size;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(tempFile);
            throw new IOException("Download interrupted: " + url, e);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw e;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
