package org.example.launcher.distribution;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Авторизованная сессия с сервером лаунчера, возвращается после
 * входа по логину и паролю.
 *
 * <p>Лаунчер хранит эту запись постоянно (только токен — пароль
 * никуда не записывается) и прикладывает токен ко всем
 * последующим запросам к серверу, как веб-сессию.</p>
 *
 * @param accountName  отображаемое имя вошедшей учётной записи
 * @param role         назначенная сервером роль, определяет доступные функции
 * @param token        bearer-токен для последующих запросов (JWT-подобный)
 * @param serverUrl    базовый URL сервера лаунчера, к которому относится токен
 * @param expiresAtRaw опциональная метка истечения токена в формате ISO-8601
 */
public record ServerSession(
        String accountName,
        UserRole role,
        String token,
        String serverUrl,
        String expiresAtRaw) {

    public ServerSession {
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("accountName must not be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        // Пусто — анонимная сессия (публичные git-репозитории): bearer-заголовок
        // тогда не отправляется (см. GitHubBuildApi).
        if (token == null) {
            token = "";
        }
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl must not be blank");
        }
    }

    /** @return true, если сессия анонимная (без учётных данных). */
    public boolean isAnonymous() {
        return token.isBlank();
    }

    /** @return true, если сессия имеет роль ADMIN. */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** @return разобранная метка истечения, если есть. */
    public Optional<OffsetDateTime> expiresAt() {
        if (expiresAtRaw == null || expiresAtRaw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(expiresAtRaw));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** @return true, если у токена есть разобранная метка истечения в прошлом. */
    public boolean isExpired() {
        return expiresAt().map(expiry -> expiry.isBefore(OffsetDateTime.now()))
                .orElse(false);
    }
}
