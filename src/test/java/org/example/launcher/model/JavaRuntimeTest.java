package org.example.launcher.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JavaRuntime")
class JavaRuntimeTest {

    @Test
    @DisplayName("satisfies returns true for exact or higher major version")
    void satisfiesExactOrHigher() {
        JavaRuntime rt = new JavaRuntime(Path.of("/java"), 17, JavaRuntime.Source.JAVA_HOME);
        assertTrue(rt.satisfies(17));
        assertTrue(rt.satisfies(8));
        assertTrue(rt.satisfies(11));
    }

    @Test
    @DisplayName("satisfies returns false for higher requirement")
    void doesNotSatisfyHigher() {
        JavaRuntime rt = new JavaRuntime(Path.of("/java"), 8, JavaRuntime.Source.PATH);
        assertFalse(rt.satisfies(11));
        assertFalse(rt.satisfies(17));
    }

    @Test
    @DisplayName("vendor is optional")
    void vendorOptional() {
        JavaRuntime withVendor = new JavaRuntime(Path.of("/java"), 17,
                JavaRuntime.Source.JAVA_HOME, "Eclipse Adoptium");
        JavaRuntime withoutVendor = new JavaRuntime(Path.of("/java"), 17,
                JavaRuntime.Source.JAVA_HOME);

        assertTrue(withVendor.vendor().isPresent());
        assertEquals("Eclipse Adoptium", withVendor.vendor().get());
        assertTrue(withoutVendor.vendor().isEmpty());
    }

    @Test
    @DisplayName("equals and hashCode based on path, majorVersion, and source")
    void equalsAndHashCode() {
        JavaRuntime a = new JavaRuntime(Path.of("/java"), 17, JavaRuntime.Source.JAVA_HOME);
        JavaRuntime b = new JavaRuntime(Path.of("/java"), 17, JavaRuntime.Source.JAVA_HOME, "Vendor");
        JavaRuntime c = new JavaRuntime(Path.of("/java2"), 17, JavaRuntime.Source.JAVA_HOME);

        assertEquals(a, b, "Vendor should not affect equality");
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c), "Different path should not be equal");
    }
}
