package org.example.launcher.domain.model;

import java.util.Collections;
import java.util.Map;

/**
 * Разобранное содержимое JSON-файла индекса ресурсов Mojang.
 * <p>
 * Отображает логические имена ресурсов (например, {@code "minecraft/sounds/...ogg"})
 * на их дескрипторы {@link AssetObject}.
 */
public final class AssetIndexContent {

    private final Map<String, AssetObject> objects;
    private final boolean virtual;

    public AssetIndexContent(Map<String, AssetObject> objects, boolean virtual) {
        this.objects = objects != null ? Map.copyOf(objects) : Map.of();
        this.virtual = virtual;
    }

    public Map<String, AssetObject> objects() {
        return Collections.unmodifiableMap(objects);
    }

    public boolean isVirtual() {
        return virtual;
    }

    public int size() {
        return objects.size();
    }

    @Override
    public String toString() {
        return "AssetIndexContent{objects=" + objects.size() + ", virtual=" + virtual + '}';
    }
}
