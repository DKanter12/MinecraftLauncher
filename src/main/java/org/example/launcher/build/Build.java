package org.example.launcher.build;

import java.util.Objects;

import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Сборка как доменная сущность: то, что видит и называет пользователь.
 * Хранит только идентичность: название, версию цифрами, ядро и версию ядра.
 * Манифестные записи и провайдерные объекты здесь не живут — их несёт
 * {@link BuildRequest} как конверт для выполнения создания.
 *
 * @param name             название сборки (пустое означает авто-имя)
 * @param minecraftVersion версия цифрами, например {@code "1.21.1"}
 * @param core             ядро: {@code VANILLA} либо загрузчик
 *                         ({@code FABRIC}/{@code FORGE}/{@code NEOFORGE}/{@code QUILT})
 * @param coreVersion      версия ядра, например {@code "0.16.9"};
 *                         пустая для ваниллы
 */
public record Build(
        String name,
        String minecraftVersion,
        ModLoaderType core,
        String coreVersion) {

    public Build {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(core, "core");
        name = name == null ? "" : name;
        coreVersion = coreVersion == null ? "" : coreVersion;
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion must not be blank");
        }
        if (core == ModLoaderType.VANILLA && !coreVersion.isBlank()) {
            throw new IllegalArgumentException("Vanilla build must not have a core version");
        }
        if (core != ModLoaderType.VANILLA && coreVersion.isBlank()) {
            throw new IllegalArgumentException("Modded build requires a core version");
        }
    }

    /** Ванильная ли сборка (ядро {@code VANILLA}). */
    public boolean isVanilla() {
        return core == ModLoaderType.VANILLA;
    }

    /** Обрезанное название (пустое означает авто-имя). */
    public String effectiveName() {
        return name.trim();
    }

    /** Имя для отображения: введённое либо авто «Ядро МК». */
    public String displayName() {
        String wanted = effectiveName();
        return wanted.isEmpty()
                ? core.displayName() + " " + minecraftVersion
                : wanted;
    }

    /**
     * Id версии, которая должна лежать в {@code versions/}.
     * Формула одна на всех — из {@link ModLoaderVersion}.
     */
    public String versionId() {
        if (isVanilla()) {
            return minecraftVersion;
        }
        return new ModLoaderVersion(core, coreVersion, minecraftVersion,
                true, null).installedVersionId();
    }
}
