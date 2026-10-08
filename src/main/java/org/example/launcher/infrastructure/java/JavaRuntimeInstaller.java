package org.example.launcher.infrastructure.java;

import java.nio.file.Path;

import org.example.launcher.domain.model.JavaRuntime;

/**
 * Устанавливает рантаймы Java в управляемый каталог лаунчера.
 * <p>
 * Интерфейс — точка расширения для будущей функциональности: когда
 * {@link JavaResolutionService} сообщает об отсутствии подходящего рантайма,
 * лаунчер может вызвать установщик для скачивания и распаковки
 * совместимого JRE (например, из Mojang
 * {@code java-runtime-manifest} или Eclipse Adoptium).
 * <p>
 * Первая реализация будет заглушкой; конкретную реализацию
 * можно добавить позже без изменения точек вызова.
 */
public interface JavaRuntimeInstaller {

    /**
     * Устанавливает рантайм Java под заданный идентификатор компонента Mojang.
     * <p>
     * Манифест рантаймов Mojang отображает имена компонентов вида
     * {@code "java-runtime-gamma"} на платформенные загрузки.
     *
     * @param component компонент рантайма Mojang (например,
     *                  {@code "java-runtime-gamma"})
     * @param targetDir каталог для установки
     * @return установленный {@link JavaRuntime} или {@code null}, если
     *         установка не удалась
     * @throws Exception при невосстановимой ошибке
     */
    JavaRuntime install(String component, Path targetDir) throws Exception;

    /**
     * Устанавливает рантайм Java как минимум с заданной major-версией.
     * <p>
     * Используется, когда метаданные версии не содержат идентификатор
     * компонента Mojang (старые версии).
     *
     * @param requiredMajor минимальная major-версия (например, 17)
     * @param targetDir     каталог для установки
     * @return установленный {@link JavaRuntime} или {@code null}, если
     *         установка не удалась
     * @throws Exception при невосстановимой ошибке
     */
    JavaRuntime install(int requiredMajor, Path targetDir) throws Exception;
}
