package org.example.launcher.domain;

/** Ошибки запуска игры и мониторинга процесса. */
public class LaunchException extends LauncherException {

    public LaunchException(ErrorCode code, String message) {
        super(code, message);
    }

    public LaunchException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
