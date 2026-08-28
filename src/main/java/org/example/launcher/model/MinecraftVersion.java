package org.example.launcher.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.version.VersionType;

/**
 * Immutable description of a single Minecraft version as listed in the
 * official Mojang version manifest.
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

    /** Version identifier, e.g. {@code "1.21"} or {@code "1.21-rc1"}. */
    public String id() {
        return id;
    }

    /** The version type (Release, Snapshot, …). */
    public VersionType type() {
        return type;
    }

    /**
     * Raw publication date string as returned by Mojang (ISO-8601 with
     * offset), e.g. {@code "2024-06-13T10:30:00+00:00"}.
     */
    public String releaseTimeRaw() {
        return releaseTimeRaw;
    }

    /**
     * Parsed publication date, or {@link Optional#empty()} when the raw
     * value is missing or not parseable.
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

    /** Human-readable, formatted publication date for UI display. */
    public String formattedReleaseTime() {
        return releaseTime()
                .map(dt -> dt.format(DISPLAY_FORMATTER))
                .orElse("—");
    }

    /** Direct link to the per-version metadata JSON on Mojang servers. */
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
