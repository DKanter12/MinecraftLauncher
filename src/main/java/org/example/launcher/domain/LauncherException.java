package org.example.launcher.domain;

/**
 * Базовая ошибка лаунчера. Всегда несёт стабильный {@link ErrorCode},
 * поэтому UI может показать понятный текст без разбора строк.
 */
public class LauncherException extends Exception {

    private final ErrorCode code;

    public LauncherException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LauncherException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** Стабильный код ошибки для маппинга в UI. */
    public ErrorCode code() {
        return code;
    }
}
