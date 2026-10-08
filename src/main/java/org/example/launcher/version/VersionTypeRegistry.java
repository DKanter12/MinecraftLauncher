package org.example.launcher.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.example.launcher.domain.model.VersionType;

/**
 * Реестр типов версий для отображения и фильтрации каталога.
 * Предзаполнен типами Mojang; сторонние типы можно регистрировать
 * во время выполнения без изменения существующего кода.
 * <p>
 * Реестр не описывает конкретную версию Minecraft (это
 * {@link org.example.launcher.domain.model.MinecraftVersion})
 * и не подменяет разбор манифеста (это
 * {@link VersionType#fromMojangId(String)}).
 */
public final class VersionTypeRegistry {

    private final Map<String, VersionType> typesById = new LinkedHashMap<>();

    public VersionTypeRegistry() {
        register(VersionType.RELEASE);
        register(VersionType.SNAPSHOT);
        register(VersionType.BETA);
        register(VersionType.ALPHA);
    }

    /**
     * Регистрирует тип версии, делая его доступным для фильтров каталога.
     *
     * @param type регистрируемый тип (не должен быть {@code null})
     */
    public void register(VersionType type) {
        Objects.requireNonNull(type, "type");
        typesById.put(type.name(), type);
    }

    /**
     * @return зарегистрированные типы в порядке вставки
     */
    public List<VersionType> getRegisteredTypes() {
        return Collections.unmodifiableList(new ArrayList<>(typesById.values()));
    }

    /**
     * Находит зарегистрированный тип по имени.
     * Неизвестное имя даёт {@link VersionType#UNKNOWN}.
     */
    public VersionType findById(String id) {
        if (id == null) {
            return VersionType.UNKNOWN;
        }
        return typesById.getOrDefault(id, VersionType.UNKNOWN);
    }
}
