package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.DistributionSources;
import org.example.launcher.distribution.RemoteBuildService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.UserRole;
import org.example.launcher.net.UrlFetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubBuildApiTest {

    @TempDir
    Path tempDir;

    private Path repo;
    private String baseUrl;
    private GitHubBuildApi api;
    private ServerSession session;

    @BeforeEach
    void setUp() throws IOException {
        repo = tempDir.resolve("repo");
        write("builds/files/survival/1.1.0/mods/a.jar", "mod-bytes");
        write("builds/files/survival/1.1.0/config/x.toml", "cfg-bytes");
        String modSha = sha1("mod-bytes");
        String cfgSha = sha1("cfg-bytes");
        write("builds/catalog.json", """
                {"builds": [
                  {"id": "survival", "version": "1.1.0",
                   "displayName": "Survival", "description": "d",
                   "loaderType": "FABRIC", "minecraftVersion": "1.21.4",
                   "loaderVersion": "0.16.9"},
                  {"id": "broken", "version": "1.0.0",
                   "displayName": "Broken", "loaderType": "NOPE",
                   "minecraftVersion": "1.21.4"}
                ]}
                """);
        write("builds/survival.json", """
                {"summary":
                  {"id": "survival", "version": "1.1.0",
                   "displayName": "Survival", "description": "d",
                   "loaderType": "FABRIC", "minecraftVersion": "1.21.4",
                   "loaderVersion": "0.16.9"},
                 "files": [
                  {"relativePath": "a.jar", "category": "MODS",
                   "sha1": "%s", "size": 9},
                  {"relativePath": "x.toml", "category": "CONFIGS",
                   "sha1": "%s", "size": 9}
                 ]}
                """.formatted(modSha, cfgSha));

        baseUrl = repo.toUri().toString();
        api = new GitHubBuildApi(baseUrl,
                new UrlFetcher());
        session = api.login("tester", "");
    }

    @Test
    void loginValidatesAndHandsOutUserSession() {
        assertEquals("tester", session.accountName());
        assertEquals(UserRole.USER, session.role());
    }

    @Test
    void loginRejectsBlankName() {
        assertThrows(IOException.class, () -> api.login("  ", ""));
    }

    @Test
    void catalogSkipsMalformedEntries() throws IOException {
        List<BuildSummary> builds = api.listBuilds(session);

        assertEquals(1, builds.size());
        assertEquals("survival", builds.get(0).id());
        assertEquals("1.1.0", builds.get(0).version());
    }

    @Test
    void fetchBuildReadsDescriptor() throws IOException {
        BuildDescriptor descriptor = api.fetchBuild(session, "survival");

        assertEquals("Survival", descriptor.summary().displayName());
        assertEquals(2, descriptor.files().size());
    }

    @Test
    void fetchUnknownBuildFails() {
        assertThrows(IOException.class,
                () -> api.fetchBuild(session, "ghost"));
    }

    @Test
    void remoteServiceInstallsFromGit() throws IOException {
        RemoteBuildService builds =
                new RemoteBuildService(api);
        Path gameDir = tempDir.resolve("game");

        BuildDescriptor installed = builds.install(session,
                api.listBuilds(session).get(0), gameDir);

        assertEquals("1.1.0", installed.version());
        assertEquals("mod-bytes", Files.readString(gameDir
                .resolve("builds").resolve("survival").resolve("mods")
                .resolve("a.jar")));
    }

    @Test
    void factorySelectsBackend() {
        assertTrue(DistributionSources.createBuildsBackend(
                "") instanceof OfflineLauncherServerApi);
        assertTrue(DistributionSources.createBuildsBackend(
                "  ") instanceof OfflineLauncherServerApi);
        assertTrue(DistributionSources.createBuildsBackend(
                baseUrl) instanceof GitHubBuildApi);
    }

    private void write(String relative, String content) throws IOException {
        Path target = repo.resolve(relative.replace("/", java.io.File.separator));
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
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
