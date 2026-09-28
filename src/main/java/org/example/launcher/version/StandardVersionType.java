package org.example.launcher.version;

/**
 * Стандартные типы версий Minecraft из официального манифеста Mojang.
 * <p>
 * Каждая константа соответствует значению поля {@code "type"} в JSON
 * манифеста версий. Новые константы можно добавлять сюда при появлении
 * дополнительных типов у Mojang — существующий код менять не нужно.
 */
public enum StandardVersionType implements VersionType {

    RELEASE("release", "Release", true),
    SNAPSHOT("snapshot", "Snapshot", false),
    OLD_BETA("old_beta", "Beta", false),
    OLD_ALPHA("old_alpha", "Alpha", false),
    UNKNOWN("unknown", "Unknown", false);

    private final String id;
    private final String displayName;
    private final boolean stable;

    StandardVersionType(String id, String displayName, boolean stable) {
        this.id = id;
        this.displayName = displayName;
        this.stable = stable;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isStable() {
        return stable;
    }
}
