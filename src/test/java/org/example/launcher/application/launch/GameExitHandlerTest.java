package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GameExitHandler")
class GameExitHandlerTest {

    private static Optional<String> currentJava() {
        return ProcessHandle.current().info().command();
    }

    private GameProcess finished(String flag, Path dir) throws Exception {
        String javaExe = currentJava().orElseThrow();
        var manager = new GameProcessManager();
        GameProcess process = manager.start(
                new GameLaunchCommand(List.of(javaExe, flag), dir), "test");
        process.process().waitFor();
        return process;
    }

    @Test
    @DisplayName("exit code 0 means no crash report")
    void cleanExit(@TempDir Path dir) throws Exception {
        if (currentJava().isEmpty()) {
            return;
        }
        var handler = new GameExitHandler();
        GameProcess process = finished("-version", dir);

        assertTrue(handler.handle(process, 0, dir).isEmpty());
    }

    @Test
    @DisplayName("non-zero exit produces a report with the log path")
    void crashExit(@TempDir Path dir) throws Exception {
        if (currentJava().isEmpty()) {
            return;
        }
        var handler = new GameExitHandler();
        GameProcess process = finished("-Xbogusflag", dir);

        Optional<CrashReport> report =
                handler.handle(process, process.exitCode(), dir);

        assertTrue(report.isPresent());
        assertTrue(report.get().fullLog().endsWith("latest.log"));
    }
}
