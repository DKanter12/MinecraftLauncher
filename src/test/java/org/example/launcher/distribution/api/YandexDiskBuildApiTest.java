package org.example.launcher.distribution.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.DistributionSources;
import org.example.launcher.distribution.RemoteBuildService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.net.UrlFetcher;
import org.example.launcher.net.UrlFetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole Yandex Disk flow against a local stub of the Disk REST
 * API — no internet needed.
 */
class YandexDiskBuildApiTest {

    private static final String TOKEN = "secret";

    @TempDir
    Path tempDir;

    private HttpServer stub;
    private String apiBase;
    private final Map<String, byte[]> files = new HashMap<>();
    private YandexDiskBuildApi api;
    private ServerSession session;

    @BeforeEach
    void setUp() throws IOException {
        files.put("/Builds/Survival/mods/a.jar", "mod-bytes".getBytes(StandardCharsets.UTF_8));
        files.put("/Builds/Survival/config/x.toml", "cfg-bytes".getBytes(StandardCharsets.UTF_8));
        files.put("/Builds/Survival/build.json", """
                {"summary": {"id": "Survival", "version": "2.0.0",
                 "displayName": "Survival Pack", "description": "from yandex",
                 "loaderType": "FORGE", "minecraftVersion": "1.20.1",
                 "loaderVersion": "47.4.23"}}
                """.getBytes(StandardCharsets.UTF_8));
        files.put("/Builds/Old/mods/old.jar", "old-bytes".getBytes(StandardCharsets.UTF_8));
        files.put("/Builds/Empty/readme.txt", "hi".getBytes(StandardCharsets.UTF_8));

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", this::handle);
        stub.start();
        apiBase = "http://127.0.0.1:" + stub.getAddress().getPort() + "/";

        api = new YandexDiskBuildApi(TOKEN, "/Builds",
                new UrlFetcher(), apiBase,
                new com.google.gson.Gson());
        session = api.login("tester", TOKEN);
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    @Test
    void loginHandsOutSession() {
        assertEquals("tester", session.accountName());
        assertEquals("yandex:/Builds", session.serverUrl());
    }

    @Test
    void loginRejectsBlankName() {
        assertThrows(IOException.class, () -> api.login("  ", TOKEN));
    }

    @Test
    void loginRejectsBadToken() {
        assertThrows(IOException.class, () -> api.login("tester", "wrong"));
    }

    @Test
    void catalogDetectsBuildFolders() throws IOException {
        List<BuildSummary> builds = api.listBuilds(session);

        assertEquals(List.of("Old", "Survival"),
                builds.stream().map(BuildSummary::id).sorted().toList());
        BuildSummary survival = builds.stream()
                .filter(b -> b.id().equals("Survival")).findFirst().orElseThrow();
        // the build.json manifest wins over synthesized values
        assertEquals("2.0.0", survival.version());
        assertEquals("Survival Pack", survival.displayName());
        assertEquals("1.20.1", survival.minecraftVersion());
        BuildSummary old = builds.stream()
                .filter(b -> b.id().equals("Old")).findFirst().orElseThrow();
        // synthesized from the folder modification time
        assertEquals("2025.01.01.00.00.00", old.version());
    }

    @Test
    void fetchBuildListsFilesWithHashes() throws IOException {
        BuildDescriptor descriptor = api.fetchBuild(session, "Survival");

        assertEquals("2.0.0", descriptor.version());
        assertEquals(
                List.of("config/x.toml", "mods/a.jar"),
                descriptor.files().stream().map(Object::toString).sorted()
                        .toList());
        assertTrue(descriptor.files().stream().allMatch(
                f -> f.md5() != null || f.sha256() != null));
    }

    @Test
    void installViaRemoteService() throws IOException {
        RemoteBuildService builds = new RemoteBuildService(api);
        Path gameDir = tempDir.resolve("game");

        BuildDescriptor installed = builds.install(session,
                api.listBuilds(session).stream()
                        .filter(b -> b.id().equals("Survival")).findFirst()
                        .orElseThrow(),
                gameDir);

        assertEquals("2.0.0", installed.version());
        assertEquals("mod-bytes", Files.readString(gameDir
                .resolve("builds").resolve("Survival").resolve("mods")
                .resolve("a.jar")));
    }

    @Test
    void newBuildsListsMissingOnes() throws IOException {
        RemoteBuildService builds = new RemoteBuildService(api);
        Path gameDir = tempDir.resolve("game");
        builds.install(session, api.listBuilds(session).stream()
                .filter(b -> b.id().equals("Survival")).findFirst()
                .orElseThrow(), gameDir);

        List<BuildSummary> fresh = builds.newBuilds(session, gameDir);

        assertEquals(List.of("Old"),
                fresh.stream().map(BuildSummary::id).toList());
    }

    @Test
    void versionHelpers() {        assertEquals("1.20.1",
                YandexDiskBuildApi.parseMinecraftVersion("Forge pack 1.20.1"));
        assertEquals("26.1",
                YandexDiskBuildApi.parseMinecraftVersion("26.1"));
        assertEquals(null, YandexDiskBuildApi.parseMinecraftVersion("Nope"));
        assertEquals("2026.09.21.10.00.00", YandexDiskBuildApi
                .modifiedVersion("2026-09-21T10:00:00+00:00"));
        assertEquals("0.0.0", YandexDiskBuildApi.modifiedVersion("junk"));
    }

    @Test
    void publicFolderNeedsNoToken() throws IOException {
        YandexDiskBuildApi pub = new YandexDiskBuildApi("", "/", "https://disk.yandex.ru/d/abc",
                new UrlFetcher(), apiBase,
                new com.google.gson.Gson());
        ServerSession pubSession = pub.login("guest", "");

        List<BuildSummary> builds = pub.listBuilds(pubSession);

        assertEquals(List.of("Old", "Survival"),
                builds.stream().map(BuildSummary::id).sorted().toList());
        BuildDescriptor descriptor = pub.fetchBuild(pubSession, "Survival");
        assertEquals("2.0.0", descriptor.version());
    }

    @Test
    void publicFolderFactory() {
        assertTrue(DistributionSources.createYandexBackend(
                "", "")
                instanceof OfflineLauncherServerApi);
        assertTrue(DistributionSources.createYandexBackend(
                "", "https://disk.yandex.ru/d/abc")
                instanceof YandexDiskBuildApi);
        assertTrue(DistributionSources.createYandexBackend(
                "tok", "/Builds")
                instanceof YandexDiskBuildApi);
    }

    // ------------------------------------------------------------------
    //  Stub Disk API
    // ------------------------------------------------------------------

    private void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getPath();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if (rawPath.startsWith("/files/")) {
            // pre-signed download links need no auth, like the real ones
            byte[] content = files.get(rawPath.substring("/files".length()));
            if (content == null) {
                send(exchange, 404, "nope");
            } else {
                send(exchange, 200, content);
            }
            return;
        }
        boolean isPublicRoute = rawPath.startsWith("/v1/disk/public/");
        if (isPublicRoute) {
            rawPath = "/v1/disk" + rawPath.substring("/v1/disk/public".length());
            String diskPath = query.getOrDefault("path", "");
            query.put("path", diskPath.equals("/")
                    ? "/Builds" : "/Builds" + diskPath);
        } else if (!("OAuth " + TOKEN).equals(
                exchange.getRequestHeaders().getFirst("Authorization"))) {
            send(exchange, 401, "{\"error\": \"unauthorized\"}");
            return;
        }
        if (rawPath.equals("/v1/disk/resources/download")) {
            String diskPath = query.getOrDefault("path", "");
            send(exchange, 200, "{\"href\": \"" + apiBase + "files" + diskPath + "\"}");
            return;
        }
        if (rawPath.equals("/v1/disk/resources")) {
            String diskPath = query.getOrDefault("path", "");
            if (query.getOrDefault("fields", "").equals("modified")) {
                send(exchange, 200, "{\"modified\": \""
                        + modifiedOf(diskPath) + "\"}");
                return;
            }
            if (files.containsKey(diskPath)) {
                String name = diskPath.substring(diskPath.lastIndexOf('/') + 1);
                send(exchange, 200, "{\"name\": \"" + name + "\","
                        + "\"type\": \"file\","
                        + "\"path\": \"disk:" + diskPath + "\"}");
                return;
            }
            String body = listing(diskPath);
            if (body == null) {
                send(exchange, 404, "{\"error\": \"not found\"}");
            } else {
                send(exchange, 200, body);
            }
            return;
        }
        if (rawPath.startsWith("/files/")) {
            byte[] content = files.get(rawPath.substring("/files".length()));
            if (content == null) {
                send(exchange, 404, "nope");
            } else {
                send(exchange, 200, content);
            }
            return;
        }
        send(exchange, 404, "{\"error\": \"nope\"}");
    }

    private String listing(String diskPath) {
        Map<String, List<String>> dirs = new LinkedHashMap<>();
        dirs.put("/Builds", List.of("Survival", "Old", "Empty", "note.txt"));
        dirs.put("/Builds/Survival", List.of("mods", "config", "build.json"));
        dirs.put("/Builds/Survival/mods", List.of("a.jar"));
        dirs.put("/Builds/Survival/config", List.of("x.toml"));
        dirs.put("/Builds/Old", List.of("mods"));
        dirs.put("/Builds/Old/mods", List.of("old.jar"));
        dirs.put("/Builds/Empty", List.of("readme.txt"));
        List<String> children = dirs.get(diskPath);
        if (children == null) {
            return null;
        }
        StringBuilder items = new StringBuilder();
        for (String child : children) {
            String childPath = diskPath + "/" + child;
            boolean dir = dirs.containsKey(childPath);
            if (items.length() > 0) {
                items.append(',');
            }
            items.append("{\"name\": \"").append(child).append("\",")
                    .append("\"type\": \"").append(dir ? "dir" : "file").append("\",")
                    .append("\"path\": \"disk:").append(childPath).append("\",")
                    .append("\"modified\": \"").append(modifiedOf(childPath))
                    .append("\"");
            byte[] content = files.get(childPath);
            if (!dir && content != null) {
                items.append(",\"md5\": \"").append(hex("MD5", content)).append("\",")
                        .append("\"sha256\": \"").append(hex("SHA-256", content)).append("\",")
                        .append("\"size\": ").append(content.length);
            }
            items.append('}');
        }
        return "{\"_embedded\": {\"total\": " + children.size()
                + ", \"offset\": 0, \"items\": [" + items + "]}}";
    }

    private String modifiedOf(String diskPath) {
        if (diskPath.equals("/Builds/Old") || diskPath.startsWith("/Builds/Old/")) {
            return "2025-01-01T00:00:00+00:00";
        }
        return "2026-09-21T10:00:00+00:00";
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(pair.substring(0, eq), URLDecoder.decode(
                        pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static void send(HttpExchange exchange, int code, String body)
            throws IOException {
        send(exchange, code, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int code, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String hex(String algorithm, byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
