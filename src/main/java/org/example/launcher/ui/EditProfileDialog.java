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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.model.ModdedProfile;

/**
 * Modal dialog for editing a game instance: its display name, the
 * dedicated memory (RAM) limit and extra JVM launch arguments.
 * <p>
 * Renaming also renames the instance folder, so it always matches
 * the launcher name (mods, saves and builds move along). The
 * loader/Minecraft versions are fixed properties of an instance.
 */
public class EditProfileDialog extends Stage {

    /**
     * The edited values. JVM arguments are given as a list of separate
     * arguments in launch order; the memory limit is megabytes
     * ({@code 0} means automatic).
     */
    public record Result(String name, List<String> extraJvmArgs,
                         int memoryMb) {
    }

    private Result result;

    private final TextField nameField = new TextField();
    private final TextField memoryField = new TextField();
    private final HBox memoryChips = new HBox(6);
    private final ToggleGroup memoryGroup = new ToggleGroup();
    private final TextArea jvmArgsArea = new TextArea();
    private final Label errorLabel = new Label();
    private final Button saveButton = new Button("Save");

    public EditProfileDialog(Stage owner, ModdedProfile profile) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Edit Instance");

        Label infoLabel = new Label(profile.name() + " \u00b7 "
                + profile.summary() + " \u00b7 " + profile.versionId());
        infoLabel.getStyleClass().add("quick-select-label");
        infoLabel.setWrapText(true);

        Label nameLabel = new Label("Instance name");
        nameLabel.getStyleClass().add("section-title");
        nameField.setText(profile.name());
        nameField.getStyleClass().add("search-field");
        nameField.setMaxWidth(Double.MAX_VALUE);

        Label memoryLabel = new Label("Memory (RAM, megabytes)");
        memoryLabel.getStyleClass().add("section-title");
        addMemoryChip("Auto", 0);
        addMemoryChip("2 GB", 2048);
        addMemoryChip("4 GB", 4096);
        addMemoryChip("6 GB", 6144);
        addMemoryChip("8 GB", 8192);
        addMemoryChip("12 GB", 12288);
        addMemoryChip("16 GB", 16384);
        memoryField.setText(profile.memoryMb() > 0
                ? String.valueOf(profile.memoryMb()) : "");
        memoryField.setPromptText("Custom MB (e.g. 10240 for 10 GB)");
        memoryField.getStyleClass().add("search-field");
        memoryField.setMaxWidth(Double.MAX_VALUE);

        Label jvmLabel = new Label("Extra JVM arguments (one per line)");
        jvmLabel.getStyleClass().add("section-title");
        jvmArgsArea.setText(String.join("\n", profile.extraJvmArgs()));
        jvmArgsArea.setPromptText("-XX:+UseG1GC");
        jvmArgsArea.setPrefRowCount(4);
        jvmArgsArea.setPrefColumnCount(34);
        jvmArgsArea.setWrapText(false);
        jvmArgsArea.getStyleClass().add("profile-jvm-args");

        errorLabel.getStyleClass().add("account-error");
        errorLabel.setWrapText(true);

        nameField.textProperty().addListener((obs, old, val) ->
                updateSaveButton());
        memoryField.textProperty().addListener((obs, old, val) -> {
            syncMemoryChips();
            updateSaveButton();
        });

        saveButton.getStyleClass().add("install-close-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            result = new Result(nameField.getText().trim(),
                    parseJvmArgs(jvmArgsArea.getText()),
                    parseMemory(memoryField.getText()));
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
                infoLabel, nameLabel, nameField,
                memoryLabel, memoryChips, memoryField,
                jvmLabel, jvmArgsArea, errorLabel,
                buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(440);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
        syncMemoryChips();
        updateSaveButton();
    }

    private void addMemoryChip(String text, int megabytes) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("filter-button");
        chip.setToggleGroup(memoryGroup);
        chip.setUserData(megabytes);
        chip.setOnAction(e -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
            }
            memoryField.setText(
                    megabytes == 0 ? "" : String.valueOf(megabytes));
        });
        memoryChips.getChildren().add(chip);
    }

    /** Highlights the preset matching the field (none for custom). */
    private void syncMemoryChips() {
        int current = parseLenient(memoryField.getText());
        for (var node : memoryChips.getChildren()) {
            if (node instanceof ToggleButton chip
                    && chip.getUserData() instanceof Integer preset) {
                chip.setSelected(current >= 0 && preset == current);
            }
        }
    }

    private void updateSaveButton() {
        if (nameField.getText().isBlank()) {
            errorLabel.setText("The instance needs a name.");
            saveButton.setDisable(true);
            return;
        }
        if (parseMemory(memoryField.getText()) < 0) {
            errorLabel.setText(
                    "Memory must be empty (auto) or at least 256 MB.");
            saveButton.setDisable(true);
            return;
        }
        errorLabel.setText("");
        saveButton.setDisable(false);
    }

    /**
     * Parses the memory field: empty means automatic ({@code 0}),
     * otherwise megabytes; {@code -1} when invalid.
     */
    private static int parseMemory(String text) {
        int value = parseLenient(text);
        if (value == 0) {
            return 0;
        }
        return value < 256 ? -1 : value;
    }

    /** Lenient parse for chip syncing: empty is 0, garbage is -1. */
    private static int parseLenient(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
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
