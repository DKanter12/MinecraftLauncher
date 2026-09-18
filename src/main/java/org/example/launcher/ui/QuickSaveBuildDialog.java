package org.example.launcher.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.model.ModdedProfile;

/**
 * Fast build saving without scrolling the cards grid: find the
 * instance by typing, name the build and choose what to snapshot.
 */
public class QuickSaveBuildDialog extends Stage {

    /**
     * The build to save.
     *
     * @param profile the instance (version) the build is made from
     * @param name    build name (non-blank)
     * @param mods    snapshot the mods folder
     * @param configs snapshot the config folder
     */
    public record Result(ModdedProfile profile, String name,
                         boolean mods, boolean configs) {
    }

    private Result result;

    private final List<ModdedProfile> instances;
    private final ObservableList<ModdedProfile> filtered =
            FXCollections.observableArrayList();
    private final ListView<ModdedProfile> instanceList =
            new ListView<>(filtered);
    private final TextField searchField = new TextField();
    private final TextField nameField = new TextField();
    private final CheckBox modsCheck = new CheckBox("Mods");
    private final CheckBox configsCheck = new CheckBox("Configs");
    private final Button saveButton = new Button("Save Build");

    public QuickSaveBuildDialog(Stage owner, List<ModdedProfile> instances,
                                String preselectedId) {
        this.instances = List.copyOf(instances);

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Save Build");

        Label instanceLabel = new Label("Instance");
        instanceLabel.getStyleClass().add("section-title");
        searchField.setPromptText("Type to find...");
        searchField.getStyleClass().add("search-field");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.textProperty().addListener((obs, old, val) ->
                applyFilter());

        instanceList.getStyleClass().add("profile-list");
        instanceList.setPrefHeight(170);
        instanceList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ModdedProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label name = new Label(item.name());
                    name.getStyleClass().add("profile-name");
                    Label summary = new Label(item.summary());
                    summary.getStyleClass().add("profile-summary");
                    setText(null);
                    setGraphic(new VBox(2, name, summary));
                }
            }
        });
        instanceList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> updateSaveButton());

        Label nameLabel = new Label("Build name");
        nameLabel.getStyleClass().add("section-title");
        nameField.setPromptText("e.g. Performance Pack");
        nameField.getStyleClass().add("search-field");
        nameField.setMaxWidth(Double.MAX_VALUE);

        Label contentsLabel = new Label("What to save");
        contentsLabel.getStyleClass().add("section-title");
        modsCheck.setSelected(true);
        configsCheck.setSelected(false);
        HBox checks = new HBox(12, modsCheck, configsCheck);
        checks.setAlignment(Pos.CENTER_LEFT);
        checks.setPadding(new Insets(2, 0, 2, 0));

        Label hint = new Label("The build snapshots the chosen instance's "
                + "current mods and configs into its builds folder.");
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
            result = new Result(
                    instanceList.getSelectionModel().getSelectedItem(),
                    nameField.getText().trim(),
                    modsCheck.isSelected(), configsCheck.isSelected());
            close();
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, cancelButton, saveButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, instanceLabel, searchField, instanceList,
                nameLabel, nameField, contentsLabel, checks, hint, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(440);
        VBox.setVgrow(instanceList, Priority.ALWAYS);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);

        applyFilter();
        selectInitial(preselectedId);
        updateSaveButton();
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? ""
                : searchField.getText().trim().toLowerCase();
        ModdedProfile selected =
                instanceList.getSelectionModel().getSelectedItem();
        filtered.clear();
        for (ModdedProfile p : instances) {
            if (query.isEmpty()) {
                filtered.add(p);
                continue;
            }
            String haystack = (p.name() + " " + p.minecraftVersion()
                    + " " + p.loaderType().displayName()
                    + " " + p.versionId()).toLowerCase();
            if (haystack.contains(query)) {
                filtered.add(p);
            }
        }
        if (selected != null && filtered.contains(selected)) {
            instanceList.getSelectionModel().select(selected);
        } else if (!filtered.isEmpty()
                && instanceList.getSelectionModel().getSelectedItem() == null) {
            instanceList.getSelectionModel().selectFirst();
        }
    }

    private void selectInitial(String preselectedId) {
        if (preselectedId != null) {
            for (ModdedProfile p : filtered) {
                if (p.id().equals(preselectedId)) {
                    instanceList.getSelectionModel().select(p);
                    return;
                }
            }
        }
        if (!filtered.isEmpty()
                && instanceList.getSelectionModel().getSelectedItem() == null) {
            instanceList.getSelectionModel().selectFirst();
        }
    }

    private void updateSaveButton() {
        saveButton.setDisable(
                instanceList.getSelectionModel().getSelectedItem() == null
                        || nameField.getText().isBlank()
                        || (!modsCheck.isSelected()
                                && !configsCheck.isSelected()));
    }

    /**
     * Shows the dialog and returns the build to save, or {@code null}
     * if it was cancelled.
     */
    public static Result show(Stage owner, List<ModdedProfile> instances,
                              String preselectedId) {
        List<ModdedProfile> modded = new ArrayList<>();
        for (ModdedProfile p : instances) {
            if (!p.isVanilla()) {
                modded.add(p);
            }
        }
        if (modded.isEmpty()) {
            return null;
        }
        QuickSaveBuildDialog dialog =
                new QuickSaveBuildDialog(owner, modded, preselectedId);
        dialog.showAndWait();
        return dialog.result;
    }
}
