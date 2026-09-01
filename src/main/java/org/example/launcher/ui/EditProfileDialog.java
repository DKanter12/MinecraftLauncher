package org.example.launcher.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.model.ModdedProfile;

/**
 * Modal dialog for editing an existing game instance: display name
 * and extra JVM launch arguments (e.g. {@code -Xmx4G}).
 * <p>
 * The loader/Minecraft versions and the game directory are fixed
 * properties of an instance and are shown read-only; renaming an
 * instance never moves its directory, so mods and saves are never
 * orphaned.
 */
public class EditProfileDialog extends Stage {

    /**
     * The edited values. JVM arguments are given as a list of separate
     * arguments in launch order.
     */
    public record Result(String name, List<String> extraJvmArgs) {
    }

    private Result result;

    private final TextField nameField = new TextField();
    private final TextArea jvmArgsArea = new TextArea();

    public EditProfileDialog(Stage owner, ModdedProfile profile) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Edit Instance");

        Label nameLabel = new Label("Instance name");
        nameLabel.getStyleClass().add("section-title");
        nameField.setText(profile.name());
        nameField.getStyleClass().add("search-field");
        nameField.setPromptText("Display name of this instance");

        Label infoLabel = new Label(profile.summary() + " \u00b7 "
                + profile.versionId());
        infoLabel.getStyleClass().add("quick-select-label");
        infoLabel.setWrapText(true);

        Label jvmLabel = new Label("Extra JVM arguments (one per line)");
        jvmLabel.getStyleClass().add("section-title");
        jvmArgsArea.setText(String.join("\n", profile.extraJvmArgs()));
        jvmArgsArea.setPromptText("-Xmx4G");
        jvmArgsArea.setPrefRowCount(5);
        jvmArgsArea.setPrefColumnCount(34);
        jvmArgsArea.setWrapText(false);
        jvmArgsArea.getStyleClass().add("profile-jvm-args");

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("install-close-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            result = new Result(
                    nameField.getText(),
                    parseJvmArgs(jvmArgsArea.getText()));
            close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, cancelButton, saveButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6,
                nameLabel, nameField, infoLabel, jvmLabel, jvmArgsArea,
                buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(420);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    /**
     * Splits the text area content into separate JVM arguments: one
     * argument per non-blank line, trimmed.
     */
    private static List<String> parseJvmArgs(String text) {
        List<String> args = new ArrayList<>();
        if (text == null) return List.of();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                args.add(trimmed);
            }
        }
        return List.copyOf(args);
    }

    /**
     * Shows the dialog and returns the edited values, or {@code null}
     * if the dialog was cancelled.
     */
    public static Result show(Stage owner, ModdedProfile profile) {
        EditProfileDialog dialog = new EditProfileDialog(owner, profile);
        dialog.showAndWait();
        return dialog.result;
    }
}
