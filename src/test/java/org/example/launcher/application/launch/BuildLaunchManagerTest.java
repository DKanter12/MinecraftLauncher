package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.loaders.ModdedProfileVerificationService.VerificationReport;

@DisplayName("BuildLaunchManager decisions")
class BuildLaunchManagerTest {

    private static VersionMetadata meta(int javaMajor) {
        return new VersionMetadata("1.21.4", "release",
                "net.minecraft.client.main.Main", null, null,
                new JavaVersion("java-runtime-gamma", javaMajor), null,
                List.of(), List.of(), List.of(), null);
    }

    private static VerificationReport report(boolean ok, List<String> errors,
                                             VersionMetadata meta) {
        return new VerificationReport(ok, errors, List.of(),
                Optional.ofNullable(meta));
    }

    @Test
    @DisplayName("only missing Java 8 runtime leads to INSTALL_JAVA8")
    void installsJava8() {
        var report = report(false,
                List.of("No suitable Java runtime (need 8)"), meta(8));

        assertEquals(LaunchDecision.INSTALL_JAVA8,
                BuildLaunchManager.decide(report, false, false));
    }

    @Test
    @DisplayName("second attempt after Java install goes to REPAIR")
    void repairsAfterJavaAttempt() {
        var report = report(false,
                List.of("No suitable Java runtime (need 8)"), meta(8));

        assertEquals(LaunchDecision.REPAIR,
                BuildLaunchManager.decide(report, false, true));
    }

    @Test
    @DisplayName("mixed errors skip Java install and go to REPAIR")
    void mixedErrorsRepair() {
        var report = report(false,
                List.of("No suitable Java runtime (need 8)", "client JAR (hash mismatch)"),
                meta(8));

        assertEquals(LaunchDecision.REPAIR,
                BuildLaunchManager.decide(report, false, false));
    }

    @Test
    @DisplayName("modern Java without runtime goes straight to REPAIR")
    void modernJavaRepairs() {
        var report = report(false,
                List.of("No suitable Java runtime (need 17)"), meta(17));

        assertEquals(LaunchDecision.REPAIR,
                BuildLaunchManager.decide(report, false, false));
    }

    @Test
    @DisplayName("unresolvable metadata fails immediately")
    void unresolvableFails() {
        var report = report(false, List.of("version json missing"), null);

        assertEquals(LaunchDecision.FAIL,
                BuildLaunchManager.decide(report, false, false));
    }

    @Test
    @DisplayName("already repaired failure is terminal")
    void repairedFails() {
        var report = report(false, List.of("client JAR (hash mismatch)"),
                meta(17));

        assertEquals(LaunchDecision.FAIL,
                BuildLaunchManager.decide(report, true, false));
    }

    @Test
    @DisplayName("needsJava8 matches legacy rule")
    void java8Rule() {
        assertTrue(BuildLaunchManager.needsJava8(meta(8)));
        assertFalse(BuildLaunchManager.needsJava8(meta(17)));
        assertTrue(BuildLaunchManager.needsJava8(new VersionMetadata("old", "release",
                "Main", null, null, null, null,
                List.of(), List.of(), List.of(), null)));
    }
}
