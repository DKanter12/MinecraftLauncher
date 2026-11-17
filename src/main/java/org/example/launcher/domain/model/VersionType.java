package org.example.launcher.domain.model;

/**
 * Собственный тип версии. Маппинг формата Mojang — в
 * {@link #fromMojangId(String)}, поэтому Domain не зависит от строк API.
 */
public enum VersionType {
    RELEASE,
    SNAPSHOT,
    BETA,
    ALPHA,
    UNKNOWN;

    /**
     * Преобразует официальный тип Mojang в собственный enum.
     * Неизвестное и {@code null} дают {@code UNKNOWN}, исключений нет.
     */
    public static VersionType fromMojangId(String mojangId) {
        if (mojangId == null) {
            return UNKNOWN;
        }
        return switch (mojangId) {
            case "release" -> RELEASE;
            case "snapshot" -> SNAPSHOT;
            case "old_beta" -> BETA;
            case "old_alpha" -> ALPHA;
            default -> UNKNOWN;
        };
    }

    /** Стабильная ли версия для обычной игры. */
    public boolean isStable() {
        return this == RELEASE;
    }

    /**
     * Обратный маппинг в строку Mojang: файл версий и диагностика
     * хранят официальный формат ({@code "release"}, {@code "snapshot"}...).
     */
    public String mojangId() {
        return switch (this) {
            case RELEASE -> "release";
            case SNAPSHOT -> "snapshot";
            case BETA -> "old_beta";
            case ALPHA -> "old_alpha";
            case UNKNOWN -> "unknown";
        };
    }
}
