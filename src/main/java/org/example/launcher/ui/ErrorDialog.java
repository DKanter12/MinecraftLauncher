package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog showing a launch error message with a copy button.
 */
public class ErrorDialog extends Stage {

    public ErrorDialog(Stage owner, String title, String message) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(title);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("error-title");

        TextArea textArea = new TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefWidth(560);
        textArea.setPrefHeight(200);
        textArea.getStyleClass().add("error-text");

        Button copyButton = new Button("Copy");
        copyButton.getStyleClass().add("install-close-button");
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(message);
            Clipboard.getSystemClipboard().setContent(content);
            copyButton.setText("Copied!");
        });

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("install-close-button");
        closeButton.setOnAction(e -> close());

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(titleLabel, textArea, copyButton, closeButton);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Scene scene = new Scene(content, 620, 340);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    public static void show(Stage owner, String title, String message) {
        ErrorDialog dialog = new ErrorDialog(owner, title, message);
        dialog.showAndWait();
    }
}
