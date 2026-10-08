package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.infrastructure.elyby.ElyAuthService;

@DisplayName("ElyByAuthenticator")
class ElyByAuthenticatorTest {

    @Test
    @DisplayName("offline accounts pass through untouched")
    void offlinePassthrough() {
        var authenticator = new ElyByAuthenticator(new ElyAuthService());
        GameProfile offline = GameProfile.offline("Steve");

        assertEquals(offline, authenticator.authenticate(offline));
        assertEquals(offline, authenticator.refresh(offline));
    }
}
