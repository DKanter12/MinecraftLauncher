package org.example.launcher.model;

import java.util.Collections;
import java.util.Map;

/**
 * Parsed content of a Mojang asset index JSON file.
 * <p>
 * Maps asset logical names (e.g. {@code "minecraft/sounds/...ogg"})
 * to their {@link AssetObject} descriptors.
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
