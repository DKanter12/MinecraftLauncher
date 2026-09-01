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
 * {@link ModLoaderVersionProvider} for NeoForge, backed by the
 * NeoForge Maven repository metadata.
 * <p>
 * {@code GET https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml}
 * lists all NeoForge builds. NeoForge version numbering encodes the
 * target Minecraft version (see {@link #neoforgePrefix(String)}), so
 * entries are filtered by prefix and every returned version is
 * compatible by construction.
 */
public class NeoForgeVersionProvider implements ModLoaderVersionProvider {

    public static final String DEFAULT_METADATA_URL =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

    public static final String INSTALLER_URL_TEMPLATE =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
                    + "{v}/neoforge-{v}-installer.jar";

    private static final Pattern VERSION_TAG = Pattern.compile(
            "<version>([^<]+)</version>");

    private final String metadataUrl;
    private final String installerUrlTemplate;
    private final HttpClient httpClient;

    public NeoForgeVersionProvider() {
        this(DEFAULT_METADATA_URL, INSTALLER_URL_TEMPLATE);
    }

    public NeoForgeVersionProvider(String metadataUrl, String installerUrlTemplate) {
        this(metadataUrl, installerUrlTemplate, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build());
    }

    public NeoForgeVersionProvider(String metadataUrl, String installerUrlTemplate,
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
            throw new IOException("No NeoForge versions found for MC "
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
                throw new IOException("NeoForge Maven returned HTTP "
                        + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching NeoForge versions", e);
        }
    }

    /**
     * Maps a Minecraft version to the NeoForge version-numbering
     * prefix used to filter the Maven metadata:
     * <ul>
     *   <li>{@code 1.20.1} → {@code 47.} (the only version carrying the
     *       legacy Forge build number)</li>
     *   <li>{@code 1.21} → {@code 21.0.}</li>
     *   <li>{@code 1.21.4} → {@code 21.4.}</li>
     * </ul>
     */
    public static String neoforgePrefix(String minecraftVersion) {
        if ("1.20.1".equals(minecraftVersion)) {
            return "47.";
        }
        if (minecraftVersion.startsWith("1.")) {
            String rest = minecraftVersion.substring(2);
            if (rest.indexOf('.') < 0) {
                return rest + ".0.";
            }
            return rest + ".";
        }
        return minecraftVersion + ".";
    }

    /**
     * Inverse of {@link #neoforgePrefix} — maps a NeoForge build
     * number to the Minecraft version it targets:
     * {@code 47.1.104} → {@code 1.20.1}, {@code 21.0.167} →
     * {@code 1.21}, {@code 21.4.147} → {@code 1.21.4}. Returns
     * {@code null} for numbers that encode no known Minecraft
     * version.
     */
    public static String minecraftVersionOf(String neoforgeVersion) {
        String number = neoforgeVersion;
        int dash = number.indexOf('-');
        if (dash >= 0) {
            number = number.substring(0, dash); // strip -beta etc.
        }
        if (number.startsWith("47.")) {
            return "1.20.1";
        }
        String[] parts = number.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        if (!Character.isDigit(parts[0].charAt(0))
                || !Character.isDigit(parts[1].charAt(0))) {
            return null;
        }
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        if (major < 20) {
            return null; // 1.20.1's 47.x is handled above; the oldest
                         // scheme-based build is 20.2 (MC 1.20.2)
        }
        return minor == 0 ? "1." + major : "1." + major + "." + minor;
    }

    /**
     * Parses NeoForge maven-metadata.xml and keeps only versions
     * matching the requested Minecraft version. Exposed for unit
     * testing.
     * <p>
     * The metadata lists versions ascending; the result is reversed so
     * the newest build comes first, and the newest build is flagged as
     * stable.
     */
    public List<ModLoaderVersion> parseVersions(String xml, String minecraftVersion) {
        String prefix = neoforgePrefix(minecraftVersion);
        List<String> matching = new ArrayList<>();

        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) {
            String version = m.group(1).trim();
            if (version.startsWith(prefix)) {
                matching.add(version);
            }
        }

        Collections.reverse(matching);

        List<ModLoaderVersion> result = new ArrayList<>(matching.size());
        for (int i = 0; i < matching.size(); i++) {
            String neoforgeVersion = matching.get(i);
            result.add(new ModLoaderVersion(
                    ModLoaderType.NEOFORGE,
                    neoforgeVersion,
                    minecraftVersion,
                    i == 0,
                    installerUrlTemplate.replace("{v}", neoforgeVersion)));
        }
        return result;
    }

    /**
     * Parses NeoForge maven-metadata.xml and extracts the set of
     * Minecraft versions NeoForge exists for (via
     * {@link #minecraftVersionOf}). Exposed for unit testing.
     */
    public java.util.Set<String> parseSupportedMinecraftVersions(String xml) {
        java.util.Set<String> result = new java.util.HashSet<>();
        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) {
            String mc = minecraftVersionOf(m.group(1).trim());
            if (mc != null) {
                result.add(mc);
            }
        }
        return result;
    }
}
