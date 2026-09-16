package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog for saving the current content of an instance as a
 * build: the user names the build and chooses what to include — mods
 * only, or mods together with configs. Optionally the new build can
 * be activated right away; leaving the checkbox off keeps the
 * currently active build (saving never changes the selection by
 * itself).
 */
public class SaveBuildDialog extends Stage {

    /**
     * The build to save.
     *
     * @param name     build name (non-blank)
     * @param mods     snapshot the mods folder
     * @param configs  snapshot the config folder
     * @param activate make the new build the active one (applied at
     *                 launch); {@code false} leaves the current
     *                 selection unchanged
     */
    public record Result(String name, boolean mods, boolean configs,
                         boolean activate) {
    }

    private Result result;

    private final TextField nameField = new TextField();
    private final CheckBox modsCheck = new CheckBox("Mods");
    private final CheckBox configsCheck = new CheckBox("Configs");
    private final CheckBox activateCheck = new CheckBox(
            "Activate this build (applied at launch)");
    private final Button saveButton = new Button("Save");

    public SaveBuildDialog(Stage owner) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Save Build");

        Label nameLabel = new Label("Build name");
        nameLabel.getStyleClass().add("section-title");
        nameField.setPromptText("e.g. Performance Pack");
        nameField.getStyleClass().add("search-field");

        Label contentsLabel = new Label("What to save");
        contentsLabel.getStyleClass().add("section-title");
        modsCheck.setSelected(true);
        configsCheck.setSelected(false);
        activateCheck.setSelected(false);
        HBox checks = new HBox(12, modsCheck, configsCheck);
        checks.setAlignment(Pos.CENTER_LEFT);
        checks.setPadding(new Insets(2, 0, 2, 0));

        Label hint = new Label(
                "The build is a snapshot of the instance's current mods and "
                        + "config folders. A selected build replaces those "
                        + "folders at launch; every saved build is kept "
                        + "separately and switching never deletes one.");
        hint.getStyleClass().add("quick-select-label");
        hint.setWrapText(true);

        nameField.textProperty().addListener((obs, old, val) ->
                updateSaveButton());
        modsCheck.selectedProperty().addListener((obs, old, val) ->
                updateSaveButton());
        configsCheck.selectedProperty().addListener((obs, old, val) ->
                updateSaveButton());

        saveButton.getStyleClass().add("install-close-button");
        saveButton.setDefaultButton(true);
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> {
            result = new Result(nameField.getText().trim(),
                    modsCheck.isSelected(), configsCheck.isSelected(),
                    activateCheck.isSelected());
            close();
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, cancelButton, saveButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, nameLabel, nameField, contentsLabel,
                checks, activateCheck, hint, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(420);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    private void updateSaveButton() {
        saveButton.setDisable(nameField.getText().isBlank()
                || (!modsCheck.isSelected() && !configsCheck.isSelected()));
    }

    /**
     * Shows the dialog and returns the build to save, or {@code null}
     * if it was cancelled.
     */
    public static Result show(Stage owner) {
        SaveBuildDialog dialog = new SaveBuildDialog(owner);
        dialog.showAndWait();
        return dialog.result;
    }
}
