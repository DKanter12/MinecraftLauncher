package org.example.launcher.domain.port;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.example.launcher.domain.model.MinecraftVersion;

/**
 * Порт списка версий Minecraft. Application зависит только от него,
 * откуда данные (Mojang, кэш, Fake в тестах) — дело infrastructure.
 */
public interface MinecraftVersionRepository {

    /** Все известные версии от новых к старым. */
    List<MinecraftVersion> fetchVersions() throws IOException;

    /** Версия по id из уже полученного списка. */
    default Optional<MinecraftVersion> findById(String id, List<MinecraftVersion> versions) {
        if (id == null || versions == null) {
            return Optional.empty();
        }
        return versions.stream().filter(v -> v.id().equals(id)).findFirst();
    }
}
