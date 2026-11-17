package org.example.launcher.domain.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Доменная версия Minecraft. Только данные, без JSON и HTTP.
 * Не путать со сборкой: это «1.21.11», а не «Survival».
 * Ванильная версия несёт только саму игру; модифицированная дополнительно
 * несёт ядро ({@code core}) и его версию ({@code loaderVersion}).
 */
public record MinecraftVersion(
        String id,
        VersionType type,
        Optional<OffsetDateTime> releaseTime,
        String metadataUrl,
        ModLoaderType core,
        String loaderVersion) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public MinecraftVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(releaseTime, "releaseTime");
        if (core == null) {
            core = ModLoaderType.VANILLA;
        }
        if (loaderVersion == null) {
            loaderVersion = "";
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (core == ModLoaderType.VANILLA && !loaderVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Vanilla version must not carry a loader version");
        }
    }

    /** Фабрика из сырых строк манифеста; битая дата даёт пусто, не исключение. */
    public static MinecraftVersion of(String id, VersionType type,
                                      String releaseTimeRaw, String metadataUrl) {
        return of(id, type, releaseTimeRaw, metadataUrl,
                ModLoaderType.VANILLA, "");
    }

    /** Фабрика для модифицированной версии (ядро + его версия). */
    public static MinecraftVersion of(String id, VersionType type,
                                      String releaseTimeRaw, String metadataUrl,
                                      ModLoaderType core, String loaderVersion) {
        Optional<OffsetDateTime> releaseTime = Optional.empty();
        if (releaseTimeRaw != null && !releaseTimeRaw.isBlank()) {
            try {
                releaseTime = Optional.of(OffsetDateTime.parse(releaseTimeRaw));
            } catch (DateTimeParseException ignored) {
                // оставляем пусто
            }
        }
        return new MinecraftVersion(id, type, releaseTime, metadataUrl,
                core, loaderVersion);
    }

    /** Ванильная ли версия (без ядра). */
    public boolean isVanilla() {
        return core == ModLoaderType.VANILLA;
    }

    /** URL данных версии (используется позже для скачивания). */
    public String url() {
        return metadataUrl;
    }

    /** Дата выхода строкой для отображения, пусто если неизвестна. */
    public Optional<String> releaseDate() {
        return releaseTime.map(t -> t.format(DATE_FORMAT));
    }
}
