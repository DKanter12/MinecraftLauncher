package org.example.launcher.domain.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * Доменная версия Minecraft. Только данные, без JSON и HTTP.
 * Не путать со сборкой: это «1.21.11», а не «Survival».
 */
public record MinecraftVersion(
        String id,
        VersionType type,
        Optional<OffsetDateTime> releaseTime,
        String metadataUrl) {

    public MinecraftVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(releaseTime, "releaseTime");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    /** Фабрика из сырых строк манифеста; битая дата даёт пусто, не исключение. */
    public static MinecraftVersion of(String id, VersionType type,
                                      String releaseTimeRaw, String metadataUrl) {
        Optional<OffsetDateTime> releaseTime = Optional.empty();
        if (releaseTimeRaw != null && !releaseTimeRaw.isBlank()) {
            try {
                releaseTime = Optional.of(OffsetDateTime.parse(releaseTimeRaw));
            } catch (DateTimeParseException ignored) {
                // оставляем пусто
            }
        }
        return new MinecraftVersion(id, type, releaseTime, metadataUrl);
    }
}
