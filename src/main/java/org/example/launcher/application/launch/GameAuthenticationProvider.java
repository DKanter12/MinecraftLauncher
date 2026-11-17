package org.example.launcher.application.launch;

import java.util.Objects;
import java.util.UUID;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.infrastructure.elyby.ElyAuthService;

/**
 * Даёт построителю команды актуальные данные входа.
 * По {@link ElyAuthService} — только сессии, пароль сюда не попадает.
 */
public class GameAuthenticationProvider {

    private final ElyAuthService elyAuthService;

    public GameAuthenticationProvider(ElyAuthService elyAuthService) {
        this.elyAuthService = Objects.requireNonNull(elyAuthService, "elyAuthService");
    }

    /**
     * Возвращает данные входа для аккаунта: ник, UUID, токен, тип.
     * Офлайн-аккаунту UUID выводится детерминированно из ника.
     */
    public AuthData getAuthenticationData(GameProfile account) {
        Objects.requireNonNull(account, "account");
        String uuid = account.uuid()
                .filter(u -> !u.isBlank())
                .orElseGet(() -> offlineUuid(account.name()));
        return new AuthData(account.name(), uuid,
                account.accessToken().orElse("offline"),
                account.authType(),
                account.profileProperties().orElse("{}"));
    }

    /**
     * Обновляет сессию Ely.by перед запуском.
     * При любой ошибке возвращается исходный профиль (кэш).
     */
    public GameProfile refreshSession(GameProfile account) {
        Objects.requireNonNull(account, "account");
        if (!account.isElyBy()) {
            return account;
        }
        return elyAuthService.refreshProfile(account);
    }

    /** Детерминированный офлайн-UUID, как у официального лаунчера. */
    public static String offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + playerName).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }
}
