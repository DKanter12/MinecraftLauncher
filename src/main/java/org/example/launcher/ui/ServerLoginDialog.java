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

/**
 * Modal dialog for signing in to (and out of) the launcher server.
 *
 * <p>Sign-in sends login and password to the server once — the server
 * answers with a bearer token and the account's role; from then on
 * only the token is used and stored. When a session is already active
 * the dialog shows it and offers signing out instead.</p>
 */
public class ServerLoginDialog extends Stage {

    private ServerSession result;
    private boolean signedOut;

    private final ServerAuthService authService;
    private final ServerSession current;

    private final TextField loginField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button signInButton = new Button("Sign In");
    private final Button signOutButton = new Button("Sign Out");

    public ServerLoginDialog(Stage owner, ServerAuthService authService, ServerSession current) {
        this.authService = authService;
        this.current = current;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Launcher Server");

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
        Label titleLabel = new Label("Signed in to the launcher server");
        titleLabel.getStyleClass().add("section-title");

        String role = current.isAdmin() ? "Administrator" : "User";
        Label accountLabel = new Label(current.accountName() + " · " + role);
        accountLabel.getStyleClass().add("details-version");

        Label hint = new Label("Your session token is stored locally and used for "
                + "server requests; the password is never saved. Signing out discards "
                + "the token — local builds and instances are not affected.");
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
        Button closeButton = new Button("Close");
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
        Label titleLabel = new Label("Sign in to the launcher server");
        titleLabel.getStyleClass().add("section-title");

        Label loginLabel = new Label("Login");
        loginLabel.getStyleClass().add("quick-select-label");
        loginField.getStyleClass().add("search-field");
        loginField.setPromptText("Account login");

        Label passwordLabel = new Label("Password");
        passwordLabel.getStyleClass().add("quick-select-label");
        passwordField.getStyleClass().add("search-field");
        passwordField.setPromptText("Sent over HTTPS, never stored");

        signInButton.getStyleClass().add("install-close-button");
        signInButton.setDefaultButton(true);
        signInButton.disableProperty().bind(loginField.textProperty().isEmpty()
                .or(passwordField.textProperty().isEmpty()));
        signInButton.setOnAction(e -> signIn());
        Button cancelButton = new Button("Cancel");
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
        errorLabel.setText("Signing in…");

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
                            : "Sign-in failed");
                    signInButton.setDisable(false);
                });
            }
        }, "server-login");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Shows the dialog and returns the session that is active
     * afterwards — a freshly created session, the unchanged current
     * one, or {@code null} after signing out or cancelling while
     * signed out.
     */
    public static ServerSession show(Stage owner, ServerAuthService authService,
                                     ServerSession current) {
        ServerLoginDialog dialog = new ServerLoginDialog(owner, authService, current);
        dialog.showAndWait();
        return dialog.result != null ? dialog.result
                : (dialog.signedOut ? null : current);
    }
}
