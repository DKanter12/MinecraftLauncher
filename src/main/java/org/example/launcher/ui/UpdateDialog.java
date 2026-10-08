package org.example.launcher.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.infrastructure.updater.AppVersion;
import org.example.launcher.infrastructure.updater.LauncherUpdate;
import org.example.launcher.infrastructure.updater.UpdateApplier;
import org.example.launcher.infrastructure.updater.UpdateService;
import org.example.launcher.i18n.Lang;

/**
 * Диалог обновления: показывает опубликованное в главной ветке и либо
 * скачивает его, либо применяет уже подготовленное обновление.
 * <p>
 * Применение передаёт управление скрипту-апдейтеру и завершает лаунчер —
 * заблокированные jar нельзя заменить на ходу. Когда раскладка
 * приложения не распознана (запуск из dev), вместо этого показываются
 * подготовленные файлы для ручного копирования.
 */
public class UpdateDialog extends Stage {

    private final UpdateService service;
    private UpdateService.CheckResult result;

    private final Label statusLabel = new Label();
    private final Button primaryButton = new Button();
    private final Button laterButton = new Button(Lang.tr("button.later"));

    public UpdateDialog(Stage owner, UpdateService service,
                        UpdateService.CheckResult result) {
        this.service = service;
        this.result = result;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("update.title"));

        Label titleLabel = new Label(Lang.tr("update.title"));
        titleLabel.getStyleClass().add("section-title");

        Label versionLabel = new Label(Lang.tr("update.available",
                AppVersion.current(),
                result.update() != null ? result.update().version() : "?"));
        versionLabel.getStyleClass().add("account-title");
        versionLabel.getStyleClass().add("account-title");
        versionLabel.setWrapText(true);

        TextArea notesArea = new TextArea(
                result.update() != null ? result.update().notes() : "");
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(5);
        notesArea.getStyleClass().add("profile-jvm-args");
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        statusLabel.getStyleClass().add("quick-select-label");
        statusLabel.setWrapText(true);

        primaryButton.getStyleClass().add("install-close-button");
        primaryButton.setDefaultButton(true);
        laterButton.getStyleClass().add("install-close-button");
        laterButton.setCancelButton(true);
        laterButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, laterButton, primaryButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(8, titleLabel, versionLabel, notesArea,
                statusLabel, buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(460);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);

        refreshPrimary();
    }

    private void refreshPrimary() {
        primaryButton.setDisable(false);
        if (result.status() == UpdateService.Status.STAGED) {
            statusLabel.setText(result.detail());
            primaryButton.setText(Lang.tr("update.apply"));
            primaryButton.setOnAction(e -> onRestartApply());
        } else {
            statusLabel.setText(result.detail());
            primaryButton.setText(Lang.tr("update.download"));
            primaryButton.setOnAction(e -> onDownload());
        }
    }

    private void onDownload() {
        LauncherUpdate update = result.update();
        if (update == null) {
            return;
        }
        primaryButton.setDisable(true);
        laterButton.setDisable(true);
        statusLabel.setText(Lang.tr("update.downloading", update.version()));

        Thread worker = new Thread(() -> {
            try {
                Path packageFile = service.download(update,
                        (downloaded, total) -> Platform.runLater(() -> {
                            String done = formatBytes(downloaded);
                            if (total > 0) {
                                statusLabel.setText(Lang.tr(
                                        "update.progress", update.version(),
                                        done, formatBytes(total)));
                            } else {
                                statusLabel.setText(Lang.tr(
                                        "update.downloading",
                                        update.version() + " " + done));
                            }
                        }));
                Path stageDir = service.stage(packageFile, update);
                Platform.runLater(() -> {
                    result = new UpdateService.CheckResult(
                            UpdateService.Status.STAGED, update,
                            Lang.tr("update.staged", update.version()));
                    laterButton.setDisable(false);
                    refreshPrimary();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText(Lang.tr("update.failed",
                            e.getMessage() != null ? e.getMessage()
                                    : e.toString()));
                    primaryButton.setDisable(false);
                    laterButton.setDisable(false);
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
    }

    private void onRestartApply() {
        LauncherUpdate update = result.update();
        Optional<UpdateService.PendingUpdate> staged = service.stagedUpdate();
        if (update == null || staged.isEmpty()) {
            statusLabel.setText(Lang.tr("update.nothing"));
            return;
        }
        Optional<UpdateApplier.ApplyPlan> plan =
                UpdateApplier.plan(staged.get().stageDir(), update.version());
        if (plan.isEmpty()) {
            statusLabel.setText(Lang.tr("update.manual",
                    staged.get().stageDir()));
            primaryButton.setDisable(true);
            return;
        }
        try {
            Path updater = UpdateApplier.writeUpdater(service, plan.get());
            UpdateApplier.launchAndExit(updater,
                    ProcessHandle.current().pid());
            Platform.exit();
        } catch (IOException | RuntimeException e) {
            statusLabel.setText(Lang.tr("update.startfailed",
                    e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;

    private static String formatBytes(long bytes) {
        if (bytes < BYTES_PER_KB) {
            return bytes + " B";
        }
        if (bytes < BYTES_PER_MB) {
            return (bytes / BYTES_PER_KB) + " " + Lang.tr("unit.kb");
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s",
                bytes / (double) BYTES_PER_MB, Lang.tr("unit.mb"));
    }

    /** Показывает диалог для доступного или подготовленного обновления. */
    public static void show(Stage owner, UpdateService service,
                            UpdateService.CheckResult result) {
        if (result.status() != UpdateService.Status.AVAILABLE
                && result.status() != UpdateService.Status.STAGED) {
            return;
        }
        new UpdateDialog(owner, service, result).showAndWait();
    }
}
