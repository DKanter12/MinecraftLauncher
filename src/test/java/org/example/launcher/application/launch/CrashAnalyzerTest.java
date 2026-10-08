package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.ModLoaderType;

@DisplayName("CrashAnalyzer")
class CrashAnalyzerTest {

    @Test
    @DisplayName("loader mismatch log gives WRONG_LOADER with suggestion")
    void mismatch() {
        String log = "---- Minecraft Crash Report ----\n"
                + "net.minecraftforge.fml.ModLoadingException: "
                + "Mod has failed to load correctly\n"
                + "at something.elseSON";
        CrashReport report = CrashAnalyzer.analyze(1, log, "");

        assertEquals(CrashCategory.WRONG_LOADER, report.category());
        assertEquals(1, report.exitCode());
        assertTrue(report.suggestion().isPresent());
        assertTrue(report.reason().contains("failed to load correctly"));
    }

    @Test
    @DisplayName("OutOfMemoryError gives OUT_OF_MEMORY")
    void outOfMemory() {
        CrashReport report = CrashAnalyzer.analyze(1, "",
                "Exception: java.lang.OutOfMemoryError: Java heap space");

        assertEquals(CrashCategory.OUT_OF_MEMORY, report.category());
        assertTrue(report.suggestion().isPresent());
    }

    @Test
    @DisplayName("UnsupportedClassVersionError gives WRONG_JAVA")
    void wrongJava() {
        CrashReport report = CrashAnalyzer.analyze(1,
                "java.lang.UnsupportedClassVersionError: "
                        + "net/minecraft/client/main/Main has been compiled by "
                        + "a more recent version (65.0)",
                "");

        assertEquals(CrashCategory.WRONG_JAVA, report.category());
        assertTrue(report.suggestion().isPresent());
    }

    @Test
    @DisplayName("plain log gives UNKNOWN without suggestion")
    void unknown() {
        CrashReport report = CrashAnalyzer.analyze(42, "just some output", "");

        assertEquals(CrashCategory.UNKNOWN, report.category());
        assertEquals(42, report.exitCode());
        assertTrue(report.suggestion().isEmpty());
        assertEquals("just some output", report.logTail());
    }

    @Test
    @DisplayName("null and blank logs give UNKNOWN with empty tail")
    void emptyLogs() {
        assertEquals("", CrashAnalyzer.analyze(1, null, null).logTail());
        assertEquals(CrashCategory.UNKNOWN,
                CrashAnalyzer.analyze(1, "   ", "\t").category());
    }

    @Test
    @DisplayName("log tail is limited to 30 lines")
    void tailLimit() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            log.append("line-").append(i).append('\n');
        }
        String tail = CrashAnalyzer.analyze(1, log.toString(), "").logTail();

        assertEquals(30, tail.lines().count());
        assertTrue(tail.startsWith("line-20"));
    }

    @Test
    @DisplayName("suggestOlderLoader steps one version back")
    void fallbackSuggestion() {
        var versions = List.of(
                new ModLoaderVersion(ModLoaderType.FABRIC, "0.16.9", "1.21.4",
                        true, null),
                new ModLoaderVersion(ModLoaderType.FABRIC, "0.15.0", "1.21.4",
                        false, null));

        Optional<ModLoaderVersion> next =
                CrashAnalyzer.suggestOlderLoader(versions, "0.16.9");

        assertTrue(next.isPresent());
        assertEquals("0.15.0", next.get().loaderVersion());
        assertTrue(CrashAnalyzer.suggestOlderLoader(versions, "0.15.0").isEmpty());
    }
}
