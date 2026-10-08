package org.example.launcher.infrastructure.mojang;

import java.io.IOException;

import org.example.launcher.model.VersionManifest;

/**
 * Даёт доступ к каталогу версий Minecraft.
 * <p>
 * Реализации вправе получать версии от Mojang, из локального кэша
 * или любого другого источника. Интерфейс отделяет UI от
 * конкретного поставщика данных.
 */
public interface VersionService {

    /**
     * Загружает полный манифест версий.
     *
     * @return не-{@code null} манифест со всеми известными версиями
     * @throws IOException если манифест не удалось получить или разобрать
     */
    VersionManifest fetchVersions() throws IOException;
}
