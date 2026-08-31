package org.example.launcher.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A player profile stored locally by the launcher.
 * <p>
 * Profiles contain the player's display name, UUID (if authenticated),
 * access token for premium authentication, and optional skin data.
 */
public final class GameProfile {

    public enum AuthType {
        OFFLINE,
        ELY_BY
    }

    private final String name;
    private final String uuid;
    private final String accessToken;
    private final String clientToken;
    private final boolean online;
    private final AuthType authType;
    private final String skinUrl;
    private final String skinModel;
    private final String profileProperties;

    public GameProfile(String name, String uuid, String accessToken, boolean online) {
        this(name, uuid, accessToken, null, online,
                online ? AuthType.ELY_BY : AuthType.OFFLINE, null, null, null);
    }

    public GameProfile(String name, String uuid, String accessToken, String clientToken,
                       boolean online, AuthType authType, String skinUrl, String skinModel,
                       String profileProperties) {
        this.name = Objects.requireNonNull(name, "name");
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.clientToken = clientToken;
        this.online = online;
        this.authType = authType != null ? authType : (online ? AuthType.ELY_BY : AuthType.OFFLINE);
        this.skinUrl = skinUrl;
        this.skinModel = skinModel;
        this.profileProperties = profileProperties;
    }

    public static GameProfile offline(String name) {
        return new GameProfile(name, null, null, null, false, AuthType.OFFLINE, null, null, null);
    }

    public static GameProfile elyBy(String name, String uuid, String accessToken,
                                     String clientToken, String skinUrl, String skinModel,
                                     String profileProperties) {
        return new GameProfile(name, uuid, accessToken, clientToken, true, AuthType.ELY_BY,
                skinUrl, skinModel, profileProperties);
    }

    public String name() {
        return name;
    }

    public Optional<String> uuid() {
        return Optional.ofNullable(uuid);
    }

    public Optional<String> accessToken() {
        return Optional.ofNullable(accessToken);
    }

    public Optional<String> clientToken() {
        return Optional.ofNullable(clientToken);
    }

    public boolean isOnline() {
        return online;
    }

    public AuthType authType() {
        return authType;
    }

    public boolean isElyBy() {
        return authType == AuthType.ELY_BY;
    }

    public Optional<String> skinUrl() {
        return Optional.ofNullable(skinUrl);
    }

    public Optional<String> skinModel() {
        return Optional.ofNullable(skinModel);
    }

    public Optional<String> profileProperties() {
        return Optional.ofNullable(profileProperties);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GameProfile that)) return false;
        if (uuid != null && that.uuid != null) {
            return uuid.equals(that.uuid);
        }
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : name.hashCode();
    }

    @Override
    public String toString() {
        return "GameProfile{name='" + name + "', authType=" + authType
                + (uuid != null ? ", uuid=" + uuid : "")
                + (skinUrl != null ? ", skin=" + skinUrl : "") + '}';
    }
}
