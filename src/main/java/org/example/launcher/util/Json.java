package org.example.launcher.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Небольшие помощники для безопасного чтения значений {@link JsonObject} при возможных null.
 * <p>
 * Полезные нагрузки Mojang, загрузчиков модов и дистрибутивов слабо типизированы:
 * отсутствующие ключи, явные null и неожиданные типы никогда не должны
 * ломать разбор — вместо этого они дают {@code null} / значения по умолчанию.
 */
public final class Json {

    private Json() {
    }

    /**
     * @return обрезанное строковое значение либо {@code null}, если отсутствует, равно null или не примитив.
     */
    public static String getStringOrNull(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * @return строковое значение либо {@code fallback}, если отсутствует.
     */
    public static String getStringOrDefault(JsonObject obj, String key, String fallback) {
        String value = getStringOrNull(obj, key);
        return value != null ? value : fallback;
    }

    /**
     * @throws IllegalArgumentException если ключ отсутствует или пуст.
     */
    public static String getRequiredString(JsonObject obj, String key) {
        String value = getStringOrNull(obj, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required JSON string: " + key);
        }
        return value;
    }
}
