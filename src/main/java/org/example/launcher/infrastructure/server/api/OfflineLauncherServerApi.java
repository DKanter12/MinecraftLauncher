package org.example.launcher.infrastructure.server.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.infrastructure.server.BuildDescriptor;
import org.example.launcher.infrastructure.server.BuildFileEntry;
import org.example.launcher.infrastructure.server.BuildSummary;
import org.example.launcher.infrastructure.server.ServerSession;

/**
 * Реализация по умолчанию, когда сервер лаунчера не настроен:
 * лаунчер работает в чисто локальном режиме, ровно как до появления
 * архитектуры распространения. Локальные сборки,
 * установка и запуск продолжают работать без изменений.
 *
 * <p>Вход завершается понятной ошибкой вместо молчаливого бездействия;
 * каталог сборок просто пуст.</p>
 */
public class OfflineLauncherServerApi implements LauncherServerApi {

    private static final String NOT_CONFIGURED =
            "Launcher server is not configured — the launcher runs in local mode";

    @Override
    public ServerSession login(String login, String password) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }

    @Override
    public List<BuildSummary> listBuilds(ServerSession session) throws IOException {
        if (session == null) {
            throw new IOException(NOT_CONFIGURED);
        }
        return List.of();
    }

    @Override
    public BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }

    @Override
    public void downloadFile(ServerSession session, BuildDescriptor build, BuildFileEntry file, Path target) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }
}
