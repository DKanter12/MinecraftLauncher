package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog for creating a simple (offline) account.
 * <p>
 * The user only needs to enter a player name. No password
 * or external authentication is required.
 */
public class CreateAccountDialog extends Stage {

    private String chosenName;

    public CreateAccountDialog(Stage owner) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Create Account");

        Label titleLabel = new Label("Create Simple Account");
        titleLabel.getStyleClass().add("account-title");

        Label hintLabel = new Label("Enter your player name. No password required.");
        hintLabel.getStyleClass().add("account-hint");
        hintLabel.setWrapText(true);

        TextField nameField = new TextField();
        nameField.setPromptText("Player name");
        nameField.getStyleClass().add("search-field");

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("account-error");
        errorLabel.setVisible(false);

        Button createButton = new Button("Create");
        createButton.getStyleClass().add("install-close-button");
        createButton.setDisable(true);
        createButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                errorLabel.setText("Name cannot be empty");
                errorLabel.setVisible(true);
                return;
            }
            if (name.length() > 16) {
                errorLabel.setText("Name must be 16 characters or less");
                errorLabel.setVisible(true);
                return;
            }
            chosenName = name;
            close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("browse-java-button");
        cancelButton.setOnAction(e -> close());

        nameField.textProperty().addListener((obs, old, val) -> {
            createButton.setDisable(val == null || val.trim().isEmpty());
            errorLabel.setVisible(false);
        });

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(titleLabel, hintLabel, nameField, errorLabel, createButton, cancelButton);

        Scene scene = new Scene(content, 360, 240);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    public String getChosenName() {
        return chosenName;
    }

    public static String showDialog(Stage owner) {
        CreateAccountDialog dialog = new CreateAccountDialog(owner);
        dialog.showAndWait();
        return dialog.getChosenName();
    }
}
