package org.example.launcher.presentation;

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

import org.example.launcher.infrastructure.server.ServerAuthService;
import org.example.launcher.infrastructure.server.ServerSession;
import org.example.launcher.infrastructure.server.UserRole;
import org.example.launcher.i18n.Lang;

/**
 * РњРѕРґР°Р»СЊРЅС‹Р№ РґРёР°Р»РѕРі РІС…РѕРґР° РЅР° СЃРµСЂРІРµСЂ Р»Р°СѓРЅС‡РµСЂР° (Рё РІС‹С…РѕРґР° РёР· РЅРµРіРѕ).
 *
 * <p>Р’С…РѕРґ РѕС‚РїСЂР°РІР»СЏРµС‚ Р»РѕРіРёРЅ Рё РїР°СЂРѕР»СЊ РЅР° СЃРµСЂРІРµСЂ РѕРґРёРЅ СЂР°Р· вЂ” СЃРµСЂРІРµСЂ
 * РѕС‚РІРµС‡Р°РµС‚ bearer-С‚РѕРєРµРЅРѕРј Рё СЂРѕР»СЊСЋ Р°РєРєР°СѓРЅС‚Р°; РґР°Р»РµРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ
 * Рё С…СЂР°РЅРёС‚СЃСЏ С‚РѕР»СЊРєРѕ С‚РѕРєРµРЅ. Р•СЃР»Рё СЃРµСЃСЃРёСЏ СѓР¶Рµ Р°РєС‚РёРІРЅР°,
 * РґРёР°Р»РѕРі РїРѕРєР°Р·С‹РІР°РµС‚ РµС‘ Рё РїСЂРµРґР»Р°РіР°РµС‚ РІС‹Р№С‚Рё.</p>
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
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);
    }

    private VBox signedInView() {
        Label titleLabel = new Label(Lang.tr("server.signedin"));
        titleLabel.getStyleClass().add("section-title");

        String role = current.isAdmin() ? Lang.tr("server.role.admin")
                : Lang.tr("server.role.user");
        Label accountLabel = new Label(current.accountName() + " В· " + role);
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
     * РџРѕРєР°Р·С‹РІР°РµС‚ РґРёР°Р»РѕРі Рё РІРѕР·РІСЂР°С‰Р°РµС‚ Р°РєС‚РёРІРЅСѓСЋ РїРѕСЃР»Рµ РЅРµРіРѕ СЃРµСЃСЃРёСЋ
     * вЂ” СЃРІРµР¶РµСЃРѕР·РґР°РЅРЅСѓСЋ, РЅРµРёР·РјРµРЅС‘РЅРЅСѓСЋ С‚РµРєСѓС‰СѓСЋ
     * РёР»Рё {@code null} РїРѕСЃР»Рµ РІС‹С…РѕРґР° Р»РёР±Рѕ РѕС‚РјРµРЅС‹ Р±РµР· РІС…РѕРґР°.
     */
    public static ServerSession show(Stage owner, ServerAuthService authService,
                                     ServerSession current) {
        ServerLoginDialog dialog = new ServerLoginDialog(owner, authService, current);
        dialog.showAndWait();
        return dialog.result != null ? dialog.result
                : (dialog.signedOut ? null : current);
    }
}
