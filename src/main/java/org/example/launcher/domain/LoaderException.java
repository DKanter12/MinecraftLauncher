package org.example.launcher.domain;

/** Ошибки загрузчиков: несовместимость и провал установки. */
public class LoaderException extends LauncherException {

    public LoaderException(ErrorCode code, String message) {
        super(code, message);
    }

    public LoaderException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
