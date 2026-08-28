package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog for logging in via Ely.by.
 * <p>
 * The user enters their Ely.by username/email and password.
 * On success, the caller receives the credentials to pass
 * to {@link org.example.launcher.service.ElyAuthService}.
 */
public class ElyLoginDialog extends Stage {

    private String username;
    private String password;

    public ElyLoginDialog(Stage owner) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Ely.by Login");

        Label titleLabel = new Label("Ely.by Authorization");
        titleLabel.getStyleClass().add("account-title");

        Label hintLabel = new Label("Enter your Ely.by account credentials.");
        hintLabel.getStyleClass().add("account-hint");
        hintLabel.setWrapText(true);

        TextField userField = new TextField();
        userField.setPromptText("Username or email");
        userField.getStyleClass().add("search-field");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        passField.getStyleClass().add("search-field");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("account-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("install-close-button");
        loginButton.setDisable(true);
        loginButton.setOnAction(e -> {
            String u = userField.getText().trim();
            String p = passField.getText();
            if (u.isEmpty()) {
                errorLabel.setText("Username cannot be empty");
                errorLabel.setVisible(true);
                return;
            }
            if (p.isEmpty()) {
                errorLabel.setText("Password cannot be empty");
                errorLabel.setVisible(true);
                return;
            }
            username = u;
            password = p;
            close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("browse-java-button");
        cancelButton.setOnAction(e -> close());

        userField.textProperty().addListener((obs, old, val) -> {
            boolean ready = val != null && !val.trim().isEmpty()
                    && !passField.getText().isEmpty();
            loginButton.setDisable(!ready);
            errorLabel.setVisible(false);
        });
        passField.textProperty().addListener((obs, old, val) -> {
            boolean ready = val != null && !val.isEmpty()
                    && !userField.getText().trim().isEmpty();
            loginButton.setDisable(!ready);
            errorLabel.setVisible(false);
        });

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(titleLabel, hintLabel, userField, passField,
                errorLabel, loginButton, cancelButton);

        Scene scene = new Scene(content, 360, 280);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Shows the dialog and returns the credentials, or null if cancelled.
     *
     * @return a String[]{username, password} or null
     */
    public static String[] showDialog(Stage owner) {
        ElyLoginDialog dialog = new ElyLoginDialog(owner);
        dialog.showAndWait();
        if (dialog.getUsername() == null) return null;
        return new String[]{dialog.getUsername(), dialog.getPassword()};
    }
}
