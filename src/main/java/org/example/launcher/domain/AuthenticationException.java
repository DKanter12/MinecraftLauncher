package org.example.launcher.domain;

/** Ошибки аккаунтов и авторизации (пароль сюда никогда не попадает). */
public class AuthenticationException extends LauncherException {

    public AuthenticationException(ErrorCode code, String message) {
        super(code, message);
    }

    public AuthenticationException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
