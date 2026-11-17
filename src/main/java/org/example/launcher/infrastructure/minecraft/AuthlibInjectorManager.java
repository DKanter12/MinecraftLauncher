package org.example.launcher.infrastructure.minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Управляет JAR-файлом authlib-injector — javaagent, который патчит
 * authlib Mojang в рантайме и перенаправляет запросы
 * авторизации/сессий/скинов на серверы Ely.by.
 * <p>
 * JAR скачивается один раз из GitHub releases и кэшируется
 * локально в {@code ~/.minecraft/authlib-injector.jar}.
 *
 * @see <a href="https://docs.ely.by/en/authlib-injector.html">Ely.by docs</a>
 */
public class AuthlibInjectorManager {

    private static final String DOWNLOAD_URL =
            "https://github.com/yushijinhun/authlib-injector/releases/download/v1.2.5/authlib-injector-1.2.5.jar";

    private final GameDirectory gameDir;
    private final HttpClient httpClient;

    public AuthlibInjectorManager(GameDirectory gameDir) {
        this.gameDir = gameDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Возвращает локальный путь к JAR-файлу authlib-injector,
     * при необходимости скачивая его.
     *
     * @return путь к кэшированному authlib-injector.jar
     * @throws IOException если скачивание не удалось
     */
    public Path ensureAvailable() throws IOException {
        Path jarPath = gameDir.root().resolve("authlib-injector.jar");
        if (Files.isRegularFile(jarPath) && Files.size(jarPath) > 1000) {
            return jarPath;
        }

        Files.createDirectories(jarPath.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode()
                        + " downloading authlib-injector");
            }

            Files.write(jarPath, response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("authlib-injector download interrupted", e);
        }

        return jarPath;
    }

    /**
     * Формирует строку JVM-аргумента для javaagent.
     *
     * @param jarPath путь к JAR-файлу authlib-injector
     * @return аргумент {@code -javaagent:...=ely.by}
     */
    public String buildAgentArg(Path jarPath) {
        return "-javaagent:" + jarPath.toAbsolutePath().normalize() + "=ely.by";
    }
}
