package org.example.launcher.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.model.GameProfile;

/**
 * Аккаунты игроков без UI: валидация, persistence и выбор активного.
 * JavaFX остаётся в {@code MainView}, вся решающая логика — здесь,
 * чтобы её можно было тестировать без интерфейса.
 */
public class AccountManager {

    private final ProfileService profiles;

    public AccountManager(ProfileService profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    /** Все сохранённые аккаунты, пусто если файла ещё нет. */
    public List<GameProfile> load() throws IOException {
        return profiles.loadProfiles();
    }

    /**
     * Создаёт обычный аккаунт без пароля.
     * Дубликат по имени заменяется (как раньше в {@code ProfileService}),
     * поэтому метод никогда не сообщает «занято» — возвращает итоговый профиль.
     *
     * @throws IllegalArgumentException при невалидном нике
     */
    public GameProfile createOffline(String name) throws IOException {
        validateOfflineName(name);
        return profiles.addOfflineProfile(name.trim());
    }

    /**
     * Сохраняет только что залогиненный Ely.by профиль:
     * удаляет дубликат по uuid или имени, добавляет свежий.
     */
    public GameProfile saveAuthenticated(GameProfile authenticated) throws IOException {
        Objects.requireNonNull(authenticated, "authenticated");
        var all = new ArrayList<>(profiles.loadProfiles());
        all.removeIf(p -> isSameAccount(p, authenticated));
        all.add(authenticated);
        profiles.saveProfiles(all);
        return authenticated;
    }

    /** Удаляет аккаунт по имени. */
    public boolean delete(String name) throws IOException {
        return profiles.deleteProfile(name);
    }

    /**
     * Аккаунт для запуска: текущий, иначе первый сохранённый,
     * иначе авто {@code Player} (создаётся и сохраняется).
     */
    public GameProfile getOrCreateDefault(GameProfile current) throws IOException {
        if (current != null) {
            return current;
        }
        var saved = profiles.loadProfiles();
        if (!saved.isEmpty()) {
            return saved.get(0);
        }
        try {
            return profiles.addOfflineProfile("Player");
        } catch (IOException e) {
            return GameProfile.offline("Player");
        }
    }

    /**
     * Кого подсветить в комбобоксе: сначала по uuid текущего,
     * затем по сохранённому имени из настроек, затем первого.
     */
    public Optional<GameProfile> resolveSelection(
            List<GameProfile> all, String currentUuid, String savedName) {
        if (all == null || all.isEmpty()) {
            return Optional.empty();
        }
        if (currentUuid != null) {
            for (GameProfile p : all) {
                if (p.uuid().isPresent() && p.uuid().get().equals(currentUuid)) {
                    return Optional.of(p);
                }
            }
        }
        if (savedName != null) {
            for (GameProfile p : all) {
                if (p.name().equals(savedName)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.of(all.get(0));
    }

    /** Тот же Ely.by аккаунт (по uuid) или тёзка (по нику). */
    public static boolean isSameAccount(GameProfile a, GameProfile b) {
        if (a.uuid().isPresent() && b.uuid().isPresent()
                && a.uuid().get().equals(b.uuid().get())) {
            return true;
        }
        return a.name().equals(b.name());
    }

    /** Правила ника как в диалоге: непусто, ≤16, [a-zA-Z0-9_]. */
    public static void validateOfflineName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nickname is empty");
        }
        if (trimmed.length() > 16) {
            throw new IllegalArgumentException("Nickname is too long (max 16)");
        }
        if (!trimmed.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Nickname allows only a-z, A-Z, 0-9 and _");
        }
    }
}
