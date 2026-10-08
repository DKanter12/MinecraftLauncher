package org.example.launcher.domain;

/** Ошибки самообновления лаунчера. */
public class UpdateException extends LauncherException {

    public UpdateException(ErrorCode code, String message) {
        super(code, message);
    }

    public UpdateException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
