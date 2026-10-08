package org.example.launcher.domain;

/** Ошибки скачивания файлов и проверки целостности. */
public class DownloadException extends LauncherException {

    public DownloadException(String message) {
        super(ErrorCode.DOWNLOAD_FAILED, message);
    }

    public DownloadException(ErrorCode code, String message) {
        super(code, message);
    }

    public DownloadException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
