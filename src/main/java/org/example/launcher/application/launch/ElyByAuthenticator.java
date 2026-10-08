package org.example.launcher.application.launch;

import java.util.Objects;

import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.infrastructure.elyby.ElyAuthService;

/**
 * Авторизация Ely.by отдельно от объекта аккаунта.
 * Пароль используется только в момент входа и нигде не хранится.
 */
public class ElyByAuthenticator {

    private final ElyAuthService elyAuthService;

    public ElyByAuthenticator(ElyAuthService elyAuthService) {
        this.elyAuthService = Objects.requireNonNull(elyAuthService, "elyAuthService");
    }

    /**
     * Проверяет действительность сохранённой авторизации,
     * при необходимости обновляет её. Возвращает актуальный профиль.
     */
    public GameProfile authenticate(GameProfile account) {
        Objects.requireNonNull(account, "account");
        if (!account.isElyBy()) {
            return account;
        }
        return elyAuthService.refreshProfile(account);
    }

    /**
     * Обновляет истёкшую авторизацию.
     * При любой ошибке возвращается исходный профиль (кэш).
     */
    public GameProfile refresh(GameProfile account) {
        return authenticate(account);
    }
}
