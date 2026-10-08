package org.example.launcher.application.build;

import java.util.List;
import java.util.Objects;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.ModLoaderType;

/**
 * Конверт для выполнения создания сборки: сама {@link Build} (кто она)
 * плюс всё нужное для скачивания (откуда брать файлы).
 * Несогласованные данные отбрасываются сразу в конструкторе,
 * а не в глубине пайплайна.
 *
 * @param build        сборка: название, версия цифрами, ядро, версия ядра
 * @param mcVersion    запись манифеста (несёт {@code metadataUrl} для скачивания)
 * @param loader       объект версии загрузчика для установщика
 *                     ({@code null} для ваниллы)
 * @param extraJvmArgs доп. JVM-арги
 */
public record BuildRequest(
        Build build,
        MinecraftVersion mcVersion,
        ModLoaderVersion loader,
        List<String> extraJvmArgs) {

    public BuildRequest {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(mcVersion, "mcVersion");
        extraJvmArgs = extraJvmArgs == null ? List.of() : List.copyOf(extraJvmArgs);
        if (!mcVersion.id().equals(build.minecraftVersion())) {
            throw new IllegalArgumentException("Manifest entry " + mcVersion.id()
                    + " does not match build MC " + build.minecraftVersion());
        }
        if (build.isVanilla()) {
            if (loader != null) {
                throw new IllegalArgumentException("Vanilla build must not carry a loader");
            }
        } else {
            Objects.requireNonNull(loader, "loader");
            if (loader.loaderType() != build.core()
                    || !loader.loaderVersion().equals(build.coreVersion())
                    || !loader.minecraftVersion().equals(build.minecraftVersion())) {
                throw new IllegalArgumentException("Loader does not match build core");
            }
        }
    }

    /** Ядро сборки (делегат к {@link Build#core()}). */
    public ModLoaderType type() {
        return build.core();
    }

    /** Ванильная ли сборка. */
    public boolean isVanilla() {
        return build.isVanilla();
    }

    /** Id версии, которая должна лежать в {@code versions/}. */
    public String versionId() {
        return isVanilla() ? mcVersion.id() : loader.installedVersionId();
    }

    /** Обрезанное название сборки (пустое означает авто-имя). */
    public String effectiveName() {
        return build.effectiveName();
    }
}
