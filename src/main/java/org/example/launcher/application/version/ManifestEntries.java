package org.example.launcher.application.version;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.version.StandardVersionType;
import org.example.launcher.application.build.Build;
import org.example.launcher.application.build.BuildRequest;

/**
 * Мост между доменной версией документа и внутренними моделями пайплайна.
 * Чистые преобразования без сети (кроме {@link #baseEntry}, где нужен URL).
 */
public final class ManifestEntries {

    private ManifestEntries() {
    }

    /** Доменный тип в старый тип манифеста для внутренних сервисов. */
    public static StandardVersionType toLegacyType(
            org.example.launcher.domain.model.VersionType type) {
        return switch (type) {
            case RELEASE -> StandardVersionType.RELEASE;
            case SNAPSHOT -> StandardVersionType.SNAPSHOT;
            case BETA -> StandardVersionType.OLD_BETA;
            case ALPHA -> StandardVersionType.OLD_ALPHA;
            case UNKNOWN -> StandardVersionType.UNKNOWN;
        };
    }

    /**
     * Доменная версия в запись манифеста для внутренних сервисов.
     * URL может отсутствовать (у комбинированных модовых записей его нет) —
     * тогда запись годится только для идентификации, не для скачивания.
     */
    public static org.example.launcher.model.MinecraftVersion toManifestEntry(
            MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        return new org.example.launcher.model.MinecraftVersion(
                version.id(),
                toLegacyType(version.type()),
                version.releaseTime().map(Object::toString).orElse(null),
                version.metadataUrl());
    }

    /** Загрузчик версии для установщика ({@code null} для ваниллы). */
    public static ModLoaderVersion toLoader(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        if (version.isVanilla()) {
            return null;
        }
        return new ModLoaderVersion(version.core(), version.loaderVersion(),
                version.id(), false, null);
    }

    /**
     * Полный запрос для проверки/скачивания.
     * Для модовой версии требуется указанная версия ядра,
     * иначе {@code IllegalArgumentException}.
     */
    public static BuildRequest toRequest(MinecraftVersion version) {
        Objects.requireNonNull(version, "version");
        Build build = new Build("", version.id(), version.core(),
                version.loaderVersion());
        return new BuildRequest(build, toManifestEntry(version),
                toLoader(version), List.of());
    }

    /**
     * Ванильная запись манифеста с URL для скачивания: сначала URL самой
     * версии, иначе поиск по id в репозитории.
     *
     * @throws IOException если URL нет и версия в репозитории не найдена
     */
    public static org.example.launcher.model.MinecraftVersion baseEntry(
            MinecraftVersion version, MinecraftVersionRepository repo)
            throws IOException {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(repo, "repo");
        if (version.metadataUrl() != null && !version.metadataUrl().isBlank()) {
            return toManifestEntry(version);
        }
        List<MinecraftVersion> all = repo.fetchVersions();
        return repo.findById(version.id(), all)
                .map(ManifestEntries::toManifestEntry)
                .orElseThrow(() -> new IOException(
                        "Minecraft version not found: " + version.id()));
    }

    /** Ядро по умолчанию для записей без ядра. */
    public static ModLoaderType coreOrVanilla(ModLoaderType core) {
        return core == null ? ModLoaderType.VANILLA : core;
    }
}
