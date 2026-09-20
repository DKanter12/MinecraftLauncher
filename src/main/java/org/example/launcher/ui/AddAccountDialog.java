package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Unified dialog for adding an account.
 * The user picks the account type (Offline or Ely.by) in one place
 * instead of having two separate buttons in the main window.
 * <p>
 * Offline needs only a player name; Ely.by needs login + password.
 */
public class AddAccountDialog extends Stage {

    public enum Type { OFFLINE, ELY_BY }

    public record Result(Type type, String offlineName, String elyUser, String elyPass) {}

    private Result result;

    private final TextField offlineNameField = new TextField();
    private final TextField elyUserField = new TextField();
    private final PasswordField elyPassField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button actionButton = new Button("Create");

    private Type selectedType = Type.OFFLINE;

    public AddAccountDialog(Stage owner) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Add Account");

        Label titleLabel = new Label("Add Account");
        titleLabel.getStyleClass().add("account-title");

        Label hintLabel = new Label("Choose the account type. Offline needs no password; Ely.by brings skins and capes.");
        hintLabel.getStyleClass().add("account-hint");
        hintLabel.setWrapText(true);
        hintLabel.setMaxWidth(380);

        // --- Type switch ---
        ToggleGroup typeGroup = new ToggleGroup();
        ToggleButton offlineToggle = new ToggleButton("Offline");
        offlineToggle.setToggleGroup(typeGroup);
        offlineToggle.setSelected(true);
        offlineToggle.getStyleClass().add("filter-button");
        ToggleButton elyToggle = new ToggleButton("Ely.by");
        elyToggle.setToggleGroup(typeGroup);
        elyToggle.getStyleClass().add("filter-button");
        HBox typeRow = new HBox(8, offlineToggle, elyToggle);
        typeRow.setAlignment(Pos.CENTER_LEFT);

        // --- Offline pane ---
        Label offlineLabel = new Label("Player name");
        offlineLabel.getStyleClass().add("section-title");
        offlineNameField.setPromptText("e.g. Steve");
        offlineNameField.getStyleClass().add("search-field");
        Label offlineHint = new Label("Up to 16 characters, no password needed.");
        offlineHint.getStyleClass().add("quick-select-label");
        VBox offlineBox = new VBox(4, offlineLabel, offlineNameField, offlineHint);

        // --- Ely.by pane ---
        Label elyLabel = new Label("Ely.by credentials");
        elyLabel.getStyleClass().add("section-title");
        elyUserField.setPromptText("Username or email");
        elyUserField.getStyleClass().add("search-field");
        elyPassField.setPromptText("Password");
        elyPassField.getStyleClass().add("search-field");
        Label elyHint = new Label("Used only to sign in to ely.by — the password is not stored in plain text.");
        elyHint.getStyleClass().add("quick-select-label");
        elyHint.setWrapText(true);
        elyHint.setMaxWidth(380);
        VBox elyBox = new VBox(6, elyLabel, elyUserField, elyPassField, elyHint);
        elyBox.setVisible(false);
        elyBox.setManaged(false);

        // Switch logic
        typeGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            boolean isOffline = val == offlineToggle;
            if (val == null) {
                offlineToggle.setSelected(true);
                return;
            }
            selectedType = isOffline ? Type.OFFLINE : Type.ELY_BY;
            offlineBox.setVisible(isOffline);
            offlineBox.setManaged(isOffline);
            elyBox.setVisible(!isOffline);
            elyBox.setManaged(!isOffline);
            actionButton.setText(isOffline ? "Create" : "Login");
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
            sizeToScene();
        });

        errorLabel.getStyleClass().add("account-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setMaxWidth(380);

        actionButton.getStyleClass().add("install-close-button");
        actionButton.setDefaultButton(true);
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> onAction());

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("browse-java-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        offlineNameField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });
        elyUserField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });
        elyPassField.textProperty().addListener((obs, o, v) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            updateActionButton();
        });

        HBox buttons = new HBox(8, cancelButton, actionButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(10, titleLabel, hintLabel, typeRow, offlineBox, elyBox, errorLabel, buttons);
        content.setPadding(new Insets(20));
        content.setPrefWidth(420);
        content.setMinHeight(320);

        Scene scene = new Scene(content, 420, 360);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);
        setMinWidth(420);
        setMinHeight(360);

        updateActionButton();
    }

    private void updateActionButton() {
        if (selectedType == Type.OFFLINE) {
            String name = offlineNameField.getText() == null ? "" : offlineNameField.getText().trim();
            actionButton.setDisable(name.isEmpty());
        } else {
            String u = elyUserField.getText() == null ? "" : elyUserField.getText().trim();
            String p = elyPassField.getText() == null ? "" : elyPassField.getText();
            actionButton.setDisable(u.isEmpty() || p.isEmpty());
        }
    }

    private void onAction() {
        if (selectedType == Type.OFFLINE) {
            String name = offlineNameField.getText().trim();
            if (name.isEmpty()) {
                errorLabel.setText("Name cannot be empty");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                sizeToScene();
                return;
            }
            if (name.length() > 16) {
                errorLabel.setText("Name must be 16 characters or less");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                sizeToScene();
                return;
            }
            if (!name.matches("[a-zA-Z0-9_]+")) {
                errorLabel.setText("Only letters, digits and _ are allowed");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                sizeToScene();
                return;
            }
            result = new Result(Type.OFFLINE, name, null, null);
            close();
        } else {
            String u = elyUserField.getText().trim();
            String p = elyPassField.getText();
            if (u.isEmpty()) {
                errorLabel.setText("Enter Ely.by username");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                sizeToScene();
                return;
            }
            if (p.isEmpty()) {
                errorLabel.setText("Enter password");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                sizeToScene();
                return;
            }
            result = new Result(Type.ELY_BY, null, u, p);
            close();
        }
    }

    public Result getResult() { return result; }

    public static Result showDialog(Stage owner) {
        AddAccountDialog d = new AddAccountDialog(owner);
        d.showAndWait();
        return d.getResult();
    }
}
