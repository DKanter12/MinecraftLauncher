package org.example.launcher.service.modloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.MinecraftLauncher;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.ModdedVersionType;

/**
 * Проверяет полноту и запускаемость установки игрового инстанса,
 * выдавая точную человекочитаемую диагностику.
 * <p>
 * Для модовых инстансов проверка включает:
 * <ol>
 *   <li>наличие JSON версии загрузчика ({@code versions/{id}/{id}.json});</li>
 *   <li>соответствие установленной версии Minecraft целевой версии инстанса
 *       ({@code inheritsFrom});</li>
 *   <li>соответствие установленной версии загрузчика целевой версии инстанса
 *       (соглашение об id версии);</li>
 *   <li>наличие и целостность всех зависимостей локально — клиентский JAR,
 *       каждая библиотека (загрузчик + ванильное замыкание) и asset-индекс,
 *       каждая с проверкой SHA-1, где хэш известен;</li>
 *   <li>возможность сформировать корректную команду запуска (main-класс,
 *       игровые аргументы, разрешаемый рантайм Java);</li>
 *   <li>наличие игрового каталога инстанса.</li>
 * </ol>
 * <p>
 * Ванильные инстансы пропускают проверки загрузчика (1–3); их метаданные
 * берутся напрямую из манифеста Mojang.
 * <p>
 * {@link #repair} переустанавливает недостающее: повреждённые или отсутствующие
 * общие файлы (клиентский JAR, библиотеки, asset-индекс) докачиваются напрямую
 * через {@link InstallationService} поверх разрешённых метаданных — skip-if-valid;
 * сам мод-загрузчик переустанавливается только когда его JSON версии отсутствует
 * или неразрешим, а URL установщика ищется через провайдер версий загрузчика. Это
 * делает инстансы самовосстанавливающимися.
 */
public class ModdedProfileVerificationService {

    /**
     * Итог проверки инстанса.
     *
     * @param ok       готов ли инстанс к запуску
     * @param errors   блокирующие запуск проблемы с их причинами
     * @param warnings некритичные замечания
     * @param metadata разрешённые метаданные запуска (присутствуют, когда
     *                 цепочка версий разрешилась успешно)
     */
    public record VerificationReport(boolean ok,
                                     List<String> errors,
                                     List<String> warnings,
                                     Optional<VersionMetadata> metadata) {

        public boolean isRepairableByInstall() {
            return metadata.isPresent();
        }
    }

    private final ModdedVersionService moddedVersionService;
    private final MojangVersionService versionService;
    private final MojangVersionMetadataService metadataService;
    private final ModLoaderRegistry registry;
    private final MinecraftLauncher launcher;
    private final JavaResolutionService javaResolutionService;
    private final InstallationService installationService;

    public ModdedProfileVerificationService(ModdedVersionService moddedVersionService,
                                            MojangVersionService versionService,
                                            MojangVersionMetadataService metadataService,
                                            ModLoaderRegistry registry,
                                            MinecraftLauncher launcher,
                                            JavaResolutionService javaResolutionService,
                                            InstallationService installationService) {
        this.moddedVersionService = moddedVersionService;
        this.versionService = versionService;
        this.metadataService = metadataService;
        this.registry = registry;
        this.launcher = launcher;
        this.javaResolutionService = javaResolutionService;
        this.installationService = installationService;
    }

    // ------------------------------------------------------------------
    //  Проверка
    // ------------------------------------------------------------------

    /**
     * Проверяет установку инстанса и возвращает полный отчёт.
     */
    public VerificationReport verify(ModdedProfile profile, GameDirectory storage) {
        if (profile.isVanilla()) {
            return verifyVanilla(profile, storage);
        }
        return verifyModded(profile, storage);
    }

    /**
     * Проверка ванильного инстанса: метаданные из манифеста Mojang,
     * целостность зависимостей, команда запуска и игровой каталог.
     * Офлайн-запасной вариант: если манифест/метаданные получить нельзя,
     * но JSON версии уже закэширован локально (предыдущая установка),
     * используется этот кэшированный файл, чтобы уже скачанные версии
     * запускались офлайн.
     */
    private VerificationReport verifyVanilla(ModdedProfile profile,
                                             GameDirectory storage) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        MinecraftVersion vanillaVersion = findManifestVersion(
                profile.minecraftVersion());
        // Офлайн-запасной вариант: манифест недоступен, но локальный JSON версии существует
        boolean offlineVanilla = false;
        if (vanillaVersion == null) {
            Path localJson = storage.versionMetadata(profile.minecraftVersion());
            if (Files.isRegularFile(localJson)) {
                vanillaVersion = new MinecraftVersion(profile.minecraftVersion(),
                        org.example.launcher.version.StandardVersionType.RELEASE, null, null);
                offlineVanilla = true;
                warnings.add("Offline mode: using cached version metadata for " + profile.minecraftVersion());
            } else {
                errors.add("Minecraft version " + profile.minecraftVersion()
                        + " was not found in the Mojang manifest");
                errors.add("Cause: the version may be very old, a local build, "
                        + "or the manifest could not be fetched");
                return new VerificationReport(false, errors, warnings,
                        Optional.empty());
            }
        }

        VersionMetadata metadata;
        try {
            metadata = metadataService.fetchMetadata(vanillaVersion);
        } catch (IOException e) {
            String netMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            // Офлайн: попробовать локальный кэшированный JSON версии напрямую
            Optional<VersionMetadata> local = tryLoadLocalMetadata(profile.minecraftVersion(), storage);
            if (local.isPresent()) {
                metadata = local.get();
                warnings.add("Offline mode: using cached metadata (network: " + netMsg + ")");
            } else {
                errors.add("Failed to fetch version metadata: " + netMsg);
                errors.add("Cause: MC " + profile.minecraftVersion()
                        + " metadata could not be downloaded. Expected cache: "
                        + storage.versionMetadata(profile.minecraftVersion()));
                if (offlineVanilla) {
                    errors.add("Cached file also unavailable or corrupt");
                }
                return new VerificationReport(false, errors, warnings,
                        Optional.empty());
            }
        }

        // Зависимости на месте и целы (клиентский JAR, библиотеки,
        // asset-индекс — с проверкой SHA-1, где известен). Отсутствующий файл
        // означает неполную установку; починка докачивает
        // только недостающее.
        List<String> fileProblems = launcher.verifyFiles(metadata, storage);
        for (String problem : fileProblems) {
            errors.add("Missing or corrupt dependency: " + problem);
        }

        checkLaunchCommand(metadata, errors);
        checkInstanceDirectory(profile, storage, warnings);

        return new VerificationReport(errors.isEmpty(), errors, warnings,
                Optional.of(metadata));
    }

    /**
     * Проверка модового инстанса: JSON загрузчика, цепочка версий,
     * слитые метаданные, целостность зависимостей, команда запуска и игровой
     * каталог.
     */
    private VerificationReport verifyModded(ModdedProfile profile,
                                            GameDirectory storage) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. JSON версии на месте?
        Path versionJson = storage.versionMetadata(profile.versionId());
        if (!Files.isRegularFile(versionJson)) {
            errors.add("Loader configuration file is missing: " + versionJson);
            errors.add("Cause: the " + profile.loaderType().displayName()
                    + " installation is incomplete — the loader needs to be "
                    + "reinstalled for this instance");
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        // 2. Цепочка версий совпадает с инстансом?
        JsonObject json;
        try {
            json = JsonParser.parseString(
                            Files.readString(versionJson, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            errors.add("Loader configuration file is corrupt: " + versionJson);
            errors.add("Cause: " + e);
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        String installedId = json.has("id") && json.get("id").isJsonPrimitive()
                ? json.get("id").getAsString() : null;
        String inheritsFrom = json.has("inheritsFrom")
                && json.get("inheritsFrom").isJsonPrimitive()
                        ? json.get("inheritsFrom").getAsString() : null;

        if (installedId == null || !installedId.equals(profile.versionId())) {
            errors.add("Loader version mismatch: instance expects '"
                    + profile.versionId() + "' but the installation reports '"
                    + installedId + "'");
        }
        if (inheritsFrom == null) {
            errors.add("The loader configuration does not declare which "
                    + "Minecraft version it inherits from");
        } else if (!inheritsFrom.equals(profile.minecraftVersion())) {
            errors.add("Minecraft version mismatch: instance targets MC "
                    + profile.minecraftVersion() + " but the installation "
                    + "targets MC " + inheritsFrom);
        }
        String expectedId = profile.expectedVersionId();
        if (installedId != null && !installedId.equals(expectedId)) {
            errors.add("Loader version mismatch: instance expects '"
                    + expectedId + "' ("
                    + profile.loaderType().displayName() + " "
                    + profile.loaderVersion() + ") but the installation "
                    + "reports '" + installedId + "'");
        }
        if (!errors.isEmpty()) {
            return new VerificationReport(false, errors, warnings, Optional.empty());
        }

        // 3. Разрешить слитые метаданные запуска (ванильная цепочка)
        VersionMetadata merged;
        try {
            merged = moddedVersionService.resolveMetadata(profile.versionId(), storage);
        } catch (IOException e) {
            String netMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            // Офлайн-запасной вариант: слить кэшированный ванильный JSON с JSON загрузчика локально
            Optional<VersionMetadata> offline = tryResolveOfflineModdedMetadata(profile, storage, e);
            if (offline.isPresent()) {
                merged = offline.get();
                warnings.add("Offline mode: using cached merged metadata (network: " + netMsg + ")");
            } else {
                // Если кэша нет, дать полезную подсказку вместо «null»
                Path vanillaCache = storage.versionMetadata(profile.minecraftVersion());
                boolean hasVanillaCache = Files.isRegularFile(vanillaCache);
                errors.add("Failed to resolve launch metadata: " + netMsg);
                if (!hasVanillaCache) {
                    errors.add("Cause: vanilla metadata for MC " + profile.minecraftVersion()
                            + " is not cached locally and could not be fetched (offline). "
                            + "Connect once online and launch/verify the instance to cache it, "
                            + "or reinstall the instance. Expected cache: " + vanillaCache);
                } else {
                    errors.add("Cause: the vanilla metadata for MC "
                            + profile.minecraftVersion() + " could not be fetched "
                            + "or merged with the loader configuration. Check " + vanillaCache
                            + " for corruption and the loader JSON " + storage.versionMetadata(profile.versionId()));
                }
                return new VerificationReport(false, errors, warnings, Optional.empty());
            }
        }

        // 4. Зависимости на месте и целы (клиентский JAR, библиотеки,
        //    asset-индекс — с проверкой SHA-1, где известен)
        List<String> fileProblems = launcher.verifyFiles(merged, storage);
        for (String problem : fileProblems) {
            errors.add("Missing or corrupt dependency: " + problem);
        }

        // 5. Команда запуска может быть сформирована
        checkLaunchCommand(merged, errors);

        // 6. Игровой каталог инстанса
        checkInstanceDirectory(profile, storage, warnings);

        return new VerificationReport(errors.isEmpty(), errors, warnings,
                Optional.of(merged));
    }

    private void checkLaunchCommand(VersionMetadata metadata,
                                    List<String> errors) {
        if (metadata.mainClass().isEmpty()) {
            errors.add("Cannot form a launch command: the main class is missing "
                    + "from the metadata");
        }
        if (metadata.gameArguments().isEmpty()
                && metadata.legacyMinecraftArguments().isEmpty()) {
            errors.add("Cannot form a launch command: no game arguments in "
                    + "the metadata");
        }
        JavaResolutionResult javaResult = javaResolutionService.resolve(metadata);
        if (!javaResult.isFound()) {
            errors.add("No suitable Java runtime: "
                    + javaResult.reason().orElse("none found"));
        }
    }

    private void checkInstanceDirectory(ModdedProfile profile,
                                        GameDirectory storage,
                                        List<String> warnings) {
        Path instanceDir = storage.root().resolve(profile.gameDirPath());
        if (!Files.isDirectory(instanceDir)) {
            warnings.add("Instance game directory does not exist yet and will "
                    + "be created on launch: " + instanceDir);
        } else if (!Files.isDirectory(instanceDir.resolve("mods"))) {
            warnings.add("The instance's mods folder is missing and will be "
                    + "recreated on launch");
        }
    }

    // ------------------------------------------------------------------
    //  Починка
    // ------------------------------------------------------------------

    /**
     * Чинит установку инстанса, переустанавливая недостающее. Повреждённые или
     * отсутствующие общие файлы (клиентский JAR, библиотеки, asset-индекс)
     * докачиваются напрямую через сервис установки поверх разрешённых метаданных —
     * skip-if-valid, поэтому целые файлы не трогаются. Сам мод-загрузчик
     * переустанавливается только когда его JSON версии отсутствует или неразрешим;
     * нужный установщикам-JAR загрузчикам (Forge, NeoForge) URL установщика тогда
     * ищется через провайдер версий загрузчика.
     *
     * @param profile  чинимый инстанс
     * @param storage  игровой каталог хранилища
     * @param progress колбэк прогресса
     * @return результат установки (id версии починенной установки)
     * @throws IOException если починка невозможна (например, ванильная версия
     *                     неизвестна или загрузчик не зарегистрирован)
     */
    public ModLoaderInstaller.ModLoaderInstallResult repair(ModdedProfile profile,
                                                            GameDirectory storage,
                                                            InstallationProgress progress)
            throws IOException {
        MinecraftVersion vanillaVersion = findManifestVersion(
                profile.minecraftVersion());
        if (vanillaVersion == null) {
            throw new IOException("Cannot repair instance: vanilla MC "
                    + profile.minecraftVersion()
                    + " was not found in the Mojang manifest");
        }
        VersionMetadata vanillaMetadata = metadataService.fetchMetadata(vanillaVersion);

        if (profile.isVanilla()) {
            // Переустановить ванильную игру — skip-if-valid, поэтому целые
            // файлы не трогаются
            InstallationResult result = installationService.install(
                    vanillaVersion, vanillaMetadata, storage, progress);
            if (result.hasFailures()) {
                throw new IOException("Vanilla repair incomplete: "
                        + result.failed() + " downloads failed");
            }
            return new ModLoaderInstaller.ModLoaderInstallResult(
                    profile.versionId(), null);
        }

        var entry = registry.get(profile.loaderType()).orElse(null);
        if (entry == null) {
            throw new IOException(profile.loaderType().displayName()
                    + " support is not registered in this launcher");
        }

        // Лёгкий путь: сама установка загрузчика цела (её
        // JSON версии разрешается), и только общие файлы отсутствуют или
        // повреждены — повторный прогон стандартной установки поверх
        // слитых метаданных докачивает ровно их, с
        // skip-if-valid; переустановка загрузчика (и скачивание JAR
        // установщика) не нужна
        if (Files.isRegularFile(storage.versionMetadata(profile.versionId()))) {
            VersionMetadata merged = null;
            try {
                merged = moddedVersionService.resolveMetadata(
                        profile.versionId(), storage);
            } catch (IOException e) {
                // Повреждённый/неразрешимый JSON загрузчика → полная переустановка
            }
            if (merged != null) {
                MinecraftVersion moddedVersion = new MinecraftVersion(
                        profile.versionId(), ModdedVersionType.INSTANCE,
                        null, null);
                InstallationResult result = installationService.install(
                        moddedVersion, merged, storage, progress);
                if (result.hasFailures()) {
                    throw new IOException(profile.loaderType().displayName()
                            + " repair incomplete: " + result.failed()
                            + " downloads failed");
                }
                return new ModLoaderInstaller.ModLoaderInstallResult(
                        profile.versionId(), result);
            }
        }

        // Полная переустановка: перезаписывает JSON версии, заново запускает
        // установщик загрузчика (Forge/NeoForge) и забирает каждую
        // зависимость — со skip-if-valid, поэтому целые файлы не
        // трогаются
        return entry.installer().install(vanillaVersion, vanillaMetadata,
                resolveLoaderVersion(entry, profile), storage, progress);
    }

    /**
     * Разрешает версию загрузчика для переустановки из провайдера версий
     * загрузчика — единственного источника URL установщика, нужного
     * загрузчикам с installer-JAR, — с откатом на безадресную
     * реконструкцию из данных инстанса, когда провайдер недоступен
     * (например, офлайн).
     */
    private ModLoaderVersion resolveLoaderVersion(ModLoaderRegistry.Entry entry,
                                                  ModdedProfile profile) {
        try {
            for (ModLoaderVersion v : entry.provider()
                    .fetchVersions(profile.minecraftVersion())) {
                if (v.loaderVersion().equals(profile.loaderVersion())) {
                    return v;
                }
            }
        } catch (Exception e) {
            // Провайдер недоступен или версии нет в списке — реконструкция
            // ниже является лучшими доступными данными
        }
        return new ModLoaderVersion(
                profile.loaderType(), profile.loaderVersion(),
                profile.minecraftVersion(), true, null);
    }

    private Optional<VersionMetadata> tryLoadLocalMetadata(String versionId, GameDirectory storage) {
        Path local = storage.versionMetadata(versionId);
        if (!Files.isRegularFile(local)) return Optional.empty();
        try {
            String json = Files.readString(local, StandardCharsets.UTF_8);
            return Optional.of(metadataService.parseMetadata(json, versionId));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<VersionMetadata> tryResolveOfflineModdedMetadata(ModdedProfile profile,
                                                                     GameDirectory storage,
                                                                     Exception cause) {
        Path loaderJsonPath = storage.versionMetadata(profile.versionId());
        if (!Files.isRegularFile(loaderJsonPath)) return Optional.empty();
        try {
            String loaderJson = Files.readString(loaderJsonPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(loaderJson).getAsJsonObject();
            String vanillaId = root.has("inheritsFrom") && root.get("inheritsFrom").isJsonPrimitive()
                    ? root.get("inheritsFrom").getAsString() : profile.minecraftVersion();
            Optional<VersionMetadata> vanillaOpt = tryLoadLocalMetadata(vanillaId, storage);
            if (vanillaOpt.isEmpty()) return Optional.empty();
            // Слить ваниллу + загрузчик локально тем же парсером
            ModLoaderMetadataMerger merger = new ModLoaderMetadataMerger(metadataService);
            return Optional.of(merger.merge(vanillaOpt.get(), loaderJson));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private MinecraftVersion findManifestVersion(String minecraftVersion) {
        try {
            return versionService.fetchVersions().versions().stream()
                    .filter(v -> v.id().equals(minecraftVersion))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
