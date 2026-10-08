package org.example.launcher.domain.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.domain.model.ModLoaderType;

/**
 * Игровой экземпляр: именованная изолированная игровая среда.
 * <p>
 * Экземпляр бывает {@code VANILLA} (запускает немодифицированную игру)
 * либо установкой загрузчика модов (Fabric, Forge, NeoForge, Quilt). Каждый
 * экземпляр владеет отдельным игровым каталогом (с {@code mods/},
 * {@code config/}, {@code resourcepacks/}, {@code shaderpacks/},
 * {@code saves/}, {@code logs/} и прочими файлами сборки), поэтому
 * разные экземпляры никогда не конфликтуют друг с другом. Общие
 * ресурсы (клиентский JAR, библиотеки, ресурсы, нативные файлы) хранятся в
 * корневом хранилище лаунчера и переиспользуются экземплярами без
 * повторной загрузки.
 * <p>
 * Экземпляр хранит всё необходимое для воспроизведения и проверки
 * установки: версию Minecraft, тип и версию загрузчика,
 * идентификатор установленной версии, игровой каталог, установленные
 * компоненты и дополнительные параметры запуска.
 *
 * @param id              уникальный идентификатор профиля (также имя каталога
 *                        в {@code profiles/})
 * @param name            человекочитаемое отображаемое имя
 * @param loaderType      семейство загрузчика модов (Fabric, Forge, …)
 * @param loaderVersion   собственная версия загрузчика (например, "0.16.9")
 * @param minecraftVersion целевая версия Minecraft (например, "1.21.4")
 * @param versionId       идентификатор установленной модовой версии, которую запускает
 *                        профиль (например, "fabric-loader-0.16.9-1.21.4")
 * @param gameDirPath     игровой каталог профиля относительно корня
 *                        хранилища (например, "profiles/MyPack")
 * @param components      установленные компоненты, например
 *                        ["minecraft:1.21.4", "fabric-loader:0.16.9"]
 * @param extraJvmArgs    дополнительные параметры запуска JVM для этого
 *                        профиля (например, "-Xmx4G")
 * @param memoryMb        лимит выделенной памяти в мегабайтах, применяется как
 *                        {@code -Xmx} при запуске (переопределяет
 *                        {@code -Xmx}/{@code -Xms} в
 *                        {@code extraJvmArgs}); {@code 0} означает автоматический
 *                        режим (без явного лимита)
 * @param createdTimeRaw     метка создания в формате ISO-8601
 * @param lastPlayedTimeRaw  метка последнего запуска в формате ISO-8601
 *                           либо {@code null}, если запусков не было
 */
public record ModdedProfile(
        String id,
        String name,
        ModLoaderType loaderType,
        String loaderVersion,
        String minecraftVersion,
        String versionId,
        String gameDirPath,
        List<String> components,
        List<String> extraJvmArgs,
        int memoryMb,
        String createdTimeRaw,
        String lastPlayedTimeRaw) {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public ModdedProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(loaderType, "loaderType");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(gameDirPath, "gameDirPath");
        components = List.copyOf(components == null ? List.of() : components);
        extraJvmArgs = List.copyOf(extraJvmArgs == null ? List.of() : extraJvmArgs);
    }

    /**
     * Идентификатор версии, который должен запускать профиль, выведенный из
     * типа/версии загрузчика и версии Minecraft по соглашению об идентификаторах
     * загрузчика. Сверяется с идентификатором установленного JSON версии
     * при проверке. Ванильные экземпляры запускают сам
     * ванильный идентификатор версии.
     */
    public String expectedVersionId() {
        if (loaderType == ModLoaderType.VANILLA) {
            return minecraftVersion;
        }
        return new ModLoaderVersion(loaderType, loaderVersion, minecraftVersion,
                true, null).installedVersionId();
    }

    /** Признак запуска немодифицированной ванильной игры данным экземпляром. */
    public boolean isVanilla() {
        return loaderType == ModLoaderType.VANILLA;
    }

    public Optional<OffsetDateTime> createdTime() {
        return parseTime(createdTimeRaw);
    }

    public Optional<OffsetDateTime> lastPlayedTime() {
        return parseTime(lastPlayedTimeRaw);
    }

    /** Краткая строка описания, например "Fabric 0.16.9 · MC 1.21.4". */
    public String summary() {
        if (isVanilla()) {
            return "Vanilla · MC " + minecraftVersion;
        }
        return loaderType.displayName() + " " + loaderVersion
                + " · MC " + minecraftVersion;
    }

    private static Optional<OffsetDateTime> parseTime(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(raw, FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "ModdedProfile{id='" + id + "', name='" + name + "', "
                + summary() + ", versionId=" + versionId + "}";
    }
}
