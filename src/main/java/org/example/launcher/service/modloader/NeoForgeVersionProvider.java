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
 * lists all NeoForge builds. Every build number encodes its target
 * Minecraft version (see {@link #minecraftVersionOf}), so entries are
 * filtered by exact decode and every returned version is compatible
 * by construction.
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
     * Maps a NeoForge build number to the Minecraft version it
     * targets — both numbering schemes NeoForge has used:
     * <ul>
     *   <li>legacy 3-component {@code {major}.{minor}.{build}} for
     *       the MC {@code 1.x} era: {@code 47.1.104} →
     *       {@code 1.20.1} (the only version carrying the old Forge
     *       build number), {@code 21.0.167} → {@code 1.21},
     *       {@code 21.4.147} → {@code 1.21.4}</li>
     *   <li>modern 4-component {@code {mc…}.{build}} for the MC
     *       {@code 26.x} era: {@code 26.1.0.19} → {@code 26.1} (a
     *       {@code .0} patch component marks the 2-part MC version),
     *       {@code 26.1.2.101} → {@code 26.1.2},
     *       {@code 26.2.0.3} → {@code 26.2}</li>
     * </ul>
     * Returns {@code null} for numbers that encode no known Minecraft
     * version — including odd special builds like
     * {@code 0.25w14craftmine.3-beta} that occasionally appear in the
     * Maven metadata.
     */
    public static String minecraftVersionOf(String neoforgeVersion) {
        String number = neoforgeVersion;
        // Strip qualifiers: "-beta", "-rc", "+snapshot-1" after
        // "alpha.N", … — whichever comes first
        int cut = number.length();
        int dash = number.indexOf('-');
        int plus = number.indexOf('+');
        if (dash >= 0) cut = Math.min(cut, dash);
        if (plus >= 0) cut = Math.min(cut, plus);
        number = number.substring(0, cut);

        if (number.startsWith("47.")) {
            return "1.20.1";
        }
        String[] parts = number.split("\\.");
        if (parts.length < 2 || parts.length > 4) {
            return null;
        }
        int major;
        int minor;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null; // e.g. "0.25w14craftmine.3"
        }
        if (major == 20 || major == 21) {
            // Legacy scheme (MC 1.20–1.21): 1.{major}.{minor}
            return minor == 0 ? "1." + major
                    : "1." + major + "." + minor;
        }
        if (major >= 22 && parts.length >= 3) {
            // Modern scheme (MC 26.x): every component but the last
            // (the build) forms the MC version; a ".0" patch marks
            // the 2-part MC version (26.1.0.x → 26.1)
            String[] mcParts = new String[parts.length - 1];
            System.arraycopy(parts, 0, mcParts, 0, mcParts.length);
            if (mcParts.length == 3 && "0".equals(mcParts[2])) {
                return mcParts[0] + "." + mcParts[1];
            }
            return String.join(".", mcParts);
        }
        return null;
    }

    /**
     * Parses NeoForge maven-metadata.xml and keeps only builds whose
     * decoded target Minecraft version equals the requested one.
     * Exposed for unit testing.
     * <p>
     * The metadata lists versions ascending; the result is reversed so
     * the newest build comes first, and the newest build is flagged as
     * stable.
     */
    public List<ModLoaderVersion> parseVersions(String xml, String minecraftVersion) {
        List<String> matching = new ArrayList<>();

        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) {
            String version = m.group(1).trim();
            if (minecraftVersion.equals(minecraftVersionOf(version))) {
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
