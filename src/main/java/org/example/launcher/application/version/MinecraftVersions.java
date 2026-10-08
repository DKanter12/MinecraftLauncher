package org.example.launcher.application.version;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.domain.port.MinecraftVersionRepository;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;

/**
 * Главный класс списка версий: ванильные — из Mojang,
 * модифицированные — из официальных источников каждого загрузчика.
 * На выходе единый отсортированный список {@link MinecraftVersion}
 * для интерфейса. Списки и скачивание — разные задачи:
 * здесь только получение и подготовка списков.
 */
public class MinecraftVersions {

    private final MinecraftVersionRepository vanillaRepo;
    private final ModLoaderRegistry loaders;

    public MinecraftVersions(MinecraftVersionRepository vanillaRepo,
                             ModLoaderRegistry loaders) {
        this.vanillaRepo = Objects.requireNonNull(vanillaRepo, "vanillaRepo");
        this.loaders = Objects.requireNonNull(loaders, "loaders");
    }

    /**
     * Ванильные версии из официального манифеста, от новых к старым.
     */
    public List<MinecraftVersion> getMinecraftVanillaVersions()
            throws IOException {
        return MinecraftVersionSorter.sortByReleaseDate(
                vanillaRepo.fetchVersions());
    }

    /**
     * Модифицированные версии: для каждого загрузчика — поддерживаемые им
     * версии Minecraft (ядро проставлено, тип — релиз: загрузчики ставятся
     * только на релизы). Загрузчик с недоступным источником пропускается,
     * чтобы один упавший metaservice не ронял весь список.
     */
    public List<MinecraftVersion> getMinecraftModdedVersions() {
        List<MinecraftVersion> result = new ArrayList<>();
        for (var entry : loaders.all()) {
            ModLoaderType core = entry.type();
            if (core == ModLoaderType.VANILLA) {
                continue;
            }
            java.util.Set<String> supported;
            try {
                supported = entry.provider().fetchSupportedMinecraftVersions();
            } catch (IOException | RuntimeException e) {
                continue;
            }
            if (supported == null || supported.isEmpty()) {
                continue;
            }
            List<MinecraftVersion> perLoader = new ArrayList<>();
            for (String mc : supported) {
                if (mc == null || mc.isBlank()) {
                    continue;
                }
                perLoader.add(MinecraftVersion.of(mc, VersionType.RELEASE,
                        null, null, core, ""));
            }
            result.addAll(MinecraftVersionSorter.sortByVersion(perLoader));
        }
        return result;
    }
}
