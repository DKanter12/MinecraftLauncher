package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.distribution.api.OfflineLauncherServerApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAuthServiceTest {

    @TempDir
    Path tempDir;

    private ServerSession session(UserRole role, String expiresAtRaw) {
        return new ServerSession("tester", role, "token-123", "https://launcher.example",
                expiresAtRaw);
    }

    @Test
    void loginPersistsTokenOnly() throws IOException {
        FakeLauncherServerApi api = new FakeLauncherServerApi();
        api.sessionToReturn = session(UserRole.USER, null);
        ServerAuthService service = new ServerAuthService(api, tempDir.resolve("server_session.json"));

        ServerSession result = service.login("tester", "secret-password");

        assertEquals(UserRole.USER, result.role());
        Path file = tempDir.resolve("server_session.json");
        assertTrue(Files.isRegularFile(file));
        String stored = Files.readString(file);
        assertTrue(stored.contains("token-123"));
        assertTrue(stored.contains("tester"));
        assertFalse(stored.contains("secret-password"), "password must never be persisted");
    }

    @Test
    void restoreSessionRoundTripsAfterRestart() throws IOException {
        FakeLauncherServerApi api = new FakeLauncherServerApi();
        api.sessionToReturn = session(UserRole.ADMIN, null);
        Path file = tempDir.resolve(ServerAuthService.SESSION_FILE_NAME);
        ServerAuthService service = new ServerAuthService(api, file);
        service.login("tester", "secret-password");

        ServerAuthService restarted = new ServerAuthService(api, file);
        Optional<ServerSession> restored = restarted.restoreSession();

        assertTrue(restored.isPresent());
        assertTrue(restored.get().isAdmin());
        assertEquals("token-123", restored.get().token());
        assertEquals("https://launcher.example", restored.get().serverUrl());
    }

    @Test
    void expiredSessionIsNotRestored() throws IOException {
        FakeLauncherServerApi api = new FakeLauncherServerApi();
        String past = OffsetDateTime.ofInstant(Instant.now().minusSeconds(3600), ZoneOffset.UTC).toString();
        api.sessionToReturn = session(UserRole.USER, past);
        Path file = tempDir.resolve(ServerAuthService.SESSION_FILE_NAME);
        ServerAuthService service = new ServerAuthService(api, file);
        service.login("tester", "secret-password");

        Optional<ServerSession> restored = service.restoreSession();

        assertTrue(restored.isEmpty());
        assertFalse(Files.isRegularFile(file), "expired session file should be removed");
    }

    @Test
    void logoutDiscardsSession() throws IOException {
        FakeLauncherServerApi api = new FakeLauncherServerApi();
        api.sessionToReturn = session(UserRole.USER, null);
        Path file = tempDir.resolve(ServerAuthService.SESSION_FILE_NAME);
        ServerAuthService service = new ServerAuthService(api, file);
        service.login("tester", "secret-password");
        assertTrue(Files.isRegularFile(file));

        service.logout();

        assertFalse(Files.isRegularFile(file));
        assertTrue(service.restoreSession().isEmpty());
    }

    @Test
    void offlineApiLoginFailsWithClearMessage() {
        ServerAuthService service = new ServerAuthService(
                new OfflineLauncherServerApi(), tempDir.resolve(ServerAuthService.SESSION_FILE_NAME));

        IOException error = assertThrows(IOException.class,
                () -> service.login("tester", "secret-password"));

        assertTrue(error.getMessage().contains("local mode"));
        assertTrue(service.restoreSession().isEmpty());
    }

    @Test
    void corruptSessionFileIsIgnored() throws IOException {
        Path file = tempDir.resolve(ServerAuthService.SESSION_FILE_NAME);
        Files.writeString(file, "{not json");
        ServerAuthService service = new ServerAuthService(new FakeLauncherServerApi(), file);

        assertTrue(service.restoreSession().isEmpty());
    }
}
