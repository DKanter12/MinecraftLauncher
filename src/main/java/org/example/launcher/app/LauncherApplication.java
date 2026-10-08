package org.example.launcher.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.launcher.application.launch.LauncherWindowManager;
import org.example.launcher.core.LauncherContext;
import org.example.launcher.i18n.Lang;
import org.example.launcher.presentation.MainView;

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

        MainView view = new MainView(context);

        Scene scene = new Scene(view.getView(), 1180, 680);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Minecraft Launcher");
        stage.setMinWidth(900);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();

        view.loadVersions();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
