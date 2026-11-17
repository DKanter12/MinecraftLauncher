package org.example.launcher.domain.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Обёртка вокруг {@link Process}, запущенного для Minecraft, с
 * удобными методами опроса состояния, захвата вывода и управления
 * жизненным циклом.
 * <p>
 * Потоки вывода (stdout / stderr) читаются в фоновых потоках,
 * чтобы избежать взаимной блокировки буферов при значительном
 * объёме журналов игры.
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
     * Блокируется до завершения процесса и возвращает код выхода.
     */
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    /**
     * Возвращает код выхода, если процесс завершён, или
     * {@code -1}, если он ещё выполняется.
     */
    public int exitCode() {
        return process.isAlive() ? -1 : process.exitValue();
    }

    /**
     * Принудительно завершает процесс.
     */
    public void kill() {
        process.destroyForcibly();
    }

    /**
     * Завершается при выходе процесса, неся код выхода.
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
