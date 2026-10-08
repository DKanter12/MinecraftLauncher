package org.example.launcher.application.launch;

import java.io.IOException;
import java.util.Objects;

/**
 * Создаёт процесс Minecraft с рабочей директорией сборки.
 * Благодаря этому у каждой сборки свои моды, миры, конфиги и логи,
 * а общие файлы Minecraft переиспользуются.
 */
public class GameProcessManager {

    public GameProcessManager() {
    }

    /**
     * Стартует процесс и возвращает обёртку.
     * Мгновенное падение обнаруживает монитор по коду выхода.
     */
    public GameProcess start(GameLaunchCommand command, String profileId)
            throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(profileId, "profileId");
        try {
            Process process = new ProcessBuilder(command.fullCommand())
                    .directory(command.workingDirectory().toFile())
                    .start();
            return new GameProcess(
                    new org.example.launcher.domain.model.MinecraftProcess(
                            process),
                    profileId);
        } catch (IOException e) {
            throw new IOException(
                    "Failed to start game process: " + e.getMessage(), e);
        }
    }
}
