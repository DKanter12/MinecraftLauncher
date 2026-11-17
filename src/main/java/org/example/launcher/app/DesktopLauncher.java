package org.example.launcher.app;

/**
 * Стартовый класс для запуска лаунчера с рабочего стола
 * (jpackage app-image, installDist-скрипты): JavaFX, разложенный
 * по classpath, требует, чтобы класс запуска не наследовал
 * {@link javafx.application.Application} — он лишь делегирует
 * {@link LauncherApplication}.
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        LauncherApplication.main(args);
    }
}
