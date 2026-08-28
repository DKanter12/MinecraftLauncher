package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.model.JavaRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SystemJavaDetector")
class SystemJavaDetectorTest {

    private final SystemJavaDetector detector = new SystemJavaDetector();

    @Test
    @DisplayName("detectInstalledRuntimes finds at least the current JVM")
    void findsCurrentJvm() {
        List<JavaRuntime> runtimes = detector.detectInstalledRuntimes();

        assertFalse(runtimes.isEmpty(), "Should find at least one Java runtime");
    }

    @Test
    @DisplayName("detected runtimes have valid executable paths")
    void detectedRuntimesHaveValidPaths() {
        List<JavaRuntime> runtimes = detector.detectInstalledRuntimes();

        for (JavaRuntime rt : runtimes) {
            assertTrue(Files.isRegularFile(rt.javaExecutable()),
                    "Executable should exist: " + rt.javaExecutable());
            assertTrue(rt.majorVersion() > 0, "Major version should be positive");
            assertNotNull(rt.source(), "Source should be set");
        }
    }

    @Test
    @DisplayName("detectFromPath returns null for non-existent directory")
    void detectFromNonExistentPath() {
        JavaRuntime rt = detector.detectFromPath(
                Path.of("non", "existent", "path"), JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("detectFromPath returns null for directory without java executable")
    void detectFromPathWithoutJava(@TempDir Path dir) {
        JavaRuntime rt = detector.detectFromPath(dir, JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("detectFromPath finds java from java.home system property")
    void detectFromJavaHome() {
        String javaHome = System.getProperty("java.home");
        assertNotNull(javaHome, "java.home should be set");

        JavaRuntime rt = detector.detectFromPath(Path.of(javaHome), JavaRuntime.Source.JAVA_HOME);
        assertNotNull(rt, "Should detect runtime from java.home");
        assertEquals(JavaRuntime.Source.JAVA_HOME, rt.source());
        assertTrue(Files.isRegularFile(rt.javaExecutable()));
    }

    @Test
    @DisplayName("detectFromPath returns null for null input")
    void detectFromNullPath() {
        JavaRuntime rt = detector.detectFromPath(null, JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("detectInstalledRuntimes de-duplicates identical paths")
    void deuplicatesRuntimes() {
        List<JavaRuntime> runtimes = detector.detectInstalledRuntimes();

        long uniquePaths = runtimes.stream()
                .map(JavaRuntime::javaExecutable)
                .distinct()
                .count();

        assertEquals(runtimes.size(), uniquePaths,
                "No duplicate executable paths should exist");
    }
}
