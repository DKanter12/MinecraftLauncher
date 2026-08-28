package org.example.launcher.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.example.launcher.install.FileDownloader;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.util.OsDetector;

/**
 * {@link JavaRuntimeInstaller} backed by the Eclipse Adoptium (Temurin) API.
 * <p>
 * Downloads a JRE ZIP archive from
 * {@code https://api.adoptium.net/v3/assets/latest/<major>/hotspot}
 * and extracts it into the target directory. After extraction the
 * installer probes the executable ({@code bin/java} or
 * {@code bin/java.exe}) and returns a {@link JavaRuntime} with the
 * correct major version and {@link JavaRuntime.Source#MANAGED}.
 */
public class AdoptiumJavaRuntimeInstaller implements JavaRuntimeInstaller {

    private static final String API_BASE =
            "https://api.adoptium.net/v3/assets/latest/";
    private static final String API_SUFFIX =
            "/hotspot?architecture=x64&image_type=jre&os=";

    private final FileDownloader fileDownloader;
    private final HttpClient httpClient;

    public AdoptiumJavaRuntimeInstaller(FileDownloader fileDownloader) {
        this.fileDownloader = fileDownloader;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @Override
    public JavaRuntime install(String component, Path targetDir) throws Exception {
        int major = componentToMajor(component);
        return install(major, targetDir);
    }

    @Override
    public JavaRuntime install(int requiredMajor, Path targetDir) throws Exception {
        String apiUrl = buildApiUrl(requiredMajor);
        String zipUrl = fetchZipUrl(apiUrl);

        Files.createDirectories(targetDir);
        Path zipFile = targetDir.resolve("jre-download.zip");

        try {
            fileDownloader.download(zipUrl, zipFile);
            extractZip(zipFile, targetDir);
        } finally {
            Files.deleteIfExists(zipFile);
        }

        Path javaExe = findJavaExecutable(targetDir);
        if (javaExe == null || !Files.isRegularFile(javaExe)) {
            throw new IOException("Could not find java executable in extracted JRE at " + targetDir);
        }

        return new JavaRuntime(javaExe, requiredMajor, JavaRuntime.Source.MANAGED, "Eclipse Temurin");
    }

    private String buildApiUrl(int major) {
        String os = switch (OsDetector.current()) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case OSX -> "mac";
            default -> "windows";
        };
        return API_BASE + major + API_SUFFIX + os;
    }

    private String fetchZipUrl(String apiUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Adoptium API returned HTTP " + response.statusCode());
        }

        String body = response.body();
        return extractPackageLink(body);
    }

    private String extractPackageLink(String json) {
        String marker = "\"package\"";
        int pkgIdx = json.indexOf(marker);
        if (pkgIdx < 0) throw new RuntimeException("Adoptium API response missing 'package' field");

        String linkMarker = "\"link\"";
        int linkIdx = json.indexOf(linkMarker, pkgIdx);
        if (linkIdx < 0) throw new RuntimeException("Adoptium API response missing package link");

        int start = json.indexOf('"', linkIdx + linkMarker.length()) + 1;
        int end = json.indexOf('"', start);
        return json.substring(start, end)
                .replace("\\/", "/");
    }

    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path dest = targetDir.resolve(entry.getName());

                if (entry.getName().contains("..")) continue;

                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private Path findJavaExecutable(Path root) throws IOException {
        String exeName = OsDetector.current() == OsDetector.Os.WINDOWS
                ? "java.exe" : "java";

        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.getFileName().toString().equals(exeName))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        }
    }

    private int componentToMajor(String component) {
        return switch (component) {
            case "jre-legacy" -> 8;
            case "java-runtime-alpha" -> 8;
            case "java-runtime-beta" -> 8;
            case "java-runtime-gamma" -> 17;
            case "java-runtime-delta" -> 21;
            default -> 8;
        };
    }
}
