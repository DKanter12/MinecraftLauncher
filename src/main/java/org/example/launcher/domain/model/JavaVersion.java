package org.example.launcher.domain.model;

/**
 * Требования к среде Java для версии Minecraft.
 */
public final class JavaVersion {

    private final String component;
    private final int majorVersion;

    public JavaVersion(String component, int majorVersion) {
        this.component = component;
        this.majorVersion = majorVersion;
    }

    /**
     * Идентификатор компонента среды Mojang, например
     * {@code "java-runtime-gamma"}.
     */
    public String component() {
        return component;
    }

    /**
     * Требуемая мажорная версия Java, например {@code 21}.
     */
    public int majorVersion() {
        return majorVersion;
    }

    @Override
    public String toString() {
        return "JavaVersion{component='" + component + "', major=" + majorVersion + '}';
    }
}
