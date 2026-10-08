package org.example.launcher.service.modloader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.launcher.domain.model.ModLoaderType;

import org.example.launcher.domain.model.ModLoaderVersion;

/**
 * Поставщик {@link ModLoaderVersionProvider} для Forge на метаданных
 * Maven-репозитория Forge.
 * <p>
 * {@code GET https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml}
 * перечисляет все сборки Forge в виде {@code {mcVersion}-{forgeVersion}}
 * (например, {@code 1.20.1-47.4.10}). Записи фильтруются по префиксу запрошенной
 * версии Minecraft, поэтому каждая возвращённая версия совместима по
 * построению. Установочные JAR разрешаются из того же Maven-
 * репозитория.
 */
public class ForgeVersionProvider implements ModLoaderVersionProvider {

    public static final String DEFAULT_METADATA_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";

    public static final String INSTALLER_URL_TEMPLATE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/"
                    + "{mc}-{v}/forge-{mc}-{v}-installer.jar";

    private static final Pattern VERSION_TAG = Pattern.compile(
            "<version>([^<]+)</version>");

    private final String metadataUrl;
    private final String installerUrlTemplate;
    private final HttpClient httpClient;

    public ForgeVersionProvider() {
        this(DEFAULT_METADATA_URL, INSTALLER_URL_TEMPLATE);
    }

    public ForgeVersionProvider(String metadataUrl, String installerUrlTemplate) {
        this(metadataUrl, installerUrlTemplate, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public ForgeVersionProvider(String metadataUrl, String installerUrlTemplate,
                                HttpClient httpClient) {
        this.metadataUrl = metadataUrl;
        this.installerUrlTemplate = installerUrlTemplate;
        this.httpClient = httpClient;
    }

    @Override
    public List<ModLoaderVersion> fetchVersions(String minecraftVersion) throws IOException {
        String body = fetchMetadata();
        List<ModLoaderVersion> versions = parseVersions(body, minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException("No Forge versions found for MC "
                    + minecraftVersion + " — the version may be unsupported");
        }
        return versions;
    }

    @Override
    public java.util.Set<String> fetchSupportedMinecraftVersions() throws IOException {
        return parseSupportedMinecraftVersions(fetchMetadata());
    }

    private String fetchMetadata() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(metadataUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Forge Maven returned HTTP "
                        + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Forge versions", e);
        }
    }

    /**
     * Разбирает Forge maven-metadata.xml и извлекает множество
     * версий Minecraft, для которых существует Forge, — часть до дефиса
     * в записях {@code {mcVersion}-{forgeVersion}}. Выставлен для юнит-
     * тестирования.
     */
    public java.util.Set<String> parseSupportedMinecraftVersions(String xml) {
        java.util.Set<String> result = new java.util.HashSet<>();
        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) {
            String entry = m.group(1).trim();
            int dash = entry.indexOf('-');
            if (dash <= 0) continue;
            String mc = entry.substring(0, dash);
            String build = entry.substring(dash + 1);
            // Обе стороны должны начинаться с цифры — отсеивает странные
            // legacy-записи, не являющиеся парами «{mc}-{build}»
            if (Character.isDigit(mc.charAt(0))
                    && !build.isEmpty()
                    && Character.isDigit(build.charAt(0))) {
                result.add(mc);
            }
        }
        return result;
    }

    /**
     * Разбирает Forge maven-metadata.xml и оставляет только версии под
     * запрошенную версию Minecraft. Выставлен для юнит-тестирования.
     * <p>
     * В метаданных версии идут по возрастанию; результат разворачивается, чтобы
     * новейшая сборка была первой, и новейшая сборка помечается как
     * стабильная (ближайший аналог «recommended»-промо Forge).
     */
    public List<ModLoaderVersion> parseVersions(String xml, String minecraftVersion) {
        String prefix = minecraftVersion + "-";
        List<String> matching = new ArrayList<>();

        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) {
            String version = m.group(1).trim();
            if (version.startsWith(prefix)) {
                matching.add(version.substring(prefix.length()));
            }
        }

        Collections.reverse(matching);

        List<ModLoaderVersion> result = new ArrayList<>(matching.size());
        for (int i = 0; i < matching.size(); i++) {
            String forgeVersion = matching.get(i);
            result.add(new ModLoaderVersion(
                    ModLoaderType.FORGE,
                    forgeVersion,
                    minecraftVersion,
                    i == 0,
                    installerUrlTemplate
                            .replace("{mc}", minecraftVersion)
                            .replace("{v}", forgeVersion)));
        }
        return result;
    }
}
