package org.example.launcher.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThemeManager: генерация CSS темы из двух цветов")
class ThemeManagerTest {

    private static Map<String, String> derived(String bg, String accent) {
        return ThemeManager.derivePalette(bg, accent);
    }

    @Test
    @DisplayName("Палитра выводит поверхности, текст и варианты акцента")
    void deriveProducesAllKeys() {
        Map<String, String> p = derived("#101216", "#10C58B");
        String keys = "bg,bgDeep,bgDeep2,bgDeep3,bgAccentDeep,sidebar,"
                + "card,card2,control,hover,pressed,border,borderSoft,"
                + "borderMid,borderStrong,accent,accentHi,accentLo,"
                + "accentDeep,accentShadow,accentShadow2,onAccent,"
                + "onAccentStrong,text,text2,text2a,text2b,text2c,text3,"
                + "link,danger,dangerSoft,dangerSoft2,dangerSoft3,"
                + "catViolet,catOrange,catIndigo,catTan";
        for (String key : keys.split(",")) {
            assertTrue(p.containsKey(key.trim()), "нет ключа " + key.trim());
        }
        assertEquals("#10C58B", p.get("accent"));
        // Поверхности светлее фона (тёмный фон).
        assertFalse(p.get("card").equals(p.get("bg")));
    }

    @Test
    @DisplayName("Стандартные входные цвета дают иерархию поверхностей")
    void deriveMatchesStandard() {
        Map<String, String> p = derived(ThemeManager.DEFAULT_BG,
                ThemeManager.DEFAULT_ACCENT);
        // Лестница поверхностей светлеет от фона к контролам.
        assertTrue(lum(p.get("card")) > lum(p.get("bg")));
        assertTrue(lum(p.get("control")) > lum(p.get("card")));
        assertTrue(lum(p.get("hover")) > lum(p.get("control")));
        // Текст почти белый на тёмном фоне.
        assertTrue(lum(p.get("text")) > 0.85);
        assertEquals("#FFFFFF", p.get("onAccent"));
        assertEquals("#10C58B", p.get("accent"));
    }

    private static double lum(String hex) {
        int[] rgb = ThemeManagerTestRgb.of(hex);
        return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2])
                / 255.0;
    }

    /** Мини-парсер hex для проверок яркости. */
    static final class ThemeManagerTestRgb {
        private ThemeManagerTestRgb() {
        }

        static int[] of(String hex) {
            String h = hex.toUpperCase().replace("#", "");
            return new int[] {
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16),
            };
        }
    }

    @Test
    @DisplayName("Светлый фон даёт тёмный текст и тёмные поверхности")
    void lightBackgroundDerivation() {
        Map<String, String> p = derived("#F4F6F9", "#0EA572");
        assertTrue(p.get("text").toUpperCase().startsWith("#1A"));
        // Поверхности темнеют от светлого фона.
        assertTrue(lum(p.get("card")) < lum("#F4F6F9"));
        assertTrue(lum(p.get("control")) < lum(p.get("card")));
        // onAccent для зелёного акцента средней яркости — белый.
        assertEquals("#FFFFFF", p.get("onAccent"));
    }

    @Test
    @DisplayName("Некорректные цвета откатываются на значения по умолчанию")
    void invalidColorsFallBack() {
        Map<String, String> p = derived("not-a-color", null);
        assertEquals(ThemeManager.DEFAULT_BG, p.get("bg"));
        assertEquals(ThemeManager.DEFAULT_ACCENT, p.get("accent"));
    }

    private static String css(Map<String, String> palette)
            throws IOException {
        return ThemeManager.generateCss(palette);
    }

    @Test
    @DisplayName("CSS темы не содержит незаменённых токенов")
    void generatedCssIsComplete() throws IOException {
        String out = css(derived("#101216", "#E11D48"));
        assertFalse(out.contains("@@"), "остались токены фазы замены");
        // Внутри rgba() обязаны быть числа, а не hex.
        assertFalse(
                java.util.regex.Pattern.compile(
                        "rgba\\(#[0-9A-Fa-f]+").matcher(out).find(),
                "hex внутри rgba()");
    }

    @Test
    @DisplayName("Цвета палитры попадают в CSS")
    void paletteColorsReachCss() throws IOException {
        String out = css(derived("#101216", "#E11D48"));
        assertTrue(out.toUpperCase().contains("#E11D48"));
        assertFalse(out.toUpperCase().contains("#10C58B"),
                "стандартный акцент заменён");
    }

    @Test
    @DisplayName("rgba-триплет акцента заменён числами")
    void accentRgbaReplaced() throws IOException {
        String out = css(derived("#101216", "#3B82F6"));
        assertFalse(out.contains("16, 197, 139"));
        assertTrue(out.contains("59, 130, 246")); // #3B82F6
    }
}
