package org.example.launcher.service.modloader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.launcher.domain.model.ModLoaderType;

import org.example.launcher.infrastructure.download.FileDownloader;
import org.example.launcher.infrastructure.download.InstallationService;

/**
 * Центральный реестр и точка расширения для поддерживаемых мод-загрузчиков.
 * <p>
 * Каждый {@link ModLoaderType} привязан к {@link ModLoaderVersionProvider}
 * (поиск версий) и {@link ModLoaderInstaller} (полностью автоматическая
 * установка). UI и сервисы работают исключительно через этот
 * реестр, поэтому добавление поддержки нового загрузчика — это один
 * вызов {@link #register} без изменений UI или пайплайна.
 * <p>
 * {@link #createDefault} подключает четыре встроенных загрузчика (Fabric,
 * Forge, NeoForge, Quilt).
 */
public final class ModLoaderRegistry {

    /** Полная привязка одного загрузчика: провайдер версий + установщик. */
    public record Entry(ModLoaderType type,
                        ModLoaderVersionProvider provider,
                        ModLoaderInstaller installer) {
    }

    private final Map<ModLoaderType, Entry> entries =
            new EnumMap<>(ModLoaderType.class);

    /**
     * Регистрирует (или заменяет) привязку для типа загрузчика.
     */
    public void register(ModLoaderType type,
                         ModLoaderVersionProvider provider,
                         ModLoaderInstaller installer) {
        entries.put(type, new Entry(type, provider, installer));
    }

    /** Привязка для типа загрузчика, если зарегистрирован. */
    public Optional<Entry> get(ModLoaderType type) {
        return Optional.ofNullable(entries.get(type));
    }

    /** Все зарегистрированные привязки. */
    public List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Создаёт реестр с подключёнными четырьмя встроенными загрузчиками
     * (Fabric и Quilt через их meta API, Forge и NeoForge через их
     * официальные installer JAR).
     */
    public static ModLoaderRegistry createDefault(
            java.net.http.HttpClient httpClient,
            FileDownloader fileDownloader,
            org.example.launcher.service.JavaResolutionService javaResolutionService,
            ModLoaderMetadataMerger merger,
            InstallationService installationService) {
        ModLoaderRegistry registry = new ModLoaderRegistry();

        registry.register(ModLoaderType.FABRIC,
                new FabricVersionProvider(),
                new MetaProfileModLoaderInstaller(
                        MetaProfileModLoaderInstaller.FABRIC_PROFILE_URL,
                        httpClient, merger, installationService));

        registry.register(ModLoaderType.QUILT,
                new QuiltVersionProvider(),
                new MetaProfileModLoaderInstaller(
                        MetaProfileModLoaderInstaller.QUILT_PROFILE_URL,
                        httpClient, merger, installationService));

        registry.register(ModLoaderType.FORGE,
                new ForgeVersionProvider(),
                new InstallerJarModLoaderInstaller(
                        fileDownloader, javaResolutionService,
                        merger, installationService));

        registry.register(ModLoaderType.NEOFORGE,
                new NeoForgeVersionProvider(),
                new InstallerJarModLoaderInstaller(
                        fileDownloader, javaResolutionService,
                        merger, installationService));

        return registry;
    }
}
