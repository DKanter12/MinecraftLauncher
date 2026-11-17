package org.example.launcher.infrastructure.mojang;

import java.io.IOException;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Предоставляет метаданные отдельной версии (клиентский JAR, библиотеки, ассеты, аргументы…).
 * <p>
 * Это выборка второго уровня: {@link VersionService} даёт список
 * версий, а этот сервис разрешает детальные метаданные конкретной версии
 * по её индивидуальному URL JSON.
 */
public interface VersionMetadataService {

    /**
     * Загружает и разбирает метаданные заданной версии.
     *
     * @param version разрешаемая версия (должна иметь не-null
     *                URL метаданных)
     * @return разобранные метаданные, никогда {@code null}
     * @throws IOException если метаданные не удалось получить или разобрать
     */
    VersionMetadata fetchMetadata(MinecraftVersion version) throws IOException;
}
