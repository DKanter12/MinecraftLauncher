package org.example.launcher.domain.model;

import java.util.Optional;

/**
 * Результат попытки подбора подходящей среды Java для
 * версии Minecraft.
 * <p>
 * Статус показывает, найдена ли среда, отсутствует или
 * найдена, но несовместима. Если среда найдена, метод {@link #runtime()}
 * содержит её, иначе метод {@link #reason()} поясняет проблему.
 */
public final class JavaResolutionResult {

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

    private JavaResolutionResult(Status status, JavaRuntime runtime, String reason) {
        this.status = status;
        this.runtime = runtime;
        this.reason = reason;
    }

    public static JavaResolutionResult found(JavaRuntime runtime) {
        return new JavaResolutionResult(Status.FOUND, runtime, null);
    }

    public static JavaResolutionResult notFound(String reason) {
        return new JavaResolutionResult(Status.NOT_FOUND, null, reason);
    }

    public static JavaResolutionResult incompatible(String reason) {
        return new JavaResolutionResult(Status.INCOMPATIBLE, null, reason);
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
        return "JavaResolutionResult{status=" + status
                + (runtime != null ? ", runtime=" + runtime : "")
                + (reason != null ? ", reason='" + reason + "'" : "") + '}';
    }
}
