package org.example.launcher.version;

import java.util.EnumMap;
import java.util.Map;

import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Version type for locally installed mod loader versions (Fabric,
 * Forge, NeoForge, Quilt). One cached instance per loader family, so
 * installed modded versions can appear in the unified version
 * browser next to the vanilla manifest versions, with their own type
 * filters (registered via {@link VersionTypeRegistry#register}).
 */
public final class ModLoaderFamilyType implements VersionType {

    private static final Map<ModLoaderType, ModLoaderFamilyType> INSTANCES =
            new EnumMap<>(ModLoaderType.class);

    private final ModLoaderType loaderType;

    private ModLoaderFamilyType(ModLoaderType loaderType) {
        this.loaderType = loaderType;
    }

    /** The cached type instance for a loader family. */
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
