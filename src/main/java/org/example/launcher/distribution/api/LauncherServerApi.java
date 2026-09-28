package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildFileEntry;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;

/**
 * Клиентская сторона API сервера лаунчера: всё, что нужно обычному
 * (вошедшему) пользователю лаунчера. Серверная часть (обычный
 * HTTPS-сервис) отвечает за проверку bearer-токена при
 * каждом вызове и за то, чтобы отдавать только опубликованные
 * администратором сборки.
 *
 * <p>Лаунчер работает ровно с одной реализацией этого
 * интерфейса; без настроенного сервера это
 * {@link OfflineLauncherServerApi}, и лаунчер остаётся полностью
 * локальным.</p>
 */
public interface LauncherServerApi {

    /**
     * Входит в учётную запись по логину и паролю.
     *
     * <p>Контракт безопасности: учётные данные передаются только внутри этого вызова
     * (по HTTPS на реальном сервере) и никогда не сохраняются —
     * лаунчер хранит возвращённый токен для последующих запросов.</p>
     *
     * @param login    логин учётной записи
     * @param password пароль учётной записи
     * @return авторизованная сессия с токеном и ролью
     * @throws IOException при сетевых ошибках или отклонении учётных данных сервером
     */
    ServerSession login(String login, String password) throws IOException;

    /**
     * Перечисляет опубликованные администратором сборки: новейшая версия
     * каждой сборки первой.
     *
     * @param session вошедшая сессия
     * @return краткие описания сборок, никогда {@code null}
     * @throws IOException при сетевых ошибках или невалидной сессии
     */
    List<BuildSummary> listBuilds(ServerSession session) throws IOException;

    /**
     * Получает полное описание одной сборки — краткое описание плюс
     * список файлов с хешами — для установки и обновлений.
     *
     * @param session вошедшая сессия
     * @param buildId уникальный id сборки
     * @return дескриптор сборки
     * @throws IOException при сетевых ошибках или неизвестной сборке
     */
    BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Скачивает один файл сборки в {@code target}.
     *
     * @param session вошедшая сессия
     * @param build   устанавливаемая сборка
     * @param file    скачиваемая запись
     * @param target  локальный путь назначения, родительские каталоги существуют
     * @throws IOException при сетевых ошибках
     */
    void downloadFile(ServerSession session, BuildDescriptor build, BuildFileEntry file, Path target) throws IOException;
}
