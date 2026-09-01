package org.example.launcher.ui;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Small modal picker for the loader version of a version browser
 * entry (e.g. {@code Fabric 0.16.14 for MC 1.20.1}): lists every
 * loader version compatible with the chosen Minecraft version,
 * newest first. The newest one is the default, but any other can be
 * picked — by clicking OK or double-clicking the entry.
 */
public class LoaderVersionDialog extends Stage {

    private ModLoaderVersion result;

    private LoaderVersionDialog(Stage owner,
                                ModLoaderType family,
                                String mcId,
                                List<ModLoaderVersion> versions,
                                ModLoaderVersion current) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(family.displayName() + " version — MC " + mcId);

        Label title = new Label(family.displayName() + " version");
        title.getStyleClass().add("section-title");

        Label hint = new Label(
                "All listed versions are compatible with MC " + mcId
                        + ". Newest first.");
        hint.getStyleClass().add("quick-select-label");

        ListView<ModLoaderVersion> list = new ListView<>();
        list.getStyleClass().add("profile-list");
        list.setPrefHeight(240);
        list.setPrefWidth(320);
        list.getItems().setAll(versions);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ModLoaderVersion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label id = new Label(item.loaderVersion());
                    id.getStyleClass().add("profile-name");
                    Label info = new Label(
                            item.stable() ? "stable" : "beta");
                    info.getStyleClass().add("profile-summary");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    HBox cell = new HBox(8, id, spacer, info);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(cell);
                }
            }
        });
        // Double-click picks the version directly
        list.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && list.getSelectionModel()
                    .getSelectedItem() != null) {
                result = list.getSelectionModel().getSelectedItem();
                close();
            }
        });
        if (current != null) {
            for (ModLoaderVersion v : versions) {
                if (v.loaderVersion().equals(current.loaderVersion())) {
                    list.getSelectionModel().select(v);
                    break;
                }
            }
        }
        if (list.getSelectionModel().getSelectedItem() == null
                && !versions.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }

        Button okButton = new Button("OK");
        okButton.getStyleClass().add("install-close-button");
        okButton.setDefaultButton(true);
        okButton.disableProperty().bind(
                list.getSelectionModel().selectedItemProperty().isNull());
        okButton.setOnAction(e -> {
            result = list.getSelectionModel().getSelectedItem();
            close();
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());
        HBox buttons = new HBox(8, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(6, title, hint, list, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(360);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    /**
     * Shows the picker and returns the chosen loader version, or
     * {@code null} if it was cancelled.
     */
    public static ModLoaderVersion show(Stage owner,
                                        ModLoaderType family,
                                        String mcId,
                                        List<ModLoaderVersion> versions,
                                        ModLoaderVersion current) {
        LoaderVersionDialog dialog = new LoaderVersionDialog(
                owner, family, mcId, versions, current);
        dialog.showAndWait();
        return dialog.result;
    }
}
