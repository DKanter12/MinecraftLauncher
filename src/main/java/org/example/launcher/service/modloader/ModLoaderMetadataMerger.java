package org.example.launcher.service.modloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.MojangVersionMetadataService;

/**
 * Сливает JSON версии мод-загрузчика (профиль в стиле {@code inheritsFrom},
 * выдаваемый Fabric/Quilt meta API или установщиками
 * Forge/NeoForge) с ванильными метаданными Minecraft, от которых он
 * наследуется, в единые самодостаточные {@link VersionMetadata} для
 * существующего пайплайна установки и запуска.
 * <p>
 * Правила слияния (соответствуют семантике наследования официального лаунчера):
 * <ul>
 *   <li>{@code id} — id профиля загрузчика (например,
 *       {@code fabric-loader-0.16.9-1.21.4})</li>
 *   <li>{@code mainClass} — загрузчика (именно это делает запуск модовым)</li>
 *   <li>{@code libraries} — сначала библиотеки загрузчика, затем ванильные
 *       (общие библиотеки дедуплицируются по имени)</li>
 *   <li>{@code arguments} — сначала ванильные аргументы, затем
 *       загрузчика (официальная семантика конкатенации при
 *       {@code inheritsFrom}). Профили загрузчиков несут только свои добавки:
 *       Forge/NeoForge добавляют аргументы {@code -p}/fml, но опираются на
 *       ванильные {@code -cp ${classpath}} и {@code --username} и т.д.;
 *       Fabric/Quilt несут пустые списки аргументов</li>
 *   <li>{@code assetIndex}, {@code assets}, {@code javaVersion},
 *       {@code clientDownload} — значение загрузчика при наличии,
 *       иначе ванильное (профили загрузчиков обычно их опускают)</li>
 * </ul>
 */
public class ModLoaderMetadataMerger {

    private final MojangVersionMetadataService parser;

    public ModLoaderMetadataMerger(MojangVersionMetadataService parser) {
        this.parser = parser;
    }

    /**
     * Сливает JSON профиля загрузчика с ванильными метаданными.
     *
     * @param vanilla    ванильные метаданные Minecraft наследуемой версии
     * @param loaderJson JSON профиля загрузчика (сырая строка)
     * @return слитые самодостаточные метаданные
     * @throws IOException если JSON загрузчика не разбирается
     */
    public VersionMetadata merge(VersionMetadata vanilla, String loaderJson)
            throws IOException {
        VersionMetadata loader = parser.parseMetadata(loaderJson, vanilla.id());

        String id = loader.id();
        String mainClass = loader.mainClass().orElse(vanilla.mainClass().orElse(null));

        List<org.example.launcher.model.Library> libraries =
                mergeLibraries(loader.libraries(), vanilla.libraries());

        // Официальная семантика inheritsFrom: сначала аргументы родителя,
        // затем дочерние (загрузчика). Forge/NeoForge опираются на ванильную
        // запись -cp ${classpath} для legacy classpath BootstrapLauncher.
        List<String> gameArgs = concat(vanilla.gameArguments(), loader.gameArguments());
        List<String> jvmArgs = concat(vanilla.jvmArguments(), loader.jvmArguments());

        String legacyArgs = loader.legacyMinecraftArguments()
                .orElseGet(() -> vanilla.legacyMinecraftArguments().orElse(null));

        org.example.launcher.model.AssetIndex assetIndex = loader.assetIndex()
                .orElse(vanilla.assetIndex().orElse(null));
        String assets = loader.assets().orElse(vanilla.assets().orElse(null));
        JavaVersion javaVersion = loader.javaVersion()
                .orElse(vanilla.javaVersion().orElse(null));
        org.example.launcher.model.DownloadInfo clientDownload = loader.clientDownload()
                .orElse(vanilla.clientDownload().orElse(null));

        return new VersionMetadata(
                id,
                loader.type().orElse(vanilla.type().orElse(null)),
                mainClass,
                assets,
                assetIndex,
                javaVersion,
                clientDownload,
                libraries,
                gameArgs,
                jvmArgs,
                legacyArgs);
    }

    /**
     * Объединяет библиотеки загрузчика и ваниллы: при конфликте имён побеждают
     * библиотеки загрузчика (загрузчики заменяют/переопределяют отдельные ванильные
     * библиотеки, например пропатченный бридж авторизации), иначе ванильные
     * добавляются в конец.
     */
    private List<org.example.launcher.model.Library> mergeLibraries(
            List<org.example.launcher.model.Library> loaderLibs,
            List<org.example.launcher.model.Library> vanillaLibs) {
        List<org.example.launcher.model.Library> result = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();

        for (org.example.launcher.model.Library lib : loaderLibs) {
            if (seenNames.add(lib.name())) {
                result.add(lib);
            }
        }
        for (org.example.launcher.model.Library lib : vanillaLibs) {
            if (seenNames.add(lib.name())) {
                result.add(lib);
            }
        }
        return result;
    }

    /** Базовый список первым, добавки — в конец (с сохранением порядка). */
    private static List<String> concat(List<String> base, List<String> additions) {
        if (additions.isEmpty()) return base;
        List<String> result = new ArrayList<>(base.size() + additions.size());
        result.addAll(base);
        result.addAll(additions);
        return result;
    }
}
