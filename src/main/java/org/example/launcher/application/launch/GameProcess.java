package org.example.launcher.application.launch;

import java.time.Instant;
import java.util.Objects;

import org.example.launcher.domain.model.MinecraftProcess;

/**
 * Запущенный процесс Minecraft: сам процесс, сборка-источник,
 * время старта и код завершения после выхода.
 */
public class GameProcess {

    private final MinecraftProcess process;
    private final String profileId;
    private final Instant startedAt;

    public GameProcess(MinecraftProcess process, String profileId) {
        this(process, profileId, Instant.now());
    }

    public GameProcess(MinecraftProcess process, String profileId,
                       Instant startedAt) {
        this.process = Objects.requireNonNull(process, "process");
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    }

    /** Жив ли процесс сейчас. */
    public boolean isRunning() {
        return process.isAlive();
    }

    /** Код выхода либо {@code -1}, если ещё выполняется. */
    public int exitCode() {
        return process.exitCode();
    }

    /** Накопленный stdout. */
    public String stdout() {
        return process.stdout();
    }

    /** Накопленный stderr. */
    public String stderr() {
        return process.stderr();
    }

    /** Id сборки, из которой запущен. */
    public String profileId() {
        return profileId;
    }

    /** Время старта. */
    public Instant startedAt() {
        return startedAt;
    }

    MinecraftProcess process() {
        return process;
    }
}
