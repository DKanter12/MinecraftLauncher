package org.example.launcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.distribution.BuildFileCategory;
import org.example.launcher.distribution.BuildFileEntry;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.api.AdminLauncherServerApi;
import org.example.launcher.install.Sha1ChecksumVerifier;
import org.example.launcher.model.ModdedProfile;

/**
 * Administrator dialog: publishes the current instance's mods and
 * configs as a build on the launcher server. Registers the build,
 * uploads every file and publishes the version, after which regular
 * users see it in their server builds list.
 *
 * <p>Only reachable with an ADMIN session — the launcher hides the
 * entry point and the server rejects non-administrator calls.</p>
 */
public class AdminPublishDialog extends Stage {

    /** The published build. */
    public record Result(String buildId, String version, String displayName) {
    }

    private Result result;

    private final ServerSession session;
    private final AdminLauncherServerApi adminApi;
    private final ModdedProfile profile;
    private final Path gameDir;

    private final TextField idField = new TextField();
    private final TextField versionField = new TextField("1.0.0");
    private final TextField nameField = new TextField();
    private final TextField descriptionField = new TextField();
    private final Button publishButton = new Button("Publish");
    private final Label statusLabel = new Label();

    public AdminPublishDialog(Stage owner, ServerSession session,
                              AdminLauncherServerApi adminApi,
                              ModdedProfile profile, Path gameDir) {
        this.session = session;
        this.adminApi = adminApi;
        this.profile = profile;
        this.gameDir = gameDir;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Publish Build — " + profile.name());

        Label titleLabel = new Label("Publish the current instance as a build");
        titleLabel.getStyleClass().add("section-title");

        Label idLabel = new Label("Build id (unique, stays the same across versions)");
        idLabel.getStyleClass().add("quick-select-label");
        idField.getStyleClass().add("search-field");
        idField.setPromptText("e.g. bettersurvival");

        Label versionLabel = new Label("Version (bump to release an update)");
        versionLabel.getStyleClass().add("quick-select-label");
        versionField.getStyleClass().add("search-field");

        Label nameLabel = new Label("Display name");
        nameLabel.getStyleClass().add("quick-select-label");
        nameField.getStyleClass().add("search-field");
        nameField.setPromptText("e.g. Better Survival");

        Label descriptionLabel = new Label("Description (optional)");
        descriptionLabel.getStyleClass().add("quick-select-label");
        descriptionField.getStyleClass().add("search-field");

        statusLabel.getStyleClass().add("quick-select-label");
        statusLabel.setWrapText(true);

        publishButton.getStyleClass().add("install-close-button");
        publishButton.setDefaultButton(true);
        publishButton.setDisable(true);
        publishButton.setOnAction(e -> onPublish());
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        idField.textProperty().addListener((o, a, v) -> updatePublishButton());
        versionField.textProperty().addListener((o, a, v) -> updatePublishButton());
        nameField.textProperty().addListener((o, a, v) -> updatePublishButton());

        HBox buttons = new HBox(8, cancelButton, publishButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        Label hint = new Label("Every file of the instance's mods and config folders "
                + "is uploaded; the build runs with " + profile.loaderType().displayName()
                + " " + profile.minecraftVersion() + " (" + profile.versionId() + ").");
        hint.getStyleClass().add("quick-select-label");
        hint.setWrapText(true);

        VBox content = new VBox(6, titleLabel, idLabel, idField, versionLabel,
                versionField, nameLabel, nameField, descriptionLabel, descriptionField,
                hint, statusLabel, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(460);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    private void updatePublishButton() {
        publishButton.setDisable(idField.getText().isBlank()
                || versionField.getText().isBlank()
                || nameField.getText().isBlank());
    }

    private void onPublish() {
        String buildId = idField.getText().trim();
        String version = versionField.getText().trim();
        publishButton.setDisable(true);
        statusLabel.setText("Collecting files…");

        Thread worker = new Thread(() -> {
            try {
                List<BuildFileEntry> files = collectFiles();
                if (files.isEmpty()) {
                    throw new IOException(
                            "Nothing to publish — the instance has no mods or configs");
                }
                BuildSummary summary = new BuildSummary(buildId, version,
                        nameField.getText().trim(), descriptionField.getText().trim(),
                        profile.loaderType(), profile.minecraftVersion(),
                        profile.loaderVersion());
                adminApi.createBuild(session, summary);
                for (int i = 0; i < files.size(); i++) {
                    BuildFileEntry file = files.get(i);
                    int index = i + 1;
                    Platform.runLater(() -> statusLabel.setText(
                            "Uploading " + index + " of " + files.size() + "…"));
                    Path source = gameDir.resolve(file.category().folder())
                            .resolve(file.relativePath());
                    adminApi.uploadBuildFile(session, buildId, version,
                            file.category(), file.relativePath(), source);
                }
                adminApi.publishBuild(session, buildId, version);
                Platform.runLater(() -> {
                    result = new Result(buildId, version, nameField.getText().trim());
                    close();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText(e.getMessage() != null ? e.getMessage()
                            : "Publishing failed");
                    publishButton.setDisable(false);
                });
            }
        }, "admin-publish");
        worker.setDaemon(true);
        worker.start();
    }

    private List<BuildFileEntry> collectFiles() throws IOException {
        List<BuildFileEntry> files = new ArrayList<>();
        collectCategory(files, gameDir.resolve("mods"), BuildFileCategory.MODS);
        collectCategory(files, gameDir.resolve("config"), BuildFileCategory.CONFIGS);
        return files;
    }

    private void collectCategory(List<BuildFileEntry> files, Path folder,
                                 BuildFileCategory category) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(folder)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                String relativePath = folder.relativize(file).toString()
                        .replace('\\', '/');
                try {
                    files.add(new BuildFileEntry(relativePath, category,
                            Sha1ChecksumVerifier.computeSha1(file),
                            Files.size(file)));
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException("SHA-1 is not available", e);
                }
            }
        }
    }

    /**
     * Shows the dialog and returns the published build, or
     * {@code null} when it was cancelled or failed.
     */
    public static Result show(Stage owner, ServerSession session,
                              AdminLauncherServerApi adminApi,
                              ModdedProfile profile, Path gameDir) {
        AdminPublishDialog dialog = new AdminPublishDialog(owner, session, adminApi,
                profile, gameDir);
        dialog.showAndWait();
        return dialog.result;
    }
}
