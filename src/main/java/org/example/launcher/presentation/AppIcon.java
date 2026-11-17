package org.example.launcher.presentation;

import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Иконка приложения MDD: один ресурс {@code /icon.png}
 * (градиентный брикет с литерой M), применяется ко всем окнам
 * лаунчера — главному и диалогам.
 */
public final class AppIcon {

    private static final Image ICON = load();

    private AppIcon() {
    }

    /** Устанавливает иконку приложения на окно, если ресурс найден. */
    public static void apply(Stage stage) {
        if (stage != null && ICON != null) {
            stage.getIcons().setAll(ICON);
        }
    }

    private static Image load() {
        try {
            var url = AppIcon.class.getResource("/icon.png");
            return url == null ? null : new Image(url.toExternalForm());
        } catch (Exception e) {
            return null;
        }
    }
}
