package org.example.launcher.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.service.BuildService;

/**
 * Modal dialog for choosing the build an instance launches with (or
 * none): lists the instance's saved builds with their contents,
 * marks the active one, allows deleting a build. Double-click or
 * Select applies the choice; the build's folders are replaced at
 * launch.
 */
public class SelectBuildDialog extends Stage {

    /**
     * The chosen build.
     *
     * @param buildName sanitized build name, or {@code null} to
     *                  launch with the instance's current folders
     */
    public record Result(String buildName) {
    }

    /** Sentinel row: launch without applying any build. */
    private static final BuildService.BuildInfo NONE =
            new BuildService.BuildInfo(null, false, false);

    private Result result;

    private final ListView<BuildService.BuildInfo> buildList = new ListView<>();
    private final Button selectButton = new Button("Select");
    private final Button deleteButton = new Button("Delete");

    private final BuildService buildService;
    private final Path gameDir;
    private final String active;

    public SelectBuildDialog(Stage owner,
                             String instanceName,
                             String active,
                             BuildService buildService,
                             Path gameDir) {
        this.buildService = buildService;
        this.gameDir = gameDir;
        this.active = active;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Select Build — " + instanceName);

        Label listLabel = new Label("Builds of this instance");
        listLabel.getStyleClass().add("section-title");
        buildList.getStyleClass().add("profile-list");
        buildList.setPrefHeight(240);
        buildList.setPlaceholder(new Label(
                "No builds saved yet — create one with 'Save Build'."));
        buildList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(BuildService.BuildInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item.name() == null) {
                    Label title = new Label("No build");
                    title.getStyleClass().add("profile-name");
                    Label details = new Label(
                            "launch with the current mods and configs");
                    details.getStyleClass().add("profile-summary");
                    setText(null);
                    setGraphic(new VBox(2, title, details));
                } else {
                    Label title = new Label(item.name()
                            + (item.name().equals(SelectBuildDialog.this.active)
                                    ? "   · active" : ""));
                    title.getStyleClass().add("profile-name");
                    Label details = new Label(item.description());
                    details.getStyleClass().add("profile-summary");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    HBox row = new HBox(8, title, spacer, details);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(row);
                }
            }
        });
        buildList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> updateButtons());
        buildList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2
                    && buildList.getSelectionModel().getSelectedItem() != null) {
                onSelect();
            }
        });
        reloadItems();

        selectButton.getStyleClass().add("install-close-button");
        selectButton.setDefaultButton(true);
        selectButton.setDisable(true);
        selectButton.setOnAction(e -> onSelect());

        deleteButton.getStyleClass().add("quick-select-button");
        deleteButton.setDisable(true);
        deleteButton.setOnAction(e -> onDelete());

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        Region buttonSpacer = new Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, deleteButton, buttonSpacer,
                cancelButton, selectButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        Label hint = new Label(
                "The selected build replaces the instance's mods and config "
                        + "folders at launch.");
        hint.getStyleClass().add("quick-select-label");
        hint.setWrapText(true);

        VBox content = new VBox(6, listLabel, buildList, hint, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(440);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    private void reloadItems() {
        List<BuildService.BuildInfo> items = new ArrayList<>();
        items.add(NONE);
        try {
            items.addAll(buildService.listBuilds(gameDir));
        } catch (IOException e) {
            // List stays at the "No build" row; deletion/selecting a
            // build then reports the error
        }
        buildList.getItems().setAll(items);
        if (items.size() > 1) {
            for (BuildService.BuildInfo info : items) {
                if (info.name() != null && info.name().equals(active)) {
                    buildList.getSelectionModel().select(info);
                    return;
                }
            }
        }
        buildList.getSelectionModel().selectFirst();
    }

    private void updateButtons() {
        BuildService.BuildInfo selected =
                buildList.getSelectionModel().getSelectedItem();
        boolean hasBuild = selected != null && selected.name() != null;
        selectButton.setDisable(selected == null);
        deleteButton.setDisable(!hasBuild);
    }

    private void onSelect() {
        BuildService.BuildInfo selected =
                buildList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        result = new Result(selected.name()); // null = no build
        close();
    }

    private void onDelete() {
        BuildService.BuildInfo selected =
                buildList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.name() == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Build");
        confirm.setHeaderText("Delete build '" + selected.name() + "'?");
        confirm.setContentText(
                "The saved mods/configs snapshot is removed. The instance's "
                        + "current folders stay untouched.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    buildService.deleteBuild(gameDir, selected.name());
                    reloadItems();
                } catch (IOException e) {
                    ErrorDialog.show(this, "Cannot Delete Build",
                            e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Shows the dialog and returns the chosen build (name
     * {@code null} = no build), or {@code null} if cancelled.
     */
    public static Result show(Stage owner,
                              String instanceName,
                              String active,
                              BuildService buildService,
                              Path gameDir) {
        SelectBuildDialog dialog = new SelectBuildDialog(
                owner, instanceName, active, buildService, gameDir);
        dialog.showAndWait();
        return dialog.result;
    }
}
