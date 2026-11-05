package org.example.launcher.infrastructure.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.domain.model.JavaRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SystemJavaScanner")
class SystemJavaScannerTest {

    private final SystemJavaScanner scanner = new SystemJavaScanner();

    @Test
    @DisplayName("scanInstalledRuntimes finds at least the current JVM")
    void findsCurrentJvm() {
        List<JavaRuntime> runtimes = scanner.scanInstalledRuntimes();

        assertFalse(runtimes.isEmpty(), "Should find at least one Java runtime");
    }

    @Test
    @DisplayName("detected runtimes have valid executable paths")
    void detectedRuntimesHaveValidPaths() {
        List<JavaRuntime> runtimes = scanner.scanInstalledRuntimes();

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
        JavaRuntime rt = scanner.detectFromPath(
                Path.of("non", "existent", "path"), JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("detectFromPath returns null for directory without java executable")
    void detectFromPathWithoutJava(@TempDir Path dir) {
        JavaRuntime rt = scanner.detectFromPath(dir, JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("detectFromPath finds java from java.home system property")
    void detectFromJavaHome() {
        String javaHome = System.getProperty("java.home");
        assertNotNull(javaHome, "java.home should be set");

        JavaRuntime rt = scanner.detectFromPath(Path.of(javaHome), JavaRuntime.Source.JAVA_HOME);
        assertNotNull(rt, "Should detect runtime from java.home");
        assertEquals(JavaRuntime.Source.JAVA_HOME, rt.source());
        assertTrue(Files.isRegularFile(rt.javaExecutable()));
    }

    @Test
    @DisplayName("detectFromPath returns null for null input")
    void detectFromNullPath() {
        JavaRuntime rt = scanner.detectFromPath(null, JavaRuntime.Source.CUSTOM);
        assertNull(rt);
    }

    @Test
    @DisplayName("scanInstalledRuntimes de-duplicates identical paths")
    void deDuplicatesRuntimes() {
        List<JavaRuntime> runtimes = scanner.scanInstalledRuntimes();

        long uniquePaths = runtimes.stream()
                .map(JavaRuntime::javaExecutable)
                .distinct()
                .count();

        assertEquals(runtimes.size(), uniquePaths,
                "No duplicate executable paths should exist");
    }
}
