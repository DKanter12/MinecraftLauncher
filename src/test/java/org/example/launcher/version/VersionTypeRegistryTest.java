package org.example.launcher.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.launcher.domain.model.VersionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VersionTypeRegistry")
class VersionTypeRegistryTest {

    @Test
    @DisplayName("is pre-populated with the Mojang display types")
    void prepopulated() {
        VersionTypeRegistry registry = new VersionTypeRegistry();

        assertTrue(registry.getRegisteredTypes().contains(VersionType.RELEASE));
        assertTrue(registry.getRegisteredTypes().contains(VersionType.SNAPSHOT));
        assertTrue(registry.getRegisteredTypes().contains(VersionType.BETA));
        assertTrue(registry.getRegisteredTypes().contains(VersionType.ALPHA));
    }

    @Test
    @DisplayName("findById resolves known ids")
    void findsKnownIds() {
        VersionTypeRegistry registry = new VersionTypeRegistry();

        assertEquals(VersionType.RELEASE, registry.findById("RELEASE"));
        assertEquals(VersionType.SNAPSHOT, registry.findById("SNAPSHOT"));
    }

    @Test
    @DisplayName("findById falls back to UNKNOWN")
    void fallsBackToUnknown() {
        VersionTypeRegistry registry = new VersionTypeRegistry();

        assertEquals(VersionType.UNKNOWN, registry.findById("experimental"));
        assertEquals(VersionType.UNKNOWN, registry.findById(null));
    }

    @Test
    @DisplayName("isStable is true only for release")
    void stabilityFlags() {
        assertTrue(VersionType.RELEASE.isStable());
        assertFalse(VersionType.SNAPSHOT.isStable());
        assertFalse(VersionType.BETA.isStable());
        assertFalse(VersionType.ALPHA.isStable());
    }

    @Test
    @DisplayName("register replaces the mapping for the same name")
    void registerReplaces() {
        VersionTypeRegistry registry = new VersionTypeRegistry();
        int before = registry.getRegisteredTypes().size();

        registry.register(VersionType.SNAPSHOT);

        assertEquals(before, registry.getRegisteredTypes().size());
        assertEquals(VersionType.SNAPSHOT, registry.findById("SNAPSHOT"));
    }
}
