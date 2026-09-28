package org.example.launcher.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Реестр известных типов версий.
 * <p>
 * Предзаполнен всеми значениями {@link StandardVersionType}. Сторонние
 * или пользовательские типы можно регистрировать во время выполнения через
 * {@link #register(VersionType)} без изменения существующего кода,
 * что является основной точкой расширения для поддержки будущих типов версий.
 */
public final class VersionTypeRegistry {

    private final Map<String, VersionType> typesById = new LinkedHashMap<>();

    public VersionTypeRegistry() {
        for (StandardVersionType type : StandardVersionType.values()) {
            register(type);
        }
    }

    /**
     * Регистрирует пользовательский тип версии, делая его разрешимым из
     * манифеста и выбираемым в интерфейсе.
     *
     * @param type регистрируемый тип версии (не должен быть {@code null})
     */
    public void register(VersionType type) {
        Objects.requireNonNull(type, "type");
        typesById.put(type.id(), type);
    }

    /**
     * Разрешает исходную строку типа (из манифеста Mojang) в
     * {@link VersionType}. Возвращает {@link StandardVersionType#UNKNOWN},
     * если идентификатор не распознан.
     */
    public VersionType resolve(String id) {
        if (id == null) {
            return StandardVersionType.UNKNOWN;
        }
        return typesById.getOrDefault(id, StandardVersionType.UNKNOWN);
    }

    /**
     * @return немодифицируемый список всех зарегистрированных типов версий в порядке
     *         вставки (удобен для заполнения фильтров интерфейса).
     */
    public List<VersionType> all() {
        return Collections.unmodifiableList(new ArrayList<>(typesById.values()));
    }
}
