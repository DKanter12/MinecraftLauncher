package org.example.launcher.infrastructure.java;

import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Определяет, какой рантайм Java использовать для запуска заданной версии
 * Minecraft.
 * <p>
 * Реализации запрашивают у {@link JavaDetector} доступные рантаймы
 * и выбирают лучшее соответствие по требованиям
 * {@link JavaVersion} версии. Если подходящий рантайм не найден,
 * результат указывает, отсутствует ли он полностью или лишь
 * несовместим, позволяя вызывающему решить, запускать ли
 * автоустановку через {@code JavaRuntimeInstaller}.
 */
public interface JavaResolutionService {

    /**
     * Подбирает рантайм Java для заданных метаданных версии Minecraft.
     *
     * @param metadata запускаемая версия
     * @return результат разрешения (никогда {@code null})
     */
    JavaResolutionResult resolve(VersionMetadata metadata);

    /**
     * Подбирает рантайм Java под заданную минимальную major-версию.
     * <p>
     * Перегрузка полезна, когда метаданные версии не содержат
     * {@link JavaVersion} (старые версии) и вызывающий передаёт
     * разумное значение по умолчанию (например, 8).
     *
     * @param requiredMajor минимальная требуемая major-версия Java
     * @return результат разрешения (никогда {@code null})
     */
    JavaResolutionResult resolve(int requiredMajor);
}
