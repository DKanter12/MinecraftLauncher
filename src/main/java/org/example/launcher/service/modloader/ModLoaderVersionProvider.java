package org.example.launcher.service.modloader;

import java.io.IOException;
import java.util.List;

import org.example.launcher.model.ModLoaderVersion;

/**
 * Предоставляет список доступных версий мод-загрузчика, совместимых
 * с заданной версией Minecraft.
 * <p>
 * Каждая реализация гарантирует, что каждый возвращённый
 * {@link ModLoaderVersion} устанавливаем для запрошенной версии Minecraft,
 * — поэтому UI может показывать список без фильтрации.
 * <p>
 * Это главная точка расширения для добавления новых мод-загрузчиков.
 */
public interface ModLoaderVersionProvider {

    /**
     * Загружает все версии загрузчика, совместимые с заданной версией Minecraft,
     * от новых к старым.
     *
     * @param minecraftVersion id версии Minecraft (например, {@code "1.21.4"})
     * @return совместимые версии загрузчика, никогда {@code null}
     * @throws IOException если список версий получить не удалось
     */
    List<ModLoaderVersion> fetchVersions(String minecraftVersion) throws IOException;

    /**
     * Загружает множество версий Minecraft, поддерживаемых этим загрузчиком, как
     * сообщает собственный metaservice загрузчика, — авторитетный ответ на вопрос
     * «существует ли этот загрузчик под ту версию» (например, NeoForge существует
     * только для MC 1.20.1 и новее; Fabric и Quilt начинаются с 1.14). Один лёгкий
     * вызов вместо одного вызова {@link #fetchVersions} на версию.
     *
     * @return поддерживаемые id версий Minecraft либо {@code null},
     *         когда провайдер не может определить множество, — вызывающие
     *         тогда откатываются на собственное предположение
     * @throws IOException если множество получить не удалось
     */
    default java.util.Set<String> fetchSupportedMinecraftVersions() throws IOException {
        return null;
    }
}
