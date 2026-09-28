package org.example.launcher.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Представляет среду Java, обнаруженную в локальной системе.
 * <p>
 * Экземпляры создаются реализациями {@code JavaDetector} и
 * используются службой {@code JavaResolutionService} для выбора подходящей
 * среды запуска указанной версии Minecraft.
 */
public final class JavaRuntime {

    /**
     * Место обнаружения среды.
     */
    public enum Source {
        /** Обнаружена через переменную окружения {@code JAVA_HOME}. */
        JAVA_HOME,
        /** Обнаружена при сканировании системного {@code PATH}. */
        PATH,
        /** Обнаружена через реестр Windows. */
        REGISTRY,
        /** Обнаружена в общеизвестном каталоге установки. */
        COMMON_LOCATION,
        /** Установлена лаунчером в управляемый каталог сред. */
        MANAGED,
        /** Явно указана пользователем. */
        CUSTOM
    }

    private final Path javaExecutable;
    private final int majorVersion;
    private final Source source;
    private final String vendor;

    public JavaRuntime(Path javaExecutable, int majorVersion, Source source, String vendor) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable);
        this.majorVersion = majorVersion;
        this.source = Objects.requireNonNull(source);
        this.vendor = vendor;
    }

    public JavaRuntime(Path javaExecutable, int majorVersion, Source source) {
        this(javaExecutable, majorVersion, source, null);
    }

    /** Абсолютный путь к исполняемому файлу {@code java} (или {@code java.exe}). */
    public Path javaExecutable() {
        return javaExecutable;
    }

    /** Мажорная версия Java, например {@code 17} или {@code 21}. */
    public int majorVersion() {
        return majorVersion;
    }

    public Source source() {
        return source;
    }

    public Optional<String> vendor() {
        return Optional.ofNullable(vendor);
    }

    /**
     * Проверяет, удовлетворяет ли среда требуемой минимальной мажорной версии.
     */
    public boolean satisfies(int requiredMajor) {
        return majorVersion >= requiredMajor;
    }

    @Override
    public String toString() {
        return "JavaRuntime{exe=" + javaExecutable + ", major=" + majorVersion
                + ", source=" + source
                + (vendor != null ? ", vendor='" + vendor + "'" : "") + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JavaRuntime other)) return false;
        return majorVersion == other.majorVersion
                && source == other.source
                && javaExecutable.equals(other.javaExecutable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaExecutable, majorVersion, source);
    }
}
