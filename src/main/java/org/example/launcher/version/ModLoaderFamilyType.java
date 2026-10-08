package org.example.launcher.version;

import java.util.EnumMap;
import java.util.Map;

import org.example.launcher.domain.model.ModLoaderType;

/**
 * Тип версии для локально установленных версий загрузчиков модов (Fabric,
 * Forge, NeoForge, Quilt). Один кэшированный экземпляр на семейство загрузчиков, чтобы
 * установленные модовые версии могли показываться в едином обозревателе
 * версий рядом с ванильными версиями манифеста со своими фильтрами
 * типов (регистрируются через {@link VersionTypeRegistry#register}).
 */
public final class ModLoaderFamilyType implements VersionType {

    private static final Map<ModLoaderType, ModLoaderFamilyType> INSTANCES =
            new EnumMap<>(ModLoaderType.class);

    private final ModLoaderType loaderType;

    private ModLoaderFamilyType(ModLoaderType loaderType) {
        this.loaderType = loaderType;
    }

    /** Кэшированный экземпляр типа для семейства загрузчиков. */
    public static synchronized ModLoaderFamilyType of(ModLoaderType loaderType) {
        return INSTANCES.computeIfAbsent(loaderType, ModLoaderFamilyType::new);
    }

    @Override
    public String id() {
        return "modded-" + loaderType.name().toLowerCase();
    }

    @Override
    public String displayName() {
        return loaderType.displayName();
    }

    @Override
    public boolean isStable() {
        return true;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
