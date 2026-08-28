package org.example.launcher.model;

import java.util.Optional;

/**
 * Java runtime requirements for a Minecraft version.
 */
public final class JavaVersion {

    private final String component;
    private final int majorVersion;

    public JavaVersion(String component, int majorVersion) {
        this.component = component;
        this.majorVersion = majorVersion;
    }

    /**
     * Mojang's runtime component identifier, e.g.
     * {@code "java-runtime-gamma"}.
     */
    public String component() {
        return component;
    }

    public Optional<String> componentOpt() {
        return Optional.ofNullable(component);
    }

    /**
     * Major Java version required, e.g. {@code 21}.
     */
    public int majorVersion() {
        return majorVersion;
    }

    @Override
    public String toString() {
        return "JavaVersion{component='" + component + "', major=" + majorVersion + '}';
    }
}
