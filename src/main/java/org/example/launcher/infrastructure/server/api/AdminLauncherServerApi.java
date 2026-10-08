package org.example.launcher.infrastructure.server.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.infrastructure.server.BuildFileCategory;
import org.example.launcher.infrastructure.server.BuildSummary;
import org.example.launcher.infrastructure.server.ServerSession;

/**
 * Административная часть API сервера лаунчера. Надмножество над
 * {@link LauncherServerApi}: поверхность, которую лаунчер администратора
 * использует для создания, загрузки, публикации и снятия сборок.
 *
 * <p>И лаунчер, и сервер принудительно требуют роль ADMIN —
 * сессия обычного пользователя никогда не доходит до этих функций.</p>
 */
public interface AdminLauncherServerApi extends LauncherServerApi {

    /**
     * Регистрирует новую сборку (или новую версию существующего id сборки)
     * на сервере, в состоянии черновика.
     *
     * @param session ADMIN-сессия
     * @param draft   id, версия, имена и координаты Minecraft/загрузчика
     *                сборки
     * @return сохранённое описание, как его принял сервер
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    BuildSummary createBuild(ServerSession session, BuildSummary draft) throws IOException;

    /**
     * Загружает один файл черновой версии сборки.
     *
     * @param session      ADMIN-сессия
     * @param buildId      уникальный id сборки
     * @param version      черновая версия, к которой относится файл
     * @param category     к какой части сборки относится файл
     * @param relativePath путь внутри папки категории
     * @param file         локальный файл для загрузки
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    void uploadBuildFile(ServerSession session, String buildId, String version,
                         BuildFileCategory category, String relativePath, Path file) throws IOException;

    /**
     * Публикует черновую версию сборки: с этого момента обычные пользователи
     * видят её в своём списке сборок.
     *
     * @param session ADMIN-сессия
     * @param buildId уникальный id сборки
     * @param version публикуемая версия
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    void publishBuild(ServerSession session, String buildId, String version) throws IOException;

    /**
     * Скрывает сборку от обычных пользователей без её удаления.
     *
     * @param session ADMIN-сессия
     * @param buildId уникальный id сборки
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    void hideBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Полностью удаляет сборку с сервера.
     *
     * @param session ADMIN-сессия
     * @param buildId уникальный id сборки
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    void deleteBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Перечисляет пользователей, зарегистрированных на сервере лаунчера, для
     * администрирования.
     *
     * @param session ADMIN-сессия
     * @return логины с ролями, никогда {@code null}
     * @throws IOException при сетевых ошибках или если сессия не ADMIN
     */
    List<String> listUsers(ServerSession session) throws IOException;
}
