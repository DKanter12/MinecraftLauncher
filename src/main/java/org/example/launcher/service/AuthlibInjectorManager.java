package org.example.launcher.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.example.launcher.install.GameDirectory;

/**
 * Manages the authlib-injector JAR — a javaagent that patches
 * Mojang's authlib at runtime to redirect auth/session/skin
 * requests to Ely.by servers.
 * <p>
 * The JAR is downloaded once from GitHub releases and cached
 * locally in {@code ~/.minecraft/authlib-injector.jar}.
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
     * Returns the local path to the authlib-injector JAR,
     * downloading it if necessary.
     *
     * @return path to the cached authlib-injector.jar
     * @throws IOException if download fails
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
     * Builds the JVM argument string for the javaagent.
     *
     * @param jarPath path to the authlib-injector JAR
     * @return the {@code -javaagent:...=ely.by} argument
     */
    public String buildAgentArg(Path jarPath) {
        return "-javaagent:" + jarPath.toAbsolutePath().normalize() + "=ely.by";
    }
}
