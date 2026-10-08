package org.example.launcher.i18n;

import java.io.IOException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.example.launcher.infrastructure.settings.FileSettingsRepository;

/**
 * Язык интерфейса лаунчера: английский и русский. Все видимые пользователю
 * строки проходят через {@link #tr} и хранятся в {@code messages*.properties};
 * технические идентификаторы (идентификаторы версий, имена файлов, имена загрузчиков,
 * URL) никогда не переводятся.
 * <p>
 * Смена языка применяется немедленно: диалоги читают строки при
 * создании, а главное окно перерисовывается через
 * {@code applyLanguage}.
 */
public final class Lang {

    public enum Language {
        ENGLISH("en", "English"),
        RUSSIAN("ru", "Русский");

        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        /** Код языка в настройках ("en"/"ru"). */
        public String code() {
            return code;
        }

        /** Имя в выборе языка. */
        public String displayName() {
            return displayName;
        }

        public static Language fromCode(String code) {
            if (code != null) {
                for (Language language : values()) {
                    if (language.code.equalsIgnoreCase(code.trim())) {
                        return language;
                    }
                }
            }
            return systemDefault();
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static volatile Language current = systemDefault();

    private Lang() {
    }

    /** Локаль ОС на русском → русский, остальные → английский. */
    public static Language systemDefault() {
        try {
            if ("ru".equalsIgnoreCase(
                    Locale.getDefault().getLanguage())) {
                return Language.RUSSIAN;
            }
        } catch (RuntimeException ignored) {
            // перейти к английскому
        }
        return Language.ENGLISH;
    }

    public static void setLanguage(Language language) {
        current = language != null ? language : systemDefault();
    }

    public static Language getLanguage() {
        return current;
    }

    /**
     * @param key ключ сообщения из {@code messages.properties}
     * @param args необязательные аргументы {@link String#format}
     * @return переведённый (и отформатированный) текст; сам ключ
     *         при отсутствии, чтобы интерфейс не ломался
     */
    public static String tr(String key, Object... args) {
        String pattern;
        try {
            Locale locale = current == Language.RUSSIAN
                    ? Locale.forLanguageTag("ru") : Locale.ENGLISH;
            pattern = ResourceBundle.getBundle("messages", locale)
                    .getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException e) {
            return pattern;
        }
    }

    /** Восстанавливает сохранённый язык (или системный по умолчанию). */
    public static void load(FileSettingsRepository preferences) {
        if (preferences == null) {
            setLanguage(systemDefault());
            return;
        }
        try {
            setLanguage(preferences.getLanguage()
                    .map(Language::fromCode)
                    .orElseGet(Lang::systemDefault));
        } catch (IOException e) {
            setLanguage(systemDefault());
        }
    }

    /** Сохраняет текущий язык. */
    public static void save(FileSettingsRepository preferences) throws IOException {
        preferences.setLanguage(current.code());
    }
}
