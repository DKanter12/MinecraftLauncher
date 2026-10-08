package org.example.launcher.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.application.account.AccountManager;
import org.example.launcher.infrastructure.filesystem.ProfileService;

@DisplayName("AccountManager")
class AccountManagerTest {

    private AccountManager manager(Path tempDir) {
        GameDirectory gameDir = new GameDirectory(tempDir);
        return new AccountManager(new ProfileService(gameDir.profilesFile()));
    }

    @Test
    @DisplayName("createOffline validates nicknames like the dialog")
    void validatesOfflineName(@TempDir Path tempDir) throws Exception {
        AccountManager mgr = manager(tempDir);
        assertThrows(IllegalArgumentException.class, () -> mgr.createOffline("  "));
        assertThrows(IllegalArgumentException.class,
                () -> mgr.createOffline("way-too-long-nickname"));
        assertThrows(IllegalArgumentException.class, () -> mgr.createOffline("bad nick!"));
        GameProfile created = mgr.createOffline("Steve_01");
        assertEquals("Steve_01", created.name());
    }

    @Test
    @DisplayName("duplicate offline name replaces, Ely dedups by uuid")
    void deduplicates(@TempDir Path tempDir) throws Exception {
        AccountManager mgr = manager(tempDir);
        mgr.createOffline("Steve");
        mgr.createOffline("Steve");
        assertEquals(1, mgr.load().size());

        GameProfile ely = GameProfile.elyBy("Alex", "uuid-1", "token",
                "client", null, null, null);
        mgr.saveAuthenticated(ely);
        GameProfile elyRenamed = GameProfile.elyBy("AlexNew", "uuid-1", "token2",
                "client", null, null, null);
        mgr.saveAuthenticated(elyRenamed);
        assertEquals(2, mgr.load().size());
        assertTrue(mgr.load().stream().anyMatch(p -> p.name().equals("AlexNew")));
    }

    @Test
    @DisplayName("resolveSelection prefers uuid, then saved name, then first")
    void resolvesSelection(@TempDir Path tempDir) throws Exception {
        AccountManager mgr = manager(tempDir);
        GameProfile a = GameProfile.offline("A");
        GameProfile b = GameProfile.elyBy("B", "uuid-b", "t", "c", null, null, null);
        List<GameProfile> all = List.of(a, b);
        assertEquals("B", mgr.resolveSelection(all, "uuid-b", "A").orElseThrow().name());
        assertEquals("A", mgr.resolveSelection(all, null, "A").orElseThrow().name());
        assertEquals("A", mgr.resolveSelection(all, null, null).orElseThrow().name());
        assertTrue(mgr.resolveSelection(List.of(), null, null).isEmpty());
    }

    @Test
    @DisplayName("getOrCreateDefault falls back to Player")
    void defaultProfile(@TempDir Path tempDir) throws Exception {
        AccountManager mgr = manager(tempDir);
        GameProfile def = mgr.getOrCreateDefault(null);
        assertEquals("Player", def.name());
        assertEquals("Player", mgr.getOrCreateDefault(null).name());
    }
}

