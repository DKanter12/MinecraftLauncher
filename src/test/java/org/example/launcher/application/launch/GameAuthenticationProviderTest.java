package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.infrastructure.elyby.ElyAuthService;

@DisplayName("GameAuthenticationProvider")
class GameAuthenticationProviderTest {

    private final GameAuthenticationProvider provider =
            new GameAuthenticationProvider(new ElyAuthService());

    @Test
    @DisplayName("offline account gets a deterministic uuid and offline token")
    void offline() {
        AuthData data = provider.getAuthenticationData(
                GameProfile.offline("Steve"));

        assertEquals("Steve", data.username());
        assertEquals(GameAuthenticationProvider.offlineUuid("Steve"),
                data.uuid());
        assertEquals("offline", data.accessToken());
        assertEquals(GameProfile.AuthType.OFFLINE, data.accountType());
    }

    @Test
    @DisplayName("offline uuid is stable for the same nickname")
    void offlineStable() {
        assertEquals(
                GameAuthenticationProvider.offlineUuid("Steve"),
                GameAuthenticationProvider.offlineUuid("Steve"));
        assertFalse(GameAuthenticationProvider.offlineUuid("Steve")
                .equals(GameAuthenticationProvider.offlineUuid("Alex")));
    }

    @Test
    @DisplayName("ely account passes uuid and token through")
    void ely() {
        GameProfile ely = GameProfile.elyBy("Alex", "uuid-1", "token-1",
                "client-1", null, null, "{}");

        AuthData data = provider.getAuthenticationData(ely);

        assertEquals("Alex", data.username());
        assertEquals("uuid-1", data.uuid());
        assertEquals("token-1", data.accessToken());
        assertEquals(GameProfile.AuthType.ELY_BY, data.accountType());
    }

    @Test
    @DisplayName("normalized profile keeps builder-relevant fields")
    void normalized() {
        GameProfile ely = GameProfile.elyBy("Alex", "uuid-1", "token-1",
                "client-1", "https://skin", "classic", "{}");

        GameProfile normalized = provider.getAuthenticationData(ely)
                .toGameProfile();

        assertTrue(normalized.isElyBy());
        assertEquals("Alex", normalized.name());
        assertEquals("uuid-1", normalized.uuid().orElseThrow());
        assertEquals("token-1", normalized.accessToken().orElseThrow());

        GameProfile offline = provider
                .getAuthenticationData(GameProfile.offline("Steve"))
                .toGameProfile();
        assertFalse(offline.isElyBy());
        assertEquals("Steve", offline.name());
    }

    @Test
    @DisplayName("refreshSession keeps offline accounts as is")
    void refreshOffline() {
        GameProfile offline = GameProfile.offline("Steve");

        assertEquals(offline, provider.refreshSession(offline));
    }
}
