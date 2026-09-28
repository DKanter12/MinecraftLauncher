package org.example.launcher.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Минимальный загрузчик URL для собственных сетевых функций лаунчера
 * (проверка обновлений, распределяемые через git сборки).
 * Прямое соединение, без прокси.
 */
public class UrlFetcher {

    private static final String USER_AGENT = "MinecraftLauncher/update";

    private static final int BUFFER_SIZE = 65536;

    public UrlFetcher() {
    }

    /** Загружает тело URL как текст в UTF-8. */
    public String fetchText(String url, Duration timeout) throws IOException {
        return fetchText(url, timeout, Map.of());
    }

    /** Загружает тело URL как текст в UTF-8 с дополнительными заголовками. */
    public String fetchText(String url, Duration timeout,
                            Map<String, String> headers) throws IOException {
        URLConnection connection = open(url, timeout, headers);
        assertOk(url, connection);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Скачивает URL в {@code target} (родительские каталоги создаются). */
    public void download(String url, Path target, Duration timeout,
                         ProgressListener progress) throws IOException {
        download(url, target, timeout, Map.of(), progress);
    }

    /** Скачивает URL в {@code target} с дополнительными заголовками. */
    public void download(String url, Path target, Duration timeout,
                         Map<String, String> headers,
                         ProgressListener progress) throws IOException {
        URLConnection connection = open(url, timeout, headers);
        assertOk(url, connection);
        long total = connection.getContentLengthLong();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (progress != null) {
                    progress.onProgress(downloaded, total);
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e instanceof IOException io ? io : new IOException(e);
        }
        Files.move(tmp, target,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Прогресс загрузки; {@code total} равен -1, если неизвестен. */
    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    private URLConnection open(String url, Duration timeout,
                               Map<String, String> headers) throws IOException {
        URL parsed = new URL(url);
        URLConnection connection = parsed.openConnection();
        connection.setConnectTimeout((int) timeout.toMillis());
        connection.setReadTimeout((int) timeout.plusSeconds(30).toMillis());
        connection.setRequestProperty("User-Agent", USER_AGENT);
        for (var header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        return connection;
    }

    private static void assertOk(String url, URLConnection connection)
            throws IOException {
        if (connection instanceof HttpURLConnection http) {
            int code = http.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + url);
            }
        }
    }
}
