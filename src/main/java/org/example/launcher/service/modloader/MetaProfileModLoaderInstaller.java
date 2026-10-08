package org.example.launcher.service.modloader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.version.ModdedVersionType;

/**
 * Реализация {@link ModLoaderInstaller} для загрузчиков, отдающих готовый
 * JSON профиля версии через meta API (Fabric и Quilt).
 * <p>
 * Поток:
 * <ol>
 *   <li>получить JSON профиля загрузчика
 *       ({@code {meta}/versions/loader/{mc}/{loader}/profile/json});</li>
 *   <li>слить его с ванильными метаданными
 *       (см. {@link ModLoaderMetadataMerger});</li>
 *   <li>сохранить исходный JSON профиля как
 *       {@code versions/{id}/{id}.json} (с маркером {@code inheritsFrom})
 *       — конфигурацию запуска;</li>
 *   <li>прогнать стандартную {@link InstallationService} на слитых
 *       метаданных, которая скачает ванильную базу (клиентский JAR,
 *       библиотеки, ассеты) плюс все зависимости загрузчика с проверкой
 *       SHA-1 и переиспользованием skip-if-valid;</li>
 *   <li>проверить результат и отчитаться.</li>
 * </ol>
 */
public class MetaProfileModLoaderInstaller implements ModLoaderInstaller {

    public static final String FABRIC_PROFILE_URL =
            "https://meta.fabricmc.net/v2/versions/loader/{mc}/{v}/profile/json";

    public static final String QUILT_PROFILE_URL =
            "https://meta.quiltmc.org/v3/versions/loader/{mc}/{v}/profile/json";

    private final String profileUrlTemplate;
    private final HttpClient httpClient;
    private final ModLoaderMetadataMerger merger;
    private final InstallationService installationService;

    public MetaProfileModLoaderInstaller(String profileUrlTemplate,
                                         HttpClient httpClient,
                                         ModLoaderMetadataMerger merger,
                                         InstallationService installationService) {
        this.profileUrlTemplate = profileUrlTemplate;
        this.httpClient = httpClient;
        this.merger = merger;
        this.installationService = installationService;
    }

    @Override
    public ModLoaderInstallResult install(MinecraftVersion vanillaVersion,
                                          VersionMetadata vanillaMetadata,
                                          ModLoaderVersion loader,
                                          GameDirectory gameDir,
                                          InstallationProgress progress) throws IOException {
        // 1. Получить JSON профиля загрузчика
        String loaderJson = fetchProfileJson(loader.minecraftVersion(),
                loader.loaderVersion());

        // 2. Слить с ванильными метаданными
        VersionMetadata merged = merger.merge(vanillaMetadata, loaderJson);
        String versionId = merged.id();

        // 3. Сохранить конфигурацию запуска: исходный JSON профиля +
        //    маркер inheritsFrom, стандартная раскладка versions/{id}/{id}.json
        Path jsonFile = gameDir.versionMetadata(versionId);
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, withInheritsFrom(loaderJson, vanillaMetadata.id()),
                StandardCharsets.UTF_8);

        // 4. Установить файлы (ванильная база + зависимости загрузчика, с проверкой хэша)
        MinecraftVersion moddedVersion = new MinecraftVersion(
                versionId, ModdedVersionType.INSTANCE, null, null);
        InstallationResult files = installationService.install(
                moddedVersion, merged, gameDir, progress);

        // 5. Проверить
        if (files.hasFailures()) {
            throw new IOException("Mod loader installation finished with "
                    + files.failed() + " failed downloads: "
                    + files.failedTasks().stream()
                            .map(t -> t.task().name())
                            .limit(5)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("(unknown)"));
        }
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("Launch configuration was not created: " + jsonFile);
        }

        return new ModLoaderInstallResult(versionId, files);
    }

    private String fetchProfileJson(String mcVersion, String loaderVersion)
            throws IOException {
        String url = profileUrlTemplate
                .replace("{mc}", mcVersion)
                .replace("{v}", loaderVersion);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Profile endpoint returned HTTP "
                        + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching loader profile", e);
        }
    }

    /**
     * Гарантирует, что в сохраняемом JSON есть поле {@code inheritsFrom},
     * указывающее на ванильную версию, чтобы установленная версия могла
     * переразрешаться при запуске.
     */
    static String withInheritsFrom(String loaderJson, String vanillaId) {
        JsonObject root = JsonParser.parseString(loaderJson).getAsJsonObject();
        if (!root.has("inheritsFrom")) {
            root.addProperty("inheritsFrom", vanillaId);
        }
        return root.toString();
    }
}
