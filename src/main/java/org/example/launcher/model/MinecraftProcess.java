package org.example.launcher.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Wrapper around the {@link Process} started for Minecraft, providing
 * convenience methods for state queries, output capture, and lifecycle
 * control.
 * <p>
 * Output streams (stdout / stderr) are consumed on background threads
 * to prevent buffer deadlocks when the game produces significant
 * log output.
 */
public final class MinecraftProcess {

    private final Process process;
    private final StringBuilder stdout;
    private final StringBuilder stderr;
    private final Future<Void> stdoutFuture;
    private final Future<Void> stderrFuture;

    public MinecraftProcess(Process process) {
        this.process = process;
        this.stdout = new StringBuilder();
        this.stderr = new StringBuilder();
        this.stdoutFuture = drainAsync(process.getInputStream(), stdout);
        this.stderrFuture = drainAsync(process.getErrorStream(), stderr);
    }

    public long pid() {
        return process.pid();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Blocks until the process exits and returns the exit code.
     */
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    /**
     * Returns the exit code if the process has finished, or
     * {@code -1} if still running.
     */
    public int exitCode() {
        return process.isAlive() ? -1 : process.exitValue();
    }

    /**
     * Forcefully terminates the process.
     */
    public void kill() {
        process.destroyForcibly();
    }

    /**
     * Completes when the process exits, carrying the exit code.
     */
    public CompletableFuture<Integer> onExit() {
        return process.onExit().thenApply(Process::exitValue);
    }

    public String stdout() {
        synchronized (stdout) {
            return stdout.toString();
        }
    }

    public String stderr() {
        synchronized (stderr) {
            return stderr.toString();
        }
    }

    private static Future<Void> drainAsync(InputStream is, StringBuilder buffer) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException e) {
                synchronized (buffer) {
                    buffer.append("[stream error: ").append(e.getMessage()).append("]\n");
                }
            }
        });
    }
}
