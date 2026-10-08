package org.example.launcher.domain.model;

import java.util.Objects;
import java.util.Optional;

import org.example.launcher.domain.model.ModLoaderType;

/**
 * Одна версия загрузчика модов, разрешённая для конкретной версии
 * Minecraft (например, Fabric Loader 0.16.9 для Minecraft 1.21.4).
 * <p>
 * Экземпляры создаются реализациями {@link org.example.launcher.infrastructure.loaders.ModLoaderVersionProvider},
 * гарантирующими совместимость: каждая запись списка, возвращённого для версии
 * Minecraft, устанавливаема для этой версии.
 *
 * @param loaderType     семейство загрузчика (Fabric, Forge, …)
 * @param loaderVersion  собственная версия загрузчика (например, {@code "0.16.9"},
 *                       {@code "47.4.10"})
 * @param minecraftVersion версия Minecraft, для которой разрешена запись
 * @param stable         признак стабильной/рекомендуемой версии
 * @param installerUrl   прямой URL JAR установщика (Forge/NeoForge)
 *                       либо {@code null} для загрузчиков на основе meta-API
 *                       (Fabric/Quilt)
 */
public record ModLoaderVersion(
        ModLoaderType loaderType,
        String loaderVersion,
        String minecraftVersion,
        boolean stable,
        String installerUrl) {

    public ModLoaderVersion {
        Objects.requireNonNull(loaderType, "loaderType");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    /**
     * URL JAR установщика, обёрнутый в Optional
     * ({@code Optional.empty()} для загрузчиков на основе meta-API).
     */
    public Optional<String> installerUrlOpt() {
        return Optional.ofNullable(installerUrl);
    }

    /**
     * Результирующий идентификатор установленной версии согласно соглашению
     * каждого загрузчика:
     * <ul>
     *   <li>Fabric: {@code fabric-loader-0.16.9-1.21.4}</li>
     *   <li>Quilt: {@code quilt-loader-0.26.0-1.21.4}</li>
     *   <li>Forge: {@code 1.20.1-47.4.10}</li>
     *   <li>NeoForge: {@code neoforge-21.4.147}</li>
     * </ul>
     */
    public String installedVersionId() {
        return switch (loaderType) {
            case VANILLA -> minecraftVersion;
            case FABRIC -> "fabric-loader-" + loaderVersion + "-" + minecraftVersion;
            case QUILT -> "quilt-loader-" + loaderVersion + "-" + minecraftVersion;
            case FORGE -> minecraftVersion + "-forge-" + loaderVersion;
            case NEOFORGE -> "neoforge-" + loaderVersion;
        };
    }

    @Override
    public String toString() {
        return loaderType.displayName() + " " + loaderVersion
                + " (MC " + minecraftVersion + ")";
    }
}
