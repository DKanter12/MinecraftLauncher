package org.example.launcher.infrastructure.mojang;

import java.io.IOException;
import java.util.List;

import org.example.launcher.domain.model.MinecraftVersion;

/**
 * Предоставляет каталог версий Minecraft.
 * <p>
 * Это выборка первого уровня: список версий; детальные метаданные
 * конкретной версии отдаёт {@link VersionMetadataService}.
 */
public interface VersionService {

    /**
     * Загружает полный каталог версий Minecraft от новых к старым.
     *
     * @return версии каталога, никогда {@code null}
     * @throws IOException если каталог не удалось получить
     */
    List<MinecraftVersion> fetchVersions() throws IOException;
}
