package org.example.launcher.domain;

/** Ошибки поиска, совместимости и установки Java. */
public class JavaException extends LauncherException {

    public JavaException(ErrorCode code, String message) {
        super(code, message);
    }

    public JavaException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
