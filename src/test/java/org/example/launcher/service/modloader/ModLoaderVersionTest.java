package org.example.launcher.service.modloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.model.ModLoaderVersion;

@DisplayName("ModLoaderVersion")
class ModLoaderVersionTest {

    @Test
    @DisplayName("installedVersionId follows each loader's convention")
    void installedVersionIdConventions() {
        assertEquals("fabric-loader-0.16.9-1.21.4",
                new ModLoaderVersion(ModLoaderType.FABRIC, "0.16.9", "1.21.4",
                        true, null).installedVersionId());

        assertEquals("quilt-loader-0.26.0-1.21.4",
                new ModLoaderVersion(ModLoaderType.QUILT, "0.26.0", "1.21.4",
                        true, null).installedVersionId());

        assertEquals("1.20.1-forge-47.4.10",
                new ModLoaderVersion(ModLoaderType.FORGE, "47.4.10", "1.20.1",
                        true, "https://example.com/forge.jar").installedVersionId());

        assertEquals("neoforge-21.4.147",
                new ModLoaderVersion(ModLoaderType.NEOFORGE, "21.4.147", "1.21.4",
                        true, "https://example.com/neoforge.jar").installedVersionId());
    }

    @Test
    @DisplayName("installerUrlOpt wraps null as empty")
    void installerUrlOpt() {
        Optional<String> none = new ModLoaderVersion(ModLoaderType.FABRIC,
                "0.16.9", "1.21.4", true, null).installerUrlOpt();
        assertFalse(none.isPresent());

        Optional<String> present = new ModLoaderVersion(ModLoaderType.FORGE,
                "47.4.10", "1.20.1", true, "https://example.com/x.jar").installerUrlOpt();
        assertTrue(present.isPresent());
        assertEquals("https://example.com/x.jar", present.get());
    }

    @Test
    @DisplayName("plain accessor returns raw nullable value")
    void rawInstallerUrlAccessor() {
        assertNull(new ModLoaderVersion(ModLoaderType.QUILT, "0.26.0", "1.21.4",
                true, null).installerUrl());
        assertEquals("https://maven.minecraftforge.net/x.jar",
                new ModLoaderVersion(ModLoaderType.FORGE, "47.4.10", "1.20.1",
                        true, "https://maven.minecraftforge.net/x.jar").installerUrl());
    }
}
