package org.example.launcher.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.launcher.application.launch.LauncherWindowManager;
import org.example.launcher.core.LauncherContext;
import org.example.launcher.domain.model.LauncherPreferences;
import org.example.launcher.i18n.Lang;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.settings.FileSettingsRepository;
import org.example.launcher.presentation.MainView;
import org.example.launcher.presentation.ThemeManager;

/**
 * Точка входа JavaFX-приложения Minecraft Launcher.
 * <p>
 * Инициализирует JavaFX, получает готовый контекст приложения из
 * {@link LauncherCompositionRoot}, создаёт главное представление,
 * настраивает сцену и окно и запускает первоначальную загрузку данных.
 * Бизнес-процессов здесь нет — только запуск.
 */ 
public class LauncherApplication extends Application {

    @Override
    public void start(Stage stage) {
        LauncherLogger.init();
        Logger.getGlobal().info("Launcher starting, log file: "
                + LauncherLogger.logFile());

        // Непойманные исключения (в т.ч. из FX-потока, мимо Task-обработчиков)
        // уходят в тот же лог — иначе они терялись бы бесследно.
        Thread.setDefaultUncaughtExceptionHandler((thread, thrown) ->
                Logger.getLogger("org.example.launcher")
                        .log(Level.SEVERE,
                                "Uncaught exception on " + thread.getName(),
                                thrown));

        Platform.setImplicitExit(false);

        LauncherWindowManager windows = new LauncherWindowManager() {
            @Override
            public void hide() {
                Platform.runLater(stage::hide);
            }

            @Override
            public void show() {
                Platform.runLater(stage::show);
            }

            @Override
            public void minimize() {
                Platform.runLater(() -> stage.setIconified(true));
            }
        };
        LauncherContext context = LauncherCompositionRoot.createContext(windows);
        Lang.load(context.preferences());

        // Тема: до создания первой сцены, чтобы окно открылось уже в ней.
        ThemeManager.initialize(GameDirectory.defaultDirectory().root());
        applySavedTheme(context.preferences());

        MainView view = new MainView(context);

        Scene scene = new Scene(view.getView(), 1180, 680);
        ThemeManager.apply(scene);

        stage.setTitle("MDD Launcher");
        stage.setMinWidth(900);
        stage.setMinHeight(520);
        stage.setScene(scene);
        org.example.launcher.presentation.AppIcon.apply(stage);
        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();

        // Отладочная вёрстка-проверка: -Dmdd.snapshot=<файл> снимет
        // интерфейс в PNG и закроет приложение (см. DebugSnapshot).
        String snapshotPath = System.getProperty("mdd.snapshot");
        if (snapshotPath != null) {
            org.example.launcher.presentation.DebugSnapshot.schedule(
                    scene.getRoot(), snapshotPath, 12000);
        }

        view.loadVersions();
    }

    public static void main(String[] args) {
        launch(args);
    }

    /** Восстанавливает сохранённую тему до открытия окна. */
    private static void applySavedTheme(FileSettingsRepository prefs) {
        try {
            LauncherPreferences saved = prefs.loadPreferences();
            boolean custom = "CUSTOM".equalsIgnoreCase(saved.theme())
                    && saved.customThemeBg() != null
                    && saved.customThemeAccent() != null;
            if (custom) {
                ThemeManager.setCustom(saved.customThemeBg(),
                        saved.customThemeAccent());
            } else {
                ThemeManager.setStandard();
            }
        } catch (Exception e) {
            ThemeManager.setStandard();
        }
    }
}
