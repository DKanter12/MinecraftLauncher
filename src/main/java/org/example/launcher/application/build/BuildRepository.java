package org.example.launcher.application.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.example.launcher.domain.model.ModdedProfile;

/**
 * Хранение сборок. Файловую реализацию даёт
 * {@code FileSystemBuildRepository}; прикладной код зависит
 * только от этого контракта.
 */
public interface BuildRepository {

    /** Все сохранённые сборки. */
    List<ModdedProfile> findAll() throws IOException;

    /** Сборка по id. */
    Optional<ModdedProfile> findById(String id) throws IOException;

    /**
     * Сохраняет сборку (новая добавляется, существующая с тем же id
     * заменяется целиком).
     */
    void save(ModdedProfile profile) throws IOException;

    /**
     * Удаляет запись сборки. Игровой каталог (моды, миры, настройки)
     * не удаляется — возвращается путь, чтобы показать пользователю,
     * где остались его файлы.
     *
     * @return осиротевший игровой каталог, если сборка существовала
     */
    Optional<Path> delete(String id) throws IOException;
}
