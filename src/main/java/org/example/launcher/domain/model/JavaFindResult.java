package org.example.launcher.domain.model;

import java.util.Optional;

/**
 * Результат попытки подобрать подходящую среду Java для
 * версии Minecraft.
 * <p>
 * Статус показывает, найдена ли среда, отсутствует или
 * найдена, но несовместима. Если среда найдена, метод {@link #runtime()}
 * содержит её, иначе метод {@link #reason()} поясняет проблему.
 */
public final class JavaFindResult {

    public enum Status {
        /** Подходящая среда Java найдена. */
        FOUND,
        /** Среда Java в системе вообще не найдена. */
        NOT_FOUND,
        /** Среды Java найдены, но ни одна не удовлетворяет требуемой версии. */
        INCOMPATIBLE
    }

    private final Status status;
    private final JavaRuntime runtime;
    private final String reason;

    private JavaFindResult(Status status, JavaRuntime runtime, String reason) {
        this.status = status;
        this.runtime = runtime;
        this.reason = reason;
    }

    public static JavaFindResult found(JavaRuntime runtime) {
        return new JavaFindResult(Status.FOUND, runtime, null);
    }

    public static JavaFindResult notFound(String reason) {
        return new JavaFindResult(Status.NOT_FOUND, null, reason);
    }

    public static JavaFindResult incompatible(String reason) {
        return new JavaFindResult(Status.INCOMPATIBLE, null, reason);
    }

    public Status status() {
        return status;
    }

    public Optional<JavaRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    @Override
    public String toString() {
        return "JavaFindResult{status=" + status
                + (runtime != null ? ", runtime=" + runtime : "")
                + (reason != null ? ", reason='" + reason + "'" : "") + '}';
    }
}
