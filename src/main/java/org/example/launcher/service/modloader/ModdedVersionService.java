package org.example.launcher.service.modloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.example.launcher.domain.model.ModLoaderType;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;
import org.example.launcher.service.MojangVersionService;
import org.example.launcher.version.ModLoaderFamilyType;
import org.example.launcher.version.ModdedVersionType;

/**
 * Обнаруживает локально установленные модовые версии (Fabric, Forge,
 * NeoForge, Quilt) и разрешает их метаданные запуска.
 * <p>
 * Установленные модовые версии следуют стандартной раскладке
 * {@code versions/{id}/{id}.json} с полем {@code inheritsFrom},
 * указывающим на ванильную версию, которую они расширяют. В манифесте Mojang
 * они никогда не появляются, поэтому сервис сканирует локальный
 * каталог {@code versions/}.
 * <p>
 * При запуске {@link #resolveMetadata} переразрешает цепочку наследования:
 * локальный JSON загрузчика сливается с ванильными метаданными,
 * полученными от Mojang (см. {@link ModLoaderMetadataMerger}).
 */
public class ModdedVersionService {

    private final MojangVersionService versionService;
    private final MojangVersionMetadataService metadataService;
    private final ModLoaderMetadataMerger merger;

    public ModdedVersionService(MojangVersionService versionService,
                                MojangVersionMetadataService metadataService,
                                ModLoaderMetadataMerger merger) {
        this.versionService = versionService;
        this.metadataService = metadataService;
        this.merger = merger;
    }

    /**
     * Перечисляет все модовые версии, установленные в заданном игровом каталоге.
     * Версия считается модовой, когда её локальный JSON несёт поле
     * {@code inheritsFrom} (профили загрузчиков) или совпадает с известным
     * соглашением об id загрузчика.
     */
    public List<MinecraftVersion> listInstalled(GameDirectory gameDir)
            throws IOException {
        List<MinecraftVersion> result = new ArrayList<>();
        Path versionsDir = gameDir.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }

        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            stream.forEach(dirs::add);
        }

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            String dirName = dir.getFileName().toString();
            Path json = dir.resolve(dirName + ".json");
            if (!Files.isRegularFile(json)) continue;

            try {
                JsonObject root = JsonParser.parseString(
                                Files.readString(json, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                boolean hasInherits = root.has("inheritsFrom")
                        && root.get("inheritsFrom").isJsonPrimitive();
                String id = root.has("id") && root.get("id").isJsonPrimitive()
                        ? root.get("id").getAsString() : dirName;
                if (hasInherits || loaderTypeOf(id).isPresent()) {
                    result.add(new MinecraftVersion(id,
                            ModdedVersionType.INSTANCE, null, null));
                }
            } catch (Exception ignored) {
                // Повреждённый или посторонний JSON в versions/ — не модовая версия
            }
        }
        return result;
    }

    /**
     * Разрешает полные метаданные запуска для установленной модовой
     * версии: читает локальный JSON загрузчика, получает унаследованные
     * ванильные метаданные из манифеста Mojang и сливает оба.
     *
     * @param moddedVersionId id установленной версии
     *                         (например, {@code fabric-loader-0.16.9-1.21.4})
     * @param gameDir         раскладка игрового каталога
     * @return слитые самодостаточные метаданные запуска
     * @throws IOException если локальный JSON отсутствует/невалиден либо
     *                     ванильные метаданные неразрешимы
     */
    public VersionMetadata resolveMetadata(String moddedVersionId,
                                           GameDirectory gameDir)
            throws IOException {
        Path jsonFile = gameDir.versionMetadata(moddedVersionId);
        if (!Files.isRegularFile(jsonFile)) {
            throw new IOException("No local version JSON for " + moddedVersionId
                    + " (expected " + jsonFile + ")");
        }
        String loaderJson = Files.readString(jsonFile, StandardCharsets.UTF_8);

        JsonObject root;
        try {
            root = JsonParser.parseString(loaderJson).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Invalid version JSON for " + moddedVersionId, e);
        }

        String vanillaId = root.has("inheritsFrom")
                && root.get("inheritsFrom").isJsonPrimitive()
                        ? root.get("inheritsFrom").getAsString() : null;

        VersionMetadata vanilla = fetchVanillaMetadata(vanillaId, gameDir);
        return merger.merge(vanilla, loaderJson);
    }

    private VersionMetadata fetchVanillaMetadata(String vanillaId, GameDirectory gameDir)
            throws IOException {
        if (vanillaId == null || vanillaId.isBlank()) {
            throw new IOException(
                    "Version JSON has no inheritsFrom — cannot resolve vanilla base");
        }

        try {
            MinecraftVersion vanillaVersion = versionService.fetchVersions().versions()
                    .stream()
                    .filter(v -> v.id().equals(vanillaId))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Vanilla version " + vanillaId
                            + " not found in the Mojang manifest"));
            return metadataService.fetchMetadata(vanillaVersion);
        } catch (IOException network) {
            // Офлайн-запасной вариант: использовать кэшированный ванильный JSON, если есть
            Path local = gameDir.versionMetadata(vanillaId);
            if (Files.isRegularFile(local)) {
                try {
                    String json = Files.readString(local, StandardCharsets.UTF_8);
                    return metadataService.parseMetadata(json, vanillaId);
                } catch (Exception parseEx) {
                    String pMsg = parseEx.getMessage() != null ? parseEx.getMessage() : parseEx.toString();
                    String nMsg = network.getMessage() != null ? network.getMessage() : network.toString();
                    throw new IOException("Offline fallback failed for " + vanillaId + ": " + pMsg + " (network: " + nMsg + ")", network);
                }
            }
            throw network;
        }
    }

    /**
     * Эвристически определяет тип загрузчика по id версии,
     * по стандартным соглашениям об именовании
     * ({@code fabric-loader-…}, {@code quilt-loader-…},
     * {@code …-forge-…}, {@code neoforge-…}).
     */
    public static Optional<ModLoaderType> loaderTypeOf(String versionId) {
        if (versionId == null) return Optional.empty();
        String id = versionId.toLowerCase();
        if (id.startsWith("fabric-loader-")) return Optional.of(ModLoaderType.FABRIC);
        if (id.startsWith("quilt-loader-")) return Optional.of(ModLoaderType.QUILT);
        if (id.contains("-forge-") || id.endsWith("-forge")) return Optional.of(ModLoaderType.FORGE);
        if (id.startsWith("neoforge-")) return Optional.of(ModLoaderType.NEOFORGE);
        return Optional.empty();
    }

    /**
     * Локально установленная модовая версия с определённым по id версии
     * семейством загрузчика, версией загрузчика, разобранной из
     * id, и ванильной базой, разрешённой из поля {@code inheritsFrom}
     * локального JSON (авторитетно — id NeoForge не содержат версию MC).
     *
     * @param version          запись таблицы для установленной версии
     * @param loaderType       семейство загрузчика (Fabric, Forge, …)
     * @param loaderVersion    собственная версия загрузчика
     *                         (например, {@code "0.16.9"}, {@code "47.4.23"})
     * @param minecraftVersion базовая ванильная версия
     *                         (например, {@code "1.21.4"})
     */
    public record InstalledModdedVersion(MinecraftVersion version,
                                         ModLoaderType loaderType,
                                         String loaderVersion,
                                         String minecraftVersion) {

        /** Разобранная информация как запись версии загрузчика. */
        public ModLoaderVersion toModLoaderVersion() {
            return new ModLoaderVersion(loaderType, loaderVersion,
                    minecraftVersion, true, null);
        }
    }

    /**
     * Перечисляет установленные модовые версии вместе с разобранными
     * семейством загрузчика, версией загрузчика и ванильной базой — для
     * единого браузера версий. Записи, чей id нельзя отнести к известному
     * загрузчику, чей локальный JSON не имеет {@code inheritsFrom}
     * либо чья версия загрузчика не разбирается из id,
     * пропускаются.
     */
    public List<InstalledModdedVersion> listInstalledDetailed(GameDirectory gameDir)
            throws IOException {
        List<InstalledModdedVersion> result = new ArrayList<>();
        Path versionsDir = gameDir.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }

        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            stream.forEach(dirs::add);
        }

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            String dirName = dir.getFileName().toString();
            Path json = dir.resolve(dirName + ".json");
            if (!Files.isRegularFile(json)) continue;

            try {
                JsonObject root = JsonParser.parseString(
                                Files.readString(json, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String id = root.has("id") && root.get("id").isJsonPrimitive()
                        ? root.get("id").getAsString() : dirName;
                ModLoaderType family = loaderTypeOf(id).orElse(null);
                if (family == null) continue;
                String mcBase = root.has("inheritsFrom")
                        && root.get("inheritsFrom").isJsonPrimitive()
                                ? root.get("inheritsFrom").getAsString() : null;
                if (mcBase == null || mcBase.isBlank()) continue;
                String loaderVersion = parseLoaderVersion(id, family, mcBase);
                if (loaderVersion == null || loaderVersion.isBlank()) continue;
                result.add(new InstalledModdedVersion(
                        new MinecraftVersion(id,
                                ModLoaderFamilyType.of(family), null, null),
                        family, loaderVersion, mcBase));
            } catch (Exception ignored) {
                // Повреждённый или посторонний JSON в versions/ — не модовая версия
            }
        }
        return result;
    }

    /**
     * Извлекает собственную версию загрузчика из id установленной версии,
     * зная семейство и (авторитетную) базовую ванильную версию.
     */
    private static String parseLoaderVersion(String id, ModLoaderType family,
                                             String mcBase) {
        return switch (family) {
            case FABRIC -> stripAffixes(id, "fabric-loader-", "-" + mcBase);
            case QUILT -> stripAffixes(id, "quilt-loader-", "-" + mcBase);
            case FORGE -> stripAffixes(id, mcBase + "-forge-", "");
            case NEOFORGE -> stripAffixes(id, "neoforge-", "");
            default -> null;
        };
    }

    private static String stripAffixes(String id, String prefix, String suffix) {
        if (!id.startsWith(prefix) || !id.endsWith(suffix)) return null;
        int end = id.length() - suffix.length();
        if (end <= prefix.length()) return null;
        return id.substring(prefix.length(), end);
    }
}
