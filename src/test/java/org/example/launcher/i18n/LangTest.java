package org.example.launcher.i18n;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.infrastructure.settings.FileSettingsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangTest {

    /** Exact keys of a bundle file (no parent fallback). */
    private static Set<String> ownKeys(String resource) throws Exception {
        Properties props = new Properties();
        try (var in = LangTest.class.getResourceAsStream(resource)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props.stringPropertyNames();
    }

    private static Set<String> bundleKeys(Locale locale) {
        return ResourceBundle.getBundle("messages", locale).keySet();
    }

    @Test
    void englishAndRussianHaveSameKeys() throws Exception {
        assertEquals(ownKeys("/messages.properties"),
                ownKeys("/messages_ru.properties"));
        assertEquals(ownKeys("/messages.properties"),
                ownKeys("/messages_en.properties"));
    }

    @Test
    void trFormatsArgs() {
        Lang.setLanguage(Lang.Language.ENGLISH);
        try {
            assertEquals("Installing 5 files",
                    Lang.tr("install.files", 5));
            Lang.setLanguage(Lang.Language.RUSSIAN);
            assertEquals("Установка файлов: 5",
                    Lang.tr("install.files", 5));
        } finally {
            Lang.setLanguage(Lang.Language.ENGLISH);
        }
    }

    @Test
    void unknownKeyReturnsKey() {
        Lang.setLanguage(Lang.Language.ENGLISH);
        assertEquals("no.such.key", Lang.tr("no.such.key"));
    }

    @Test
    void fromCodeFallsBackToSystem() {
        assertEquals(Lang.Language.ENGLISH, Lang.Language.fromCode("en"));
        assertEquals(Lang.Language.RUSSIAN, Lang.Language.fromCode("ru"));
        assertEquals(Lang.Language.RUSSIAN, Lang.Language.fromCode("RU"));
        assertEquals(Lang.systemDefault(), Lang.Language.fromCode("xx"));
        assertEquals(Lang.systemDefault(), Lang.Language.fromCode(null));
    }

    @Test
    void savesAndLoads(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("prefs.json");
        FileSettingsRepository prefs = new FileSettingsRepository(file);

        Lang.setLanguage(Lang.Language.RUSSIAN);
        try {
            Lang.save(prefs);
            assertEquals("ru", prefs.getLanguage().orElseThrow());

            Lang.setLanguage(Lang.Language.ENGLISH);
            Lang.load(prefs);
            assertEquals(Lang.Language.RUSSIAN, Lang.getLanguage());
        } finally {
            Lang.setLanguage(Lang.Language.ENGLISH);
        }
    }

    @Test
    void russianTranslationsArePresent() {
        Lang.setLanguage(Lang.Language.RUSSIAN);
        try {
            assertEquals("Играть", Lang.tr("button.play"));
            assertEquals("Мои инстансы", Lang.tr("nav.instances"));
            assertEquals("Добавить инстанс", Lang.tr("instances.add"));
        } finally {
            Lang.setLanguage(Lang.Language.ENGLISH);
        }
    }

    @Test
    void everyKeyResolvesInBothLanguages() {
        for (String key : bundleKeys(Locale.ENGLISH)) {
            Lang.setLanguage(Lang.Language.ENGLISH);
            String en = Lang.tr(key);
            Lang.setLanguage(Lang.Language.RUSSIAN);
            String ru = Lang.tr(key);
            assertTrue(en != null && !en.isBlank(), key);
            assertTrue(ru != null && !ru.isBlank(), key);
        }
        Lang.setLanguage(Lang.Language.ENGLISH);
    }
}
