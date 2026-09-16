package org.example.launcher.distribution.api;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.UserRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineLauncherServerApiTest {

    private final OfflineLauncherServerApi api = new OfflineLauncherServerApi();

    private final ServerSession session = new ServerSession(
            "tester", UserRole.ADMIN, "token", "https://launcher.example", null);

    @Test
    void loginFailsWithLocalModeMessage() {
        IOException error = assertThrows(IOException.class, () -> api.login("tester", "pw"));
        assertTrue(error.getMessage().contains("local mode"));
    }

    @Test
    void catalogIsEmptyForSignedInSession() throws IOException {
        assertEquals(List.of(), api.listBuilds(session));
    }

    @Test
    void fetchAndDownloadFail() {
        assertThrows(IOException.class, () -> api.fetchBuild(session, "any"));
        assertThrows(IOException.class, () -> api.downloadFile(session, null, null, null));
    }

    @Test
    void adminFunctionsFailEvenForAdminSession() {
        assertThrows(IOException.class, () -> api.createBuild(session, null));
        assertThrows(IOException.class, () -> api.uploadBuildFile(session, "b", "1", null, "f", null));
        assertThrows(IOException.class, () -> api.publishBuild(session, "b", "1"));
        assertThrows(IOException.class, () -> api.hideBuild(session, "b"));
        assertThrows(IOException.class, () -> api.deleteBuild(session, "b"));
        assertThrows(IOException.class, () -> api.listUsers(session));
    }
}
