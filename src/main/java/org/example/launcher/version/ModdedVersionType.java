package org.example.launcher.version;

/**
 * Тип версии для локально установленных модовых версий (Fabric, Forge,
 * NeoForge, Quilt). Эти версии никогда не встречаются в манифесте Mojang;
 * они создаются установкой загрузчиков модов лаунчера и
 * обнаруживаются сканированием локального каталога {@code versions/}.
 */
public final class ModdedVersionType implements VersionType {

    public static final ModdedVersionType INSTANCE = new ModdedVersionType();

    private ModdedVersionType() {
    }

    @Override
    public String id() {
        return "modded";
    }

    @Override
    public String displayName() {
        return "Modded";
    }

    @Override
    public boolean isStable() {
        return true;
    }
}
