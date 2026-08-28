package org.example.launcher.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JavaResolutionResult")
class JavaResolutionResultTest {

    private final JavaRuntime sampleRuntime = new JavaRuntime(
            Path.of("/usr/bin/java"), 17, JavaRuntime.Source.JAVA_HOME);

    @Test
    @DisplayName("found has FOUND status and runtime present")
    void foundResult() {
        JavaResolutionResult result = JavaResolutionResult.found(sampleRuntime);

        assertEquals(JavaResolutionResult.Status.FOUND, result.status());
        assertTrue(result.runtime().isPresent());
        assertTrue(result.reason().isEmpty());
        assertTrue(result.isFound());
    }

    @Test
    @DisplayName("notFound has NOT_FOUND status and reason")
    void notFoundResult() {
        JavaResolutionResult result = JavaResolutionResult.notFound("No Java found");

        assertEquals(JavaResolutionResult.Status.NOT_FOUND, result.status());
        assertTrue(result.runtime().isEmpty());
        assertEquals("No Java found", result.reason().orElseThrow());
        assertFalse(result.isFound());
    }

    @Test
    @DisplayName("incompatible has INCOMPATIBLE status and reason")
    void incompatibleResult() {
        JavaResolutionResult result = JavaResolutionResult.incompatible("Need 17, have 8");

        assertEquals(JavaResolutionResult.Status.INCOMPATIBLE, result.status());
        assertTrue(result.runtime().isEmpty());
        assertEquals("Need 17, have 8", result.reason().orElseThrow());
        assertFalse(result.isFound());
    }
}
