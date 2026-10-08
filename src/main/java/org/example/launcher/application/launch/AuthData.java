package org.example.launcher.application.launch;

import java.util.Objects;

import org.example.launcher.domain.model.GameProfile;

/**
 * Данные аккаунта, необходимые Minecraft для входа в игру.
 * Пароля здесь нет и быть не должно.
 */
public record AuthData(
        String username,
        String uuid,
        String accessToken,
        GameProfile.AuthType accountType,
        String profileProperties) {

    public AuthData {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(accountType, "accountType");
    }

    /**
     * Пересобирает профиль с теми же полями, которые реально использует
     * построитель команды (ник, UUID, токен, тип, свойства).
     * Скины и клиентский токен в команду не попадают.
     * Офлайн-UUID построитель выводит из ника детерминированно,
     * поэтому достаточно фабрики {@code offline}.
     */
    public GameProfile toGameProfile() {
        if (accountType == GameProfile.AuthType.ELY_BY) {
            return GameProfile.elyBy(username, uuid,
                    accessToken != null ? accessToken : "offline",
                    null, null, null, profileProperties);
        }
        return GameProfile.offline(username);
    }
}
