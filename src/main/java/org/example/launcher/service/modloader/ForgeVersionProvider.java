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

import org.example.launcher.model.ModLoaderVersion;

/**
 * {@link ModLoaderVersionProvider} for Forge, backed by the Forge
 * Maven repository metadata.
 * <p>
 * {@code GET https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml}
 * lists all Forge builds in the form {@code {mcVersion}-{forgeVersion}}
 * (e.g. {@code 1.20.1-47.4.10}). Entries are filtered by the requested
 * Minecraft version prefix, so every returned version is compatible by
 * construction. Installer JARs are resolved from the same Maven
 * repository.
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
     * Parses Forge maven-metadata.xml and extracts the set of
     * Minecraft versions Forge exists for — the part before the dash
     * of {@code {mcVersion}-{forgeVersion}} entries. Exposed for unit
     * testing.
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
            // Both sides must start with a digit — filters odd
            // legacy entries that are not "{mc}-{build}" pairs
            if (Character.isDigit(mc.charAt(0))
                    && !build.isEmpty()
                    && Character.isDigit(build.charAt(0))) {
                result.add(mc);
            }
        }
        return result;
    }

    /**
     * Parses Forge maven-metadata.xml and keeps only versions matching
     * the requested Minecraft version. Exposed for unit testing.
     * <p>
     * The metadata lists versions ascending; the result is reversed so
     * the newest build comes first, and the newest build is flagged as
     * stable (the closest analogue to Forge's "recommended" promo).
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
