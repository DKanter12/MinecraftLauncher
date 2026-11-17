package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GameProcessManager")
class GameProcessManagerTest {

    private static Optional<String> currentJava() {
        return ProcessHandle.current().info().command();
    }

    @Test
    @DisplayName("start runs the process in the build directory")
    void startsProcess(@TempDir Path dir) throws Exception {
        Optional<String> javaExe = currentJava();
        if (javaExe.isEmpty()) {
            return;
        }
        var manager = new GameProcessManager();
        var command = new GameLaunchCommand(
                List.of(javaExe.get(), "-version"), dir);

        GameProcess process = manager.start(command, "test-build");

        try {
            assertTrue(process.isRunning() || process.exitCode() == 0);
            int code = process.process().waitFor();
            assertEquals(0, code);
            assertEquals("test-build", process.profileId());
            assertTrue(process.startedAt() != null);
        } finally {
            if (process.isRunning()) {
                process.process().kill();
            }
        }
    }

    @Test
    @DisplayName("start of a missing executable fails with IOException")
    void missingExecutable(@TempDir Path dir) {
        var manager = new GameProcessManager();
        var command = new GameLaunchCommand(
                List.of(dir.resolve("no-such-java").toString()), dir);

        assertThrows(IOException.class,
                () -> manager.start(command, "test-build"));
    }
}
