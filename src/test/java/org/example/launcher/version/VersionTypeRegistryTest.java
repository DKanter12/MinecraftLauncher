package org.example.launcher.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VersionTypeRegistry")
class VersionTypeRegistryTest {

    @Test
    @DisplayName("resolves known standard types by manifest id")
    void resolvesStandardTypes() {
        VersionTypeRegistry registry = new VersionTypeRegistry();

        assertEquals(StandardVersionType.RELEASE, registry.resolve("release"));
        assertEquals(StandardVersionType.SNAPSHOT, registry.resolve("snapshot"));
        assertEquals(StandardVersionType.OLD_BETA, registry.resolve("old_beta"));
        assertEquals(StandardVersionType.OLD_ALPHA, registry.resolve("old_alpha"));
    }

    @Test
    @DisplayName("falls back to UNKNOWN for unrecognised ids")
    void resolvesUnknownType() {
        VersionTypeRegistry registry = new VersionTypeRegistry();
        assertEquals(StandardVersionType.UNKNOWN, registry.resolve("experiment"));
    }

    @Test
    @DisplayName("falls back to UNKNOWN for null ids")
    void resolvesNullType() {
        VersionTypeRegistry registry = new VersionTypeRegistry();
        assertEquals(StandardVersionType.UNKNOWN, registry.resolve(null));
    }

    @Test
    @DisplayName("isStable is true only for release")
    void stabilityFlags() {
        assertTrue(StandardVersionType.RELEASE.isStable());
        assertFalse(StandardVersionType.SNAPSHOT.isStable());
        assertFalse(StandardVersionType.OLD_BETA.isStable());
        assertFalse(StandardVersionType.OLD_ALPHA.isStable());
    }

    @Test
    @DisplayName("registers custom version types that become resolvable")
    void registersCustomType() {
        VersionTypeRegistry registry = new VersionTypeRegistry();

        VersionType experimental = new VersionType() {
            @Override public String id() { return "experimental"; }
            @Override public String displayName() { return "Experimental"; }
            @Override public boolean isStable() { return false; }
        };
        registry.register(experimental);

        assertEquals(experimental, registry.resolve("experimental"));
        assertTrue(registry.all().contains(experimental));
    }

    @Test
    @DisplayName("all() returns every registered type in stable order")
    void allContainsStandardTypes() {
        VersionTypeRegistry registry = new VersionTypeRegistry();
        // at least the 5 standard types
        assertTrue(registry.all().size() >= 5);
        assertEquals(StandardVersionType.RELEASE, registry.all().get(0));
    }
}
