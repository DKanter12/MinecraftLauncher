package org.example.launcher.domain;

/**
 * Стабильные коды ошибок. UI маппит код в человеческий текст,
 * техническая строка наружу не выходит.
 */
public enum ErrorCode {
    BUILD_ALREADY_EXISTS,
    BUILD_NOT_FOUND,
    INVALID_BUILD_NAME,
    VERSION_NOT_FOUND,
    DOWNLOAD_FAILED,
    CHECKSUM_MISMATCH,
    INVALID_NICKNAME,
    AUTH_FAILED,
    SESSION_EXPIRED,
    JAVA_NOT_FOUND,
    JAVA_INCOMPATIBLE,
    JAVA_INSTALL_FAILED,
    LOADER_INCOMPATIBLE,
    LOADER_INSTALL_FAILED,
    LAUNCH_FAILED,
    UPDATE_FAILED,
    NETWORK_UNAVAILABLE,
    STORAGE_ERROR
}
