package org.example.launcher.distribution;

import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Публичное описание сборки, как её отдаёт сервер лаунчера
 * — всё, что нужно игроку для решения об установке,
 * без списка файлов.
 *
 * <p>Сборка идентифицируется неизменяемым {@code id}. Выпуск новой
 * ревизии той же сборки сохраняет id и лишь увеличивает
 * {@code version} (напр. 1.0.0 → 1.1.0), что и движет потоком
 * обновлений.</p>
 *
 * @param id              уникальный id сборки, также имя локальной папки
 * @param version         версия сборки, числа через точку
 * @param displayName     человекочитаемое имя сборки
 * @param description     краткое описание для списка сборок
 * @param loaderType      загрузчик, под который предназначена сборка
 * @param minecraftVersion версия Minecraft сборки
 * @param loaderVersion   версия загрузчика сборки
 */
public record BuildSummary(
        String id,
        String version,
        String displayName,
        String description,
        ModLoaderType loaderType,
        String minecraftVersion,
        String loaderVersion) {

    public BuildSummary {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (loaderType == null) {
            throw new IllegalArgumentException("loaderType must not be null");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("minecraftVersion must not be blank");
        }
    }

    /** @return краткая строка для строк списка, напр. {@code Better Survival 1.1.0 · Fabric 1.21.4}. */
    public String summaryLine() {
        return displayName + " " + version + " · " + loaderType.displayName()
                + " " + minecraftVersion;
    }

    /** @return отображаемая форма для диалогов. */
    @Override
    public String toString() {
        return summaryLine();
    }
}
