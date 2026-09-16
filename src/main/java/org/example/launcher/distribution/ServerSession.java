package org.example.launcher.distribution;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * An authorized session with the launcher server, returned after the
 * account signs in with login and password.
 *
 * <p>The launcher stores this record persistently (token only — the
 * password is never written anywhere) and attaches the token to all
 * subsequent server requests, exactly like a web session.</p>
 *
 * @param accountName  display name of the signed-in account
 * @param role         server-assigned role, gates available features
 * @param token        bearer token for subsequent requests (JWT-like)
 * @param serverUrl    base URL of the launcher server the token belongs to
 * @param expiresAtRaw optional ISO-8601 expiry timestamp of the token
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
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl must not be blank");
        }
    }

    /** @return true when this session carries the ADMIN role. */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** @return parsed expiry timestamp, when present. */
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

    /** @return true when the token has a parseable expiry in the past. */
    public boolean isExpired() {
        return expiresAt().map(expiry -> expiry.isBefore(OffsetDateTime.now()))
                .orElse(false);
    }
}
