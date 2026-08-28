package org.example.launcher.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry of known version types.
 * <p>
 * Pre-populated with all {@link StandardVersionType} values. Third-party
 * or custom types can be registered at runtime via
 * {@link #register(VersionType)} without changing any existing code,
 * which is the main extension point for future version type support.
 */
public final class VersionTypeRegistry {

    private final Map<String, VersionType> typesById = new LinkedHashMap<>();

    public VersionTypeRegistry() {
        for (StandardVersionType type : StandardVersionType.values()) {
            register(type);
        }
    }

    /**
     * Registers a custom version type, making it resolvable from the
     * manifest and selectable in the UI.
     *
     * @param type the version type to register (must not be {@code null})
     */
    public void register(VersionType type) {
        Objects.requireNonNull(type, "type");
        typesById.put(type.id(), type);
    }

    /**
     * Resolves a raw type string (as found in the Mojang manifest) to a
     * {@link VersionType}. Falls back to {@link StandardVersionType#UNKNOWN}
     * when the identifier is not recognised.
     */
    public VersionType resolve(String id) {
        if (id == null) {
            return StandardVersionType.UNKNOWN;
        }
        return typesById.getOrDefault(id, StandardVersionType.UNKNOWN);
    }

    /**
     * @return an unmodifiable, insertion-ordered list of all registered
     *         version types (useful for populating UI filters).
     */
    public List<VersionType> all() {
        return Collections.unmodifiableList(new ArrayList<>(typesById.values()));
    }
}
