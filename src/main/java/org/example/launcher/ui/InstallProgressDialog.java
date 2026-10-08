package org.example.launcher.ui;

import java.util.Locale;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.infrastructure.download.DownloadResult;
import org.example.launcher.infrastructure.download.DownloadTask;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.i18n.Lang;

/**
 * Модальный диалог прогресса установки версии Minecraft.
 * <p>
 * Реализует {@link InstallationProgress} для получения обновлений от
 * установщика и показывает полосу прогресса, текущий файл и статистику.
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
        setTitle(Lang.tr("install.title"));

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);
        progressBar.setPrefHeight(22);

        categoryLabel = new Label(Lang.tr("install.preparing"));
        categoryLabel.getStyleClass().add("install-category");

        fileLabel = new Label("");
        fileLabel.getStyleClass().add("install-file");
        fileLabel.setWrapText(true);
        fileLabel.setMaxWidth(420);

        statsLabel = new Label("");
        statsLabel.getStyleClass().add("install-stats");

        statusLabel = new Label(Lang.tr("install.initializing"));
        statusLabel.getStyleClass().add("install-status");

        closeButton = new Button(Lang.tr("button.close"));
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

        Platform.runLater(() -> {
            statusLabel.setText(Lang.tr("install.files", totalTasks));
            progressBar.setProgress(0);
            updateStats();
        });
    }

    @Override
    public void onFileStart(int taskIndex, DownloadTask task) {
        Platform.runLater(() -> {
            categoryLabel.setText(Lang.tr("install.row",
                    task.category(), taskIndex + 1, totalTasks));
            fileLabel.setText(task.name());
        });
    }

    @Override
    public void onFileComplete(int taskIndex, DownloadResult result) {
        completedTasks++;
        if (result.isDownloaded()) downloadedCount++;
        else if (result.isSkipped()) skippedCount++;
        else if (result.isFailed()) failedCount++;

        Platform.runLater(() -> {
            double progress = (double) completedTasks / totalTasks;
            progressBar.setProgress(progress);
            updateStats();
        });
    }

    @Override
    public void onComplete(InstallationResult result) {
        Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            if (result.isSuccess()) {
                statusLabel.setText(Lang.tr("install.complete"));
                statusLabel.setStyle("-fx-text-fill: #a6e3a1;");
            } else {
                statusLabel.setText(
                        Lang.tr("install.errors", result.failed()));
                statusLabel.setStyle("-fx-text-fill: #f38ba8;");
            }
            categoryLabel.setText(Lang.tr("install.summary",
                    result.downloaded(), result.skipped(), result.failed(),
                    result.totalTasks(),
                    formatBytes(result.totalBytesDownloaded())));
            fileLabel.setText("");
            closeButton.setDisable(false);
        });
    }

    private void updateStats() {
        statsLabel.setText(Lang.tr("install.stats",
                downloadedCount, skippedCount, failedCount));
    }

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;

    private static String formatBytes(long bytes) {
        if (bytes < BYTES_PER_KB) {
            return bytes + " B";
        }
        if (bytes < BYTES_PER_MB) {
            return (bytes / BYTES_PER_KB) + " " + Lang.tr("unit.kb");
        }
        if (bytes < BYTES_PER_GB) {
            return String.format(Locale.ROOT, "%.1f %s",
                    bytes / (double) BYTES_PER_MB, Lang.tr("unit.mb"));
        }
        return String.format(Locale.ROOT, "%.2f %s",
                bytes / (double) BYTES_PER_GB, Lang.tr("unit.gb"));
    }
}
