package org.example.launcher.model;

import java.util.Optional;

/**
 * Outcome of a launch attempt.
 */
public final class LaunchResult {

    public enum Status {
        SUCCESS,
        FILE_CHECK_FAILED,
        JAVA_NOT_FOUND,
        LAUNCH_FAILED
    }

    private final Status status;
    private final String message;
    private final MinecraftProcess process;

    private LaunchResult(Status status, String message, MinecraftProcess process) {
        this.status = status;
        this.message = message;
        this.process = process;
    }

    public static LaunchResult success(MinecraftProcess process) {
        return new LaunchResult(Status.SUCCESS, "Game launched successfully", process);
    }

    public static LaunchResult fileCheckFailed(String detail) {
        return new LaunchResult(Status.FILE_CHECK_FAILED, detail, null);
    }

    public static LaunchResult javaNotFound(String detail) {
        return new LaunchResult(Status.JAVA_NOT_FOUND, detail, null);
    }

    public static LaunchResult launchFailed(String detail) {
        return new LaunchResult(Status.LAUNCH_FAILED, detail, null);
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public Optional<MinecraftProcess> process() {
        return Optional.ofNullable(process);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public String toString() {
        return "LaunchResult{status=" + status + ", message='" + message + "'"
                + (process != null ? ", pid=" + process.pid() : "") + '}';
    }
}
