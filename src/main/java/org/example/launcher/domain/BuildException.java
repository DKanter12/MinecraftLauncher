package org.example.launcher.domain;

/** Ошибки сборок: дубликаты, имена, отсутствие, переименование. */
public class BuildException extends LauncherException {

    public BuildException(ErrorCode code, String message) {
        super(code, message);
    }

    public BuildException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
