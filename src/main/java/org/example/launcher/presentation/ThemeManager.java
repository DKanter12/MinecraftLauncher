package org.example.launcher.presentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * Темы интерфейса лаунчера. Базовый {@code /styles.css} написан под
 * стандартную тёмно-зелёную палитру; пользовательская тема строится
 * из двух цветов (фон и акцент): остальные ~40 оттенков палитры
 * (поверхности, границы, тексты, варианты акцента) вычисляются из них
 * автоматически. CSS темы получается подстановкой цветов в текст
 * стандартного styles.css (двухфазная замена через токены — чтобы
 * исключить цепочки замен) и пишется в файл в каталоге данных.
 *
 * Все сцены применяют тему через {@link #apply}; смена темы
 * мгновенно перекрашивает открытые окна.
 */
public final class ThemeManager {

    // ------------------------------------------------------------
    //  Палитры
    // ------------------------------------------------------------

    /** Стандартные значения ключей палитры (совпадают с styles.css). */
    private static final Map<String, String> STANDARD = palette(
            "bg", "#101216",
            "bgDeep", "#171B21",
            "bgDeep2", "#181D24",
            "bgDeep3", "#1B2428",
            "bgAccentDeep", "#16201D",
            "sidebar", "#15191F",
            "card", "#1B2028",
            "card2", "#1E242D",
            "control", "#222833",
            "hover", "#262D38",
            "pressed", "#2E3644",
            "border", "#2B323D",
            "borderSoft", "#1A202A",
            "borderMid", "#20262F",
            "borderStrong", "#475263",
            "accent", "#10C58B",
            "accentHi", "#16D69A",
            "accentLo", "#0EA572",
            "accentDeep", "#0A8A62",
            "accentShadow", "#0C7039",
            "accentShadow2", "#0A563C",
            "onAccent", "#FFFFFF",
            "onAccentStrong", "#06251A",
            "text", "#F0F3F6",
            "text2", "#929BAA",
            "text2a", "#AEB6C1",
            "text2b", "#C9D1DB",
            "text2c", "#C2CAD5",
            "text3", "#626C7A",
            "link", "#6FB8F5",
            "danger", "#FF4A6E",
            "dangerSoft", "#FF8CA3",
            "dangerSoft2", "#FFA9BB",
            "dangerSoft3", "#FFD5DD",
            "catViolet", "#C796F9",
            "catOrange", "#F99E6B",
            "catIndigo", "#959EEF",
            "catTan", "#DBB69B");

    /** Стандартный фон и акцент — стартовые значения пикеров. */
    public static final String DEFAULT_BG = "#101216";
    public static final String DEFAULT_ACCENT = "#10C58B";

    private static Map<String, String> palette(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------
    //  Вычисление палитры из двух цветов
    // ------------------------------------------------------------

    /**
     * Строит полную палитру из фона и акцента. Для тёмного фона
     * поверхности светлеют, для светлого — темнеют; тексты
     * контрастны фону; варианты акцента темнеют к тени.
     */
    public static Map<String, String> derivePalette(String bgHex,
            String accentHex) {
        String bg = normalizeHex(bgHex) != null
                ? normalizeHex(bgHex) : DEFAULT_BG;
        String accent = normalizeHex(accentHex) != null
                ? normalizeHex(accentHex) : DEFAULT_ACCENT;
        boolean darkBg = luminance(bg) < 0.5;
        // Направление лестницы поверхностей: от фона к тексту.
        String text = darkBg ? "#F0F3F6" : "#1A2129";
        String toward = darkBg ? "#FFFFFF" : "#000000";

        Map<String, String> p = new LinkedHashMap<>();
        p.put("bg", bg);
        p.put("bgDeep", mix(bg, toward, 0.025));
        p.put("bgDeep2", mix(bg, toward, 0.03));
        p.put("bgDeep3", mix(bg, toward, 0.035));
        p.put("bgAccentDeep", mix(bg, accent, 0.06));
        p.put("sidebar", mix(bg, toward, 0.03));
        p.put("card", mix(bg, toward, 0.06));
        p.put("card2", mix(bg, toward, 0.08));
        p.put("control", mix(bg, toward, 0.10));
        p.put("hover", mix(bg, toward, 0.13));
        p.put("pressed", mix(bg, toward, 0.16));
        p.put("border", mix(bg, toward, 0.11));
        p.put("borderSoft", mix(bg, toward, 0.04));
        p.put("borderMid", mix(bg, toward, 0.07));
        p.put("borderStrong", mix(bg, toward, 0.22));
        p.put("accent", accent);
        p.put("accentHi", mix(accent, toward, 0.12));
        p.put("accentLo", mix(accent, darkBg ? "#000000" : "#FFFFFF",
                0.10));
        p.put("accentDeep", mix(accent, "#000000", 0.28));
        p.put("accentShadow", mix(accent, "#000000", 0.45));
        p.put("accentShadow2", mix(accent, "#000000", 0.58));
        p.put("onAccent", luminance(accent) > 0.8
                ? "#17191C" : "#FFFFFF");
        p.put("onAccentStrong", mix(accent, "#000000", 0.88));
        p.put("text", text);
        p.put("text2", mix(text, bg, 0.45));
        p.put("text2a", mix(text, bg, 0.33));
        p.put("text2b", mix(text, bg, 0.20));
        p.put("text2c", mix(text, bg, 0.10));
        p.put("text3", mix(text, bg, 0.62));
        p.put("link", mix(darkBg ? "#6FB8F5" : "#2563EB", accent, 0.3));
        String danger = darkBg ? "#FF4A6E" : "#E11D48";
        p.put("danger", danger);
        p.put("dangerSoft", mix(danger, "#FFFFFF", 0.35));
        p.put("dangerSoft2", mix(danger, "#FFFFFF", 0.50));
        p.put("dangerSoft3", mix(danger, "#FFFFFF", 0.70));
        p.put("catViolet", darkBg ? "#C796F9" : "#8B3FC9");
        p.put("catOrange", darkBg ? "#F99E6B" : "#D97A2E");
        p.put("catIndigo", darkBg ? "#959EEF" : "#5B63E0");
        p.put("catTan", darkBg ? "#DBB69B" : "#B07E52");
        return p;
    }

    /** Шесть образцов для превью: фон, панель, карточка, контрол, hover, акцент. */
    public static List<String> previewSwatches(String bgHex,
            String accentHex) {
        Map<String, String> p = derivePalette(bgHex, accentHex);
        List<String> out = new ArrayList<>();
        for (String key : List.of("bg", "sidebar", "card", "control",
                "hover", "accent")) {
            out.add(p.get(key));
        }
        return out;
    }

    // ------------------- Цветовая математика -------------------

    /** Смешивает два цвета: t=0 — первый, t=1 — второй. */
    private static String mix(String aHex, String bHex, double t) {
        int[] a = toRgb(aHex);
        int[] b = toRgb(bHex);
        int[] c = new int[3];
        for (int i = 0; i < 3; i++) {
            c[i] = (int) Math.round(a[i] + (b[i] - a[i]) * t);
        }
        return String.format("#%02X%02X%02X", c[0], c[1], c[2]);
    }

    /** Относительная яркость (0..1) по Rec. 709. */
    private static double luminance(String hex) {
        int[] rgb = toRgb(hex);
        return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2])
                / 255.0;
    }

    private static int[] toRgb(String hex) {
        String h = normalizeHex(hex);
        if (h == null || h.length() != 7) {
            return new int[] {0, 0, 0};
        }
        try {
            return new int[] {
                    Integer.parseInt(h.substring(1, 3), 16),
                    Integer.parseInt(h.substring(3, 5), 16),
                    Integer.parseInt(h.substring(5, 7), 16),
            };
        } catch (NumberFormatException e) {
            return new int[] {0, 0, 0};
        }
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("#") && (v.length() == 7 || v.length() == 9)) {
            return v.toUpperCase();
        }
        return null;
    }

    private static String toRgbTriplet(String value) {
        int[] rgb = toRgb(value);
        return rgb[0] + ", " + rgb[1] + ", " + rgb[2];
    }

    // ------------------------------------------------------------
    //  Состояние и применение
    // ------------------------------------------------------------

    private static volatile boolean custom;
    private static volatile String customBg;
    private static volatile String customAccent;
    private static volatile String currentUrl;
    private static Path dataDir;
    private static Path generatedFile;

    private ThemeManager() {
    }

    /**
     * Инициализация до создания первой сцены: каталог данных лаунчера.
     * Сама тема включается отдельно — setStandard или setCustom.
     */
    public static synchronized void initialize(Path launcherDataDir) {
        if (launcherDataDir != null) {
            dataDir = launcherDataDir;
            generatedFile = launcherDataDir.resolve("launcher_theme.css");
        }
        setStandard();
    }

    /** true, если сейчас включена пользовательская тема. */
    public static boolean isCustom() {
        return custom;
    }

    /** Цвет фона пользовательской темы (или null, если стандартная). */
    public static String customBg() {
        return custom ? customBg : null;
    }

    /** Цвет акцента пользовательской темы (или null). */
    public static String customAccent() {
        return custom ? customAccent : null;
    }

    /** Включает стандартную тему и перекрашивает открытые окна. */
    public static synchronized void setStandard() {
        custom = false;
        customBg = null;
        customAccent = null;
        currentUrl = standardUrl();
        restyleAll();
    }

    /**
     * Включает пользовательскую тему из двух цветов и мгновенно
     * применяет её ко всем открытым окнам.
     */
    public static synchronized void setCustom(String bg, String accent) {
        Map<String, String> palette = derivePalette(bg, accent);
        try {
            Files.writeString(generatedFile, generateCss(palette),
                    StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException e) {
            // Не удалось сгенерировать — откатываемся на стандартную.
            setStandard();
            return;
        }
        custom = true;
        customBg = normalizeHex(bg);
        customAccent = normalizeHex(accent);
        currentUrl = fileUrl(generatedFile);
        restyleAll();
    }

    private static void restyleAll() {
        for (Window w : Window.getWindows()) {
            Scene scene = w.getScene();
            if (scene != null) {
                restyle(scene);
            }
        }
    }

    /** Добавляет текущую тему к сцене (вызывается при её создании). */
    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        String url = currentUrl;
        if (url != null && !scene.getStylesheets().contains(url)) {
            scene.getStylesheets().add(0, url);
        }
    }

    private static void restyle(Scene scene) {
        String url = currentUrl;
        var sheets = scene.getStylesheets();
        sheets.removeIf(s -> s.startsWith("file:") && s.endsWith(".css")
                && s.contains("launcher_theme")
                || s.contains("/styles.css"));
        if (url != null && !sheets.contains(url)) {
            sheets.add(0, url);
        }
        var root = scene.getRoot();
        if (root != null) {
            root.applyCss();
        }
    }

    private static String standardUrl() {
        var css = ThemeManager.class.getResource("/styles.css");
        return css != null ? css.toExternalForm() : null;
    }

    private static String fileUrl(Path file) {
        try {
            return file.toUri().toURL().toExternalForm();
        } catch (IOException | UncheckedIOException e) {
            return standardUrl();
        }
    }

    /** Подставляет палитру темы в текст стандартного styles.css. */
    static String generateCss(Map<String, String> palette)
            throws IOException {
        String css;
        var resource = ThemeManager.class.getResource("/styles.css");
        if (resource == null) {
            throw new IOException("styles.css not found");
        }
        try (var in = resource.openStream()) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Фаза 1: стандартные значения -> токены (без цепочек замен).
        // Для rgba-контекста — отдельный RGB-токен: внутри rgba()
        // допустимы только числа, а не hex.
        for (Map.Entry<String, String> e : STANDARD.entrySet()) {
            String hex = normalizeHex(e.getValue());
            if (hex != null) {
                css = Pattern.compile(Pattern.quote(hex),
                        Pattern.CASE_INSENSITIVE).matcher(css)
                        .replaceAll(Matcher.quoteReplacement(
                                "@@" + e.getKey() + "@@"));
            }
            int[] rgb = toRgb(e.getValue());
            if (rgb[0] != 0 || rgb[1] != 0 || rgb[2] != 0) {
                String triplet = "\\s*" + rgb[0] + "\\s*,\\s*" + rgb[1]
                        + "\\s*,\\s*" + rgb[2] + "\\s*,";
                css = Pattern.compile("rgba\\(" + triplet)
                        .matcher(css)
                        .replaceAll(Matcher.quoteReplacement("rgba(@@"
                                + e.getKey() + "RGB@@,"));
            }
        }
        // Фаза 2: токены -> значения палитры простой строковой заменой.
        for (Map.Entry<String, String> e : STANDARD.entrySet()) {
            String value = palette.getOrDefault(e.getKey(), e.getValue());
            css = css.replace("@@" + e.getKey() + "@@", value);
            css = css.replace("@@" + e.getKey() + "RGB@@",
                    toRgbTriplet(value));
        }
        return css;
    }
}
