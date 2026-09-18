package org.example.launcher.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.RemoteBuildService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.api.AdminLauncherServerApi;
import org.example.launcher.model.ModdedProfile;

/**
 * Modal dialog that lists the builds the administrator published on
 * the launcher server and installs them into the current instance.
 *
 * <p>Every row shows the build with its version; the action button
 * reflects the local state: install a build that is not present yet,
 * update one whose server version is newer, or shows the installed
 * version when it is current. Installing a build never touches other
 * builds — each build id gets its own folder inside this
 * instance.</p>
 */
public class ServerBuildsDialog extends Stage {

    /** What happened in the dialog, for the main status line. */
    public record Outcome(String buildId, String displayName, String version,
                          boolean wasUpdate) {
    }

    private Outcome outcome;

    private final ServerSession session;
    private final RemoteBuildService remoteBuilds;
    private final AdminLauncherServerApi adminApi;
    private final ModdedProfile profile;
    private final Path gameDir;

    private final ObservableList<BuildSummary> items = FXCollections.observableArrayList();
    private final ListView<BuildSummary> buildList = new ListView<>(items);
    private final Button actionButton = new Button("Install");
    private final Button publishButton = new Button("Publish Current Instance…");
    private final Label statusLabel = new Label("Loading builds…");

    public ServerBuildsDialog(Stage owner, ServerSession session,
                              RemoteBuildService remoteBuilds,
                              AdminLauncherServerApi adminApi,
                              ModdedProfile profile, Path gameDir) {
        this.session = session;
        this.remoteBuilds = remoteBuilds;
        this.adminApi = adminApi;
        this.profile = profile;
        this.gameDir = gameDir;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Server Builds — " + profile.name());

        Label titleLabel = new Label("Builds from the launcher server");
        titleLabel.getStyleClass().add("section-title");

        Label hintLabel = new Label(
                "Published by the administrator. Installing downloads the build "
                        + "into its own folder of this instance — other builds, "
                        + "including your saved ones, are not touched.");
        hintLabel.getStyleClass().add("quick-select-label");
        hintLabel.setWrapText(true);

        buildList.getStyleClass().add("profile-list");
        buildList.setPrefHeight(240);
        buildList.setCellFactory(list -> new BuildSummaryCell());
        buildList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> updateActionState(selected));

        statusLabel.getStyleClass().add("quick-select-label");
        statusLabel.setWrapText(true);

        actionButton.getStyleClass().add("install-close-button");
        actionButton.setDisable(true);
        actionButton.setOnAction(e -> onAction(buildList.getSelectionModel().getSelectedItem()));

        publishButton.getStyleClass().add("install-close-button");
        publishButton.setVisible(session.isAdmin());
        publishButton.setManaged(session.isAdmin());
        publishButton.setOnAction(e -> onPublish());

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("install-close-button");
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, publishButton, actionButton, closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6, titleLabel, hintLabel, buildList,
                statusLabel, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(520);
        VBox.setVgrow(buildList, Priority.ALWAYS);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);

        loadCatalog();
    }

    private void loadCatalog() {
        Thread worker = new Thread(() -> {
            try {
                List<BuildSummary> catalog = remoteBuilds.catalog(session);
                Platform.runLater(() -> {
                    items.setAll(catalog);
                    if (catalog.isEmpty()) {
                        statusLabel.setText("No builds published yet.");
                    } else {
                        buildList.getSelectionModel().selectFirst();
                        statusLabel.setText(catalog.size() + " build(s) available.");
                    }
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() ->
                        statusLabel.setText(e.getMessage() != null ? e.getMessage()
                                : "Failed to load builds"));
            }
        }, "server-builds-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void updateActionState(BuildSummary selected) {
        if (selected == null) {
            actionButton.setDisable(true);
            actionButton.setText("Install");
            return;
        }
        Optional<BuildDescriptor> installed =
                remoteBuilds.installedBuild(gameDir, selected.id());
        if (installed.isEmpty()) {
            actionButton.setDisable(false);
            actionButton.setText("Install");
        } else if (remoteBuilds.updateAvailable(installed.get(), selected)) {
            actionButton.setDisable(false);
            actionButton.setText("Update to " + selected.version());
        } else {
            actionButton.setDisable(true);
            actionButton.setText("Installed (v " + installed.get().version() + ")");
        }
    }

    private void onAction(BuildSummary selected) {
        if (selected == null) {
            return;
        }
        boolean wasUpdate = remoteBuilds.installedBuild(gameDir, selected.id()).isPresent();
        actionButton.setDisable(true);
        buildList.setDisable(true);
        statusLabel.setText((wasUpdate ? "Updating " : "Installing ")
                + selected.displayName() + " " + selected.version() + "…");

        Thread worker = new Thread(() -> {
            try {
                remoteBuilds.install(session, selected, gameDir);
                Platform.runLater(() -> {
                    outcome = new Outcome(selected.id(), selected.displayName(),
                            selected.version(), wasUpdate);
                    statusLabel.setText((wasUpdate ? "Updated " : "Installed ")
                            + selected.displayName() + " " + selected.version()
                            + ".");
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() ->
                        statusLabel.setText(e.getMessage() != null ? e.getMessage()
                                : "Installation failed"));
            } finally {
                Platform.runLater(() -> {
                    buildList.setDisable(false);
                    updateActionState(buildList.getSelectionModel().getSelectedItem());
                });
            }
        }, "server-build-install");
        worker.setDaemon(true);
        worker.start();
    }

    private void onPublish() {
        AdminPublishDialog.Result published = AdminPublishDialog.show(
                this, session, adminApi, profile, gameDir);
        if (published != null) {
            statusLabel.setText("Published " + published.displayName()
                    + " " + published.version() + " to the launcher server.");
            loadCatalog();
        }
    }

    private static final class BuildSummaryCell extends ListCell<BuildSummary> {
        @Override
        protected void updateItem(BuildSummary item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label name = new Label(item.displayName() + "  " + item.version());
            name.getStyleClass().add("profile-name");
            Label details = new Label(item.loaderType().displayName() + " "
                    + item.minecraftVersion()
                    + (item.description() == null || item.description().isBlank()
                    ? "" : "  ·  " + item.description()));
            details.getStyleClass().add("profile-summary");
            VBox box = new VBox(2, name, details);
            setGraphic(box);
            setText(null);
        }
    }

    /**
     * Shows the dialog; returns the last installed/updated build, or
     * {@code null} when nothing was installed.
     */
    public static Outcome show(Stage owner, ServerSession session,
                               RemoteBuildService remoteBuilds,
                               AdminLauncherServerApi adminApi,
                               ModdedProfile profile, Path gameDir) {
        ServerBuildsDialog dialog = new ServerBuildsDialog(owner, session,
                remoteBuilds, adminApi, profile, gameDir);
        dialog.showAndWait();
        return dialog.outcome;
    }
}
