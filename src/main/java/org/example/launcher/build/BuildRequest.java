package org.example.launcher.build;

import java.util.List;
import java.util.Objects;

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Данные интерфейса для создания сборки.
 * Формируется из {@code NewInstanceDialog.Result} сразу после
 * нажатия кнопки создания, дальше UI в создании не участвует.
 *
 * @param type         ванилла или семейство загрузчика
 * @param mcVersion    целевая версия Minecraft
 * @param loader       выбранная версия загрузчика ({@code null} для ваниллы)
 * @param extraJvmArgs доп. JVM-арги
 * @param buildName    имя сборки (может быть пустым — подставится авто)
 */
public record BuildRequest(
        ModLoaderType type,
        MinecraftVersion mcVersion,
        ModLoaderVersion loader,
        List<String> extraJvmArgs,
        String buildName) {

    public BuildRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mcVersion, "mcVersion");
        extraJvmArgs = extraJvmArgs == null ? List.of() : List.copyOf(extraJvmArgs);
        buildName = buildName == null ? "" : buildName;
        if (type != ModLoaderType.VANILLA && loader == null) {
            throw new IllegalArgumentException("Modded build requires loader version");
        }
    }

    public boolean isVanilla() {
        return type == ModLoaderType.VANILLA;
    }

    /** Id версии, которая должна лежать в {@code versions/}. */
    public String versionId() {
        return isVanilla() ? mcVersion.id() : loader.installedVersionId();
    }

    /** Имя для отображения (пустое означает авто «Loader MC»). */
    public String effectiveName() {
        return buildName == null ? "" : buildName.trim();
    }
}
