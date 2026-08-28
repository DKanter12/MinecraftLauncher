package org.example.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Scene;

import org.example.launcher.install.DownloadResult;
import org.example.launcher.install.DownloadTask;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;

/**
 * Modal progress dialog shown during Minecraft version installation.
 * <p>
 * Implements {@link InstallationProgress} to receive updates from the
 * installer and display a progress bar, current file, and statistics.
 */
public class InstallProgressDialog extends Stage implements InstallationProgress {

    private final ProgressBar progressBar;
    private final Label fileLabel;
    private final Label categoryLabel;
    private final Label statsLabel;
    private final Label statusLabel;
    private final Button closeButton;

    private int totalTasks;
    private int completedTasks;
    private int downloadedCount;
    private int skippedCount;
    private int failedCount;

    public InstallProgressDialog(Stage owner) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle("Installing Minecraft");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);
        progressBar.setPrefHeight(22);

        categoryLabel = new Label("Preparing...");
        categoryLabel.getStyleClass().add("install-category");

        fileLabel = new Label("");
        fileLabel.getStyleClass().add("install-file");
        fileLabel.setWrapText(true);
        fileLabel.setMaxWidth(420);

        statsLabel = new Label("");
        statsLabel.getStyleClass().add("install-stats");

        statusLabel = new Label("Initializing...");
        statusLabel.getStyleClass().add("install-status");

        closeButton = new Button("Close");
        closeButton.getStyleClass().add("install-close-button");
        closeButton.setDisable(true);
        closeButton.setOnAction(e -> close());

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(
                statusLabel,
                progressBar,
                categoryLabel,
                fileLabel,
                statsLabel,
                closeButton);

        Scene scene = new Scene(content, 480, 220);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    @Override
    public void onStart(int totalTasks, long totalBytes) {
        this.totalTasks = totalTasks;
        this.completedTasks = 0;
        this.downloadedCount = 0;
        this.skippedCount = 0;
        this.failedCount = 0;

        javafx.application.Platform.runLater(() -> {
            statusLabel.setText("Installing " + totalTasks + " files");
            progressBar.setProgress(0);
            updateStats();
        });
    }

    @Override
    public void onFileStart(int taskIndex, DownloadTask task) {
        javafx.application.Platform.runLater(() -> {
            categoryLabel.setText("[" + task.category() + "] "
                    + (taskIndex + 1) + " / " + totalTasks);
            fileLabel.setText(task.name());
        });
    }

    @Override
    public void onFileComplete(int taskIndex, DownloadResult result) {
        completedTasks++;
        if (result.isDownloaded()) downloadedCount++;
        else if (result.isSkipped()) skippedCount++;
        else if (result.isFailed()) failedCount++;

        javafx.application.Platform.runLater(() -> {
            double progress = (double) completedTasks / totalTasks;
            progressBar.setProgress(progress);
            updateStats();
        });
    }

    @Override
    public void onComplete(InstallationResult result) {
        javafx.application.Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            if (result.isSuccess()) {
                statusLabel.setText("Installation complete!");
                statusLabel.setStyle("-fx-text-fill: #a6e3a1;");
            } else {
                statusLabel.setText("Installation finished with "
                        + result.failed() + " errors");
                statusLabel.setStyle("-fx-text-fill: #f38ba8;");
            }
            categoryLabel.setText(result.summary());
            fileLabel.setText("");
            closeButton.setDisable(false);
        });
    }

    private void updateStats() {
        statsLabel.setText(String.format(
                "Downloaded: %d  |  Skipped: %d  |  Failed: %d",
                downloadedCount, skippedCount, failedCount));
    }
}
