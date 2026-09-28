package org.example.launcher.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.version.VersionType;

/**
 * Неизменяемое описание одной версии Minecraft из
 * официального манифеста версий Mojang.
 */
public final class MinecraftVersion {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final String id;
    private final VersionType type;
    private final String releaseTimeRaw;
    private final String metadataUrl;

    public MinecraftVersion(String id, VersionType type, String releaseTimeRaw, String metadataUrl) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.releaseTimeRaw = releaseTimeRaw;
        this.metadataUrl = metadataUrl;
    }

    /** Идентификатор версии, например {@code "1.21"} или {@code "1.21-rc1"}. */
    public String id() {
        return id;
    }

    /** Тип версии (релиз, снапшот, …). */
    public VersionType type() {
        return type;
    }

    /**
     * Исходная строка даты публикации от Mojang (ISO-8601 со
     * смещением), например {@code "2024-06-13T10:30:00+00:00"}.
     */
    public String releaseTimeRaw() {
        return releaseTimeRaw;
    }

    /**
     * Разобранная дата публикации либо {@link Optional#empty()}, если исходное
     * значение отсутствует или не разбирается.
     */
    public Optional<OffsetDateTime> releaseTime() {
        if (releaseTimeRaw == null || releaseTimeRaw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(releaseTimeRaw));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Человекочитаемая форматированная дата публикации для показа в интерфейсе. */
    public String formattedReleaseTime() {
        return releaseTime()
                .map(dt -> dt.format(DISPLAY_FORMATTER))
                .orElse("—");
    }

    /** Прямая ссылка на JSON метаданных версии на серверах Mojang. */
    public String metadataUrl() {
        return metadataUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MinecraftVersion that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MinecraftVersion{id='" + id + "', type=" + type.id()
                + ", releaseTime=" + releaseTimeRaw + '}';
    }
}
