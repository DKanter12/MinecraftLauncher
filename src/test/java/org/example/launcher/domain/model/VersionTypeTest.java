package org.example.launcher.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VersionType")
class VersionTypeTest {

    @Test
    @DisplayName("fromMojangId maps all known Mojang types")
    void mapsKnownTypes() {
        assertEquals(VersionType.RELEASE, VersionType.fromMojangId("release"));
        assertEquals(VersionType.SNAPSHOT, VersionType.fromMojangId("snapshot"));
        assertEquals(VersionType.BETA, VersionType.fromMojangId("old_beta"));
        assertEquals(VersionType.ALPHA, VersionType.fromMojangId("old_alpha"));
    }

    @Test
    @DisplayName("fromMojangId falls back to UNKNOWN without exceptions")
    void fallsBackToUnknown() {
        assertEquals(VersionType.UNKNOWN, VersionType.fromMojangId(null));
        assertEquals(VersionType.UNKNOWN, VersionType.fromMojangId(""));
        assertEquals(VersionType.UNKNOWN, VersionType.fromMojangId("modded-fabric"));
        assertEquals(VersionType.UNKNOWN, VersionType.fromMojangId("RELEASE"));
    }

    @Test
    @DisplayName("only RELEASE is stable")
    void stability() {
        assertTrue(VersionType.RELEASE.isStable());
        assertFalse(VersionType.SNAPSHOT.isStable());
        assertFalse(VersionType.UNKNOWN.isStable());
    }
}
