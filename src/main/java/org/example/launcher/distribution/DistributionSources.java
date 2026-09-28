package org.example.launcher.distribution;

import org.example.launcher.distribution.api.GitHubBuildApi;
import org.example.launcher.distribution.api.LauncherServerApi;
import org.example.launcher.distribution.api.OfflineLauncherServerApi;
import org.example.launcher.distribution.api.YandexDiskBuildApi;
import org.example.launcher.net.UrlFetcher;

/**
 * Два репозитория GitHub, с которыми связан лаунчер, плюс фабрики,
 * которые их подключают.
 * <ul>
 *   <li><b>Репозиторий лаунчера</b> — собственные исходники лаунчера. Каждый
 *       запуск (при наличии интернета) проверяет его ветку main на более новый
 *       {@code launcher-version.json} и скачивает обновление —
 *       только файлы лаунчера, никогда файлы Minecraft или данные пользователя.</li>
 *   <li><b>Репозиторий сборок</b> — сборки администратора, публикуемые
 *       отправкой файлов (каталог + дескрипторы + содержимое, см.
 *       {@link GitHubBuildApi}). Ссылка задаётся в настройках
 *       и приходит отдельно; пока она пуста, лаунчер остаётся в
 *       локальном режиме.</li>
 * </ul>
 */
public final class DistributionSources {

    /** Собственный репозиторий лаунчера. */
    public static final String LAUNCHER_REPO_URL =
            "https://github.com/DKanter12/MinecraftLauncher.git";

    /** Манифест обновлений в ветке main {@link #LAUNCHER_REPO_URL}. */
    public static final String UPDATE_MANIFEST_URL =
            "https://raw.githubusercontent.com/DKanter12/MinecraftLauncher/main/launcher-version.json";

    /** Общая папка Яндекс Диска со сборками администратора по умолчанию. */
    public static final String YANDEX_BUILDS_LINK_DEFAULT =
            "https://disk.yandex.ru/d/TiaArgV_VfAmQA";

    /** Откуда берутся распространяемые сборки. */
    public enum BuildsSource {
        LOCAL, GITHUB, YANDEX
    }

    private DistributionSources() {
    }

    /**
     * Создаёт бэкенд сборок: на основе git, если задана ссылка на репозиторий
     * (Настройки), иначе обычный локальный режим.
     *
     * @param gitBaseUrl сырой базовый URL репозитория сборок
     *                   (напр. {@code https://raw.githubusercontent.com/OWNER/REPO/BRANCH/}),
     *                   пусто для локального режима
     */
    public static LauncherServerApi createBuildsBackend(String gitBaseUrl) {
        if (gitBaseUrl == null || gitBaseUrl.isBlank()) {
            return new OfflineLauncherServerApi();
        }
        String base = gitBaseUrl.endsWith("/") ? gitBaseUrl : gitBaseUrl + "/";
        return new GitHubBuildApi(base, new UrlFetcher());
    }

    /**
     * Создаёт бэкенд сборок на Яндекс Диске: ссылка на общую папку (публичная
     * папка, токен опционален) или путь к приватной папке плюс OAuth-
     * токен. Пустые ссылка/папка означают локальный режим.
     *
     * @param token  OAuth-токен Яндекса, может быть пустым для публичных папок
     * @param linkOrFolder общая ссылка или приватная папка, напр.
     *                     {@code https://disk.yandex.ru/d/...} или
     *                     {@code /Builds}
     */
    public static LauncherServerApi createYandexBackend(String token,
                                                        String linkOrFolder) {
        if (linkOrFolder == null || linkOrFolder.isBlank()) {
            return new OfflineLauncherServerApi();
        }
        String value = linkOrFolder.trim();
        if (value.startsWith("http")) {
            return YandexDiskBuildApi.publicFolder(value, new UrlFetcher());
        }
        return new YandexDiskBuildApi(token, value, new UrlFetcher());
    }
}
