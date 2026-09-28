package org.example.launcher.ui;

import java.io.IOException;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.distribution.ServerAuthService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.UserRole;
import org.example.launcher.i18n.Lang;

/**
 * Модальный диалог входа на сервер лаунчера (и выхода из него).
 *
 * <p>Вход отправляет логин и пароль на сервер один раз — сервер
 * отвечает bearer-токеном и ролью аккаунта; далее используется
 * и хранится только токен. Если сессия уже активна,
 * диалог показывает её и предлагает выйти.</p>
 */
public class ServerLoginDialog extends Stage {

    private ServerSession result;
    private boolean signedOut;

    private final ServerAuthService authService;
    private final ServerSession current;

    private final TextField loginField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button signInButton = new Button(Lang.tr("button.signin"));
    private final Button signOutButton = new Button(Lang.tr("button.signout"));

    public ServerLoginDialog(Stage owner, ServerAuthService authService, ServerSession current) {
        this.authService = authService;
        this.current = current;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("server.title"));

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);

        VBox content = current != null ? signedInView() : signedOutView();

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    private VBox signedInView() {
        Label titleLabel = new Label(Lang.tr("server.signedin"));
        titleLabel.getStyleClass().add("section-title");

        String role = current.isAdmin() ? Lang.tr("server.role.admin")
                : Lang.tr("server.role.user");
        Label accountLabel = new Label(current.accountName() + " · " + role);
        accountLabel.getStyleClass().add("details-version");

        Label hint = new Label(Lang.tr("server.hint"));
        hint.getStyleClass().add("quick-select-label");
        hint.setWrapText(true);

        signOutButton.getStyleClass().add("install-close-button");
        signOutButton.setOnAction(e -> {
            try {
                authService.logout();
                result = null;
                signedOut = true;
            } catch (IOException io) {
                errorLabel.setText(io.getMessage());
                return;
            }
            close();
        });
        Button closeButton = new Button(Lang.tr("button.close"));
        closeButton.getStyleClass().add("install-close-button");
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> {
            result = current;
            close();
        });

        HBox buttons = new HBox(8, signOutButton, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, titleLabel, accountLabel, hint, errorLabel, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(420);
        return content;
    }

    private VBox signedOutView() {
        Label titleLabel = new Label(Lang.tr("server.signedout"));
        titleLabel.getStyleClass().add("section-title");

        Label loginLabel = new Label(Lang.tr("server.login"));
        loginLabel.getStyleClass().add("quick-select-label");
        loginField.getStyleClass().add("search-field");
        loginField.setPromptText(Lang.tr("server.login.prompt"));

        Label passwordLabel = new Label(Lang.tr("server.password"));
        passwordLabel.getStyleClass().add("quick-select-label");
        passwordField.getStyleClass().add("search-field");
        passwordField.setPromptText(Lang.tr("server.password.prompt"));

        signInButton.getStyleClass().add("install-close-button");
        signInButton.setDefaultButton(true);
        signInButton.disableProperty().bind(loginField.textProperty().isEmpty()
                .or(passwordField.textProperty().isEmpty()));
        signInButton.setOnAction(e -> signIn());
        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, cancelButton, signInButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, titleLabel, loginLabel, loginField,
                passwordLabel, passwordField, errorLabel, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(420);
        return content;
    }

    private void signIn() {
        String login = loginField.getText().trim();
        String password = passwordField.getText();
        signInButton.setDisable(true);
        errorLabel.setText(Lang.tr("server.signing"));

        Thread worker = new Thread(() -> {
            try {
                ServerSession session = authService.login(login, password);
                Platform.runLater(() -> {
                    result = session;
                    close();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    errorLabel.setText(e.getMessage() != null ? e.getMessage()
                            : Lang.tr("server.failed"));
                    signInButton.setDisable(false);
                });
            }
        }, "server-login");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Показывает диалог и возвращает активную после него сессию
     * — свежесозданную, неизменённую текущую
     * или {@code null} после выхода либо отмены без входа.
     */
    public static ServerSession show(Stage owner, ServerAuthService authService,
                                     ServerSession current) {
        ServerLoginDialog dialog = new ServerLoginDialog(owner, authService, current);
        dialog.showAndWait();
        return dialog.result != null ? dialog.result
                : (dialog.signedOut ? null : current);
    }
}
