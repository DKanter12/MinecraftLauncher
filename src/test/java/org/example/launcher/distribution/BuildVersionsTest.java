package org.example.launcher.distribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildVersionsTest {

    @Test
    void comparesNumericSegments() {
        assertTrue(BuildVersions.compare("1.1.0", "1.0.0") > 0);
        assertTrue(BuildVersions.compare("1.0.0", "1.1.0") < 0);
        assertEquals(0, BuildVersions.compare("1.0.0", "1.0.0"));
    }

    @Test
    void longerVersionIsNewerThanSharedPrefix() {
        assertTrue(BuildVersions.compare("1.0.1", "1.0") > 0);
        assertTrue(BuildVersions.compare("1.0", "1.0.1") < 0);
        assertEquals(0, BuildVersions.compare("1.0", "1.0.0"));
    }

    @Test
    void comparesMultiDigitSegmentsNumerically() {
        assertTrue(BuildVersions.compare("1.10.0", "1.9.0") > 0);
        assertTrue(BuildVersions.compare("2.0.0", "1.99.99") > 0);
    }

    @Test
    void comparesNonNumericSegmentsLexicographically() {
        assertTrue(BuildVersions.compare("1.0.0-rc2", "1.0.0-rc1") > 0);
        assertEquals(0, BuildVersions.compare("1.0.0-rc1", "1.0.0-rc1"));
    }

    @Test
    void toleratesBlankVersions() {
        assertTrue(BuildVersions.compare("1.0.0", "") > 0);
        assertTrue(BuildVersions.compare("", "1.0.0") < 0);
        assertEquals(0, BuildVersions.compare(null, "  "));
    }

    @Test
    void isNewerDrivesUpdateDetection() {
        assertTrue(BuildVersions.isNewer("1.1.0", "1.0.0"));
        assertFalse(BuildVersions.isNewer("1.0.0", "1.0.0"));
        assertFalse(BuildVersions.isNewer("1.0.0", "1.1.0"));
    }
}
