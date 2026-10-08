package org.example.launcher.infrastructure.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModdedProfile;

/**
 * Превращает скачанную сборку в запускаемый игровой инстанс: выбирает
 * версию загрузчика (сначала новейшие), создаёт инстанс под именем
 * сборки и копирует в него моды/конфиги. Остальные файлы Minecraft
 * (клиент, библиотеки, ассеты) разрешаются обычным потоком установки
 * из полученного плана.
 */
public class ModpackProvisioner {

    /** Что создать и установить для скачанной сборки. */
    public record ProvisionPlan(
            String instanceName,
            ModLoaderType loaderType,
            String minecraftVersion,
            String loaderVersion,
            List<BuildFileEntry> modFiles,
            List<BuildFileEntry> configFiles) {
    }

    private ModpackProvisioner() {
    }

    /**
     * Выбирает конфигурацию для сборки: версию Minecraft из её
     * манифеста (или имени папки), загрузчик (явное переопределение,
     * из манифеста, либо Forge, если моды есть, а загрузчик
     * неизвестен) и новейшую доступную версию загрузчика.
     *
     * @param descriptor     скачанная сборка
     * @param loaderOverride явное семейство загрузчика, или {@code null}
     * @param loaderVersions доступные версии загрузчика, сначала новейшие
     * @return план, который UI устанавливает и запускает
     * @throws IOException если конфигурацию вывести нельзя (неизвестная версия MC,
     *                     нет версий загрузчика, нет файлов)
     */
    public static ProvisionPlan plan(BuildDescriptor descriptor,
                                     ModLoaderType loaderOverride,
                                     List<ModLoaderVersion> loaderVersions)
            throws IOException {
        String minecraft = descriptor.summary().minecraftVersion();
        if (minecraft == null || minecraft.isBlank()
                || minecraft.equals("unknown")) {
            throw new IOException("Build '" + descriptor.id()
                    + "' does not say which Minecraft version it needs — "
                    + "name the folder with the version (e.g. 'Pack 1.20.1') "
                    + "or add a build.json manifest");
        }
        boolean hasMods = descriptor.files().stream()
                .anyMatch(f -> f.category() == BuildFileCategory.MODS);
        ModLoaderType loader = loaderOverride != null ? loaderOverride
                : descriptor.summary().loaderType();
        if (loader == ModLoaderType.VANILLA && hasMods) {
            loader = ModLoaderType.FORGE;
        }
        if (loaderVersions == null || loaderVersions.isEmpty()) {
            throw new IOException("No " + loader.displayName()
                    + " versions available for MC " + minecraft);
        }
        if (descriptor.files().isEmpty()) {
            throw new IOException(
                    "Build '" + descriptor.id() + "' has no files");
        }
        List<BuildFileEntry> mods = new ArrayList<>();
        List<BuildFileEntry> configs = new ArrayList<>();
        for (BuildFileEntry file : descriptor.files()) {
            if (file.category() == BuildFileCategory.MODS) {
                mods.add(file);
            } else if (file.category() == BuildFileCategory.CONFIGS) {
                configs.add(file);
            }
        }
        return new ProvisionPlan(
                descriptor.summary().displayName(),
                loader, minecraft, loaderVersions.get(0).loaderVersion(),
                List.copyOf(mods), List.copyOf(configs));
    }

    /**
     * Создаёт инстанс по плану и копирует уже
     * скачанные моды/конфиги из подготовленного каталога сборки в
     * него.
     *
     * @param plan            что создать (см. {@link #plan})
     * @param profiles        реестр инстансов
     * @param stagedBuildDir  папка скачанной сборки
     *                        (раскладка {@code builds/{id}/})
     * @return созданный инстанс
     * @throws IOException если инстанс не удаётся создать
     */
    public static ModdedProfile instantiate(ProvisionPlan plan,
                                            FileSystemBuildRepository profiles,
                                            Path stagedBuildDir)
            throws IOException {
        String versionId = plan.loaderType() == ModLoaderType.VANILLA
                ? plan.minecraftVersion()
                : new ModLoaderVersion(plan.loaderType(),
                        plan.loaderVersion(), plan.minecraftVersion(),
                        true, null).installedVersionId();
        ModdedProfile profile = profiles.createProfile(
                plan.loaderType(), plan.loaderType() == ModLoaderType.VANILLA
                        ? "" : plan.loaderVersion(),
                plan.minecraftVersion(), versionId, List.of(),
                plan.instanceName(), 0);
        Path gameDir = profiles.resolveGameDir(profile);
        copyCategory(stagedBuildDir, gameDir, BuildFileCategory.MODS);
        copyCategory(stagedBuildDir, gameDir, BuildFileCategory.CONFIGS);
        return profile;
    }

    private static void copyCategory(Path stagedBuildDir, Path gameDir,
                                     BuildFileCategory category)
            throws IOException {
        Path source = stagedBuildDir.resolve(category.folder());
        if (!Files.isDirectory(source)) {
            return;
        }
        Path target = gameDir.resolve(category.folder());
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .sorted().toList()) {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative.toString());
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(file, destination,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
