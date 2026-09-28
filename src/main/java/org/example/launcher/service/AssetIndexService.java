package org.example.launcher.service;

import java.io.IOException;

import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.AssetIndexContent;

/**
 * Загружает и разбирает asset-индекс версии Minecraft.
 * <p>
 * Asset-индекс — это JSON-документ по ссылке
 * {@link AssetIndex#url()}, который отображает каждый отдельный ассет
 * (текстуры, звуки, языковые файлы…) в его SHA1-хэш и размер.
 */
public interface AssetIndexService {

    /**
     * Загружает и разбирает содержимое asset-индекса.
     *
     * @param assetIndex ссылка на asset-индекс из метаданных версии
     * @return разобранное содержимое со всеми объектами-ассетами
     * @throws IOException если индекс не удалось загрузить или разобрать
     */
    AssetIndexContent fetchIndex(AssetIndex assetIndex) throws IOException;
}
