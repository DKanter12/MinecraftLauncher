package org.example.launcher.model;

import java.util.Optional;

/**
 * Result of attempting to resolve a suitable Java runtime for a
 * Minecraft version.
 * <p>
 * The status indicates whether a runtime was found, is missing, or was
 * found but is incompatible. When a runtime is found, {@link #runtime()}
 * contains it; otherwise {@link #reason()} explains the problem.
 */
public final class JavaResolutionResult {

    public enum Status {
        /** A suitable Java runtime was found. */
        FOUND,
        /** No Java runtime was found on the system at all. */
        NOT_FOUND,
        /** Java runtimes were found but none satisfy the required version. */
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
