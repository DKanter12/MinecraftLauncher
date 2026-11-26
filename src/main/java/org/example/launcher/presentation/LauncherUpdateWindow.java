package org.example.launcher.presentation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.infrastructure.updater.AppVersion;
import org.example.launcher.infrastructure.updater.LauncherRestartManager;
import org.example.launcher.infrastructure.updater.LauncherUpdate;
import org.example.launcher.infrastructure.updater.LauncherUpdateManager;
import org.example.launcher.infrastructure.updater.UpdateService;
import org.example.launcher.i18n.Lang;

/**
 * Диалог обновления в стиле остальных модальных окон лаунчера:
 * затемнённый размытый фон, карточка {@code .modal-card}, заголовок
 * {@code .modal-title} и кнопки {@code .modal-button-*}.
 * <p>
 * Показывает опубликованное в главной ветке обновление и либо скачивает
 * его, либо применяет уже подготовленное. Применение передаёт управление
 * скрипту-апдейтеру и завершает лаунчер — заблокированные jar нельзя
 * заменить на ходу. Когда раскладка приложения не распознана (запуск из
 * dev), вместо этого показываются подготовленные файлы для ручного
 * копирования.
 */
public class LauncherUpdateWindow extends Stage {

    private final UpdateService service;
    private UpdateService.CheckResult result;

    private final Label statusLabel = new Label();
    private final Button primaryButton = new Button();
    private final Button laterButton = new Button(Lang.tr("button.later"));
    private final Button closeButton = new Button("\u2715");
    private final ProgressBar progressBar = new ProgressBar(0);

    private boolean downloading;

    public LauncherUpdateWindow(Stage owner, UpdateService service,
                                UpdateService.CheckResult result) {
        this.service = service;
        this.result = result;

        // Прозрачное окно поверх владельца: фон рисует сама сцена
        // (размытый снапшот + затемнение), как в NewInstanceDialog.
        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("update.title"));

        // -- Заголовок и круглая кнопка закрытия --
        Label titleLabel = new Label(Lang.tr("update.title"));
        titleLabel.getStyleClass().add("modal-title");

        closeButton.getStyleClass().add("modal-close");
        closeButton.setAccessibleText(Lang.tr("button.close"));
        closeButton.setOnAction(e -> requestClose());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(12, titleLabel, headerSpacer, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // -- Строка версий: установлено → доступно --
        Label installedCaption = new Label(Lang.tr("update.installed"));
        installedCaption.getStyleClass().add("update-version-label");
        Label installedValue = new Label(AppVersion.current());
        installedValue.getStyleClass().add("update-version-value");
        Label arrow = new Label("\u2192");
        arrow.getStyleClass().add("update-version-arrow");
        Label availableCaption = new Label(Lang.tr("update.available.version"));
        availableCaption.getStyleClass().add("update-version-label");
        Label availableValue = new Label(result.update() != null
                ? result.update().version() : "?");
        availableValue.getStyleClass().addAll("update-version-value",
                "update-version-new");

        HBox versionRow = new HBox(8, installedCaption, installedValue, arrow,
                availableCaption, availableValue);
        versionRow.setAlignment(Pos.CENTER_LEFT);
        versionRow.getStyleClass().add("update-version-row");

        // -- Список изменений --
        Label notesTitle = new Label(Lang.tr("update.notes"));
        notesTitle.getStyleClass().add("section-title");

        TextArea notesArea = new TextArea(result.update() != null
                ? result.update().notes() : "");
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(6);
        notesArea.setPrefHeight(132);
        notesArea.setMaxWidth(Double.MAX_VALUE);
        notesArea.getStyleClass().add("update-notes");
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        // -- Прогресс загрузки --
        progressBar.getStyleClass().add("update-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        statusLabel.getStyleClass().add("update-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // -- Кнопки --
        primaryButton.getStyleClass().add("modal-button-primary");
        primaryButton.setDefaultButton(true);
        laterButton.getStyleClass().add("modal-button-ghost");
        laterButton.setCancelButton(true);
        laterButton.setOnAction(e -> requestClose());

        HBox buttons = new HBox(8, laterButton, primaryButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        VBox card = new VBox(12, header, versionRow, notesTitle, notesArea,
                progressBar, statusLabel, buttons);
        card.getStyleClass().add("modal-card");
        card.setPrefWidth(540);
        card.setMaxWidth(540);

        StackPane overlay = new StackPane();
        NewInstanceDialog.addBlurBackdrop(overlay, owner);
        HBox centered = new HBox(card);
        centered.setAlignment(Pos.CENTER);
        overlay.getChildren().add(centered);

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);

        if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
            setWidth(owner.getWidth());
            setHeight(owner.getHeight());
            setX(owner.getX());
            setY(owner.getY());
        } else {
            setWidth(640);
            setHeight(560);
        }

        setOnCloseRequest(e -> {
            if (downloading) {
                e.consume();
            }
        });

        refreshPrimary();
    }

    /** Закрывает окно, но не даёт прервать загрузку на середине. */
    private void requestClose() {
        if (!downloading) {
            close();
        }
    }

    private void refreshPrimary() {
        primaryButton.setDisable(false);
        laterButton.setDisable(false);
        closeButton.setDisable(false);
        downloading = false;
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        statusLabel.getStyleClass().remove("update-status-error");

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

    private void setBusy(boolean busy) {
        downloading = busy;
        primaryButton.setDisable(busy);
        laterButton.setDisable(busy);
        closeButton.setDisable(busy);
    }

    private void onDownload() {
        LauncherUpdate update = result.update();
        if (update == null) {
            return;
        }
        setBusy(true);
        statusLabel.getStyleClass().remove("update-status-error");
        statusLabel.setText(Lang.tr("update.downloading", update.version()));
        progressBar.setProgress(0);
        progressBar.setVisible(true);
        progressBar.setManaged(true);

        Thread worker = new Thread(() -> {
            try {
                Path packageFile = service.download(update,
                        (downloaded, total) -> Platform.runLater(() -> {
                            String done = formatBytes(downloaded);
                            if (total > 0) {
                                progressBar.setProgress(Math.min(1.0,
                                        downloaded / (double) total));
                                statusLabel.setText(Lang.tr("update.progress",
                                        update.version(), done,
                                        formatBytes(total)));
                            } else {
                                progressBar.setProgress(
                                        ProgressBar.INDETERMINATE_PROGRESS);
                                statusLabel.setText(Lang.tr("update.downloading",
                                        update.version() + " " + done));
                            }
                        }));
                Platform.runLater(() -> statusLabel.setText(
                        Lang.tr("update.unpacking", update.version())));
                service.stage(packageFile, update);
                Platform.runLater(() -> {
                    result = new UpdateService.CheckResult(
                            UpdateService.Status.STAGED, update,
                            Lang.tr("update.staged", update.version()));
                    refreshPrimary();
                });
            } catch (IOException | RuntimeException e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    statusLabel.getStyleClass().add("update-status-error");
                    statusLabel.setText(Lang.tr("update.failed",
                            friendlyError(e)));
                    primaryButton.setText(Lang.tr("update.retry"));
                    primaryButton.setOnAction(ev -> onDownload());
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
    }

    private void onRestartApply() {
        LauncherUpdate update = result.update();
        Optional<LauncherUpdateManager.PendingUpdate> staged =
                service.stagedUpdate();
        if (update == null || staged.isEmpty()) {
            statusLabel.setText(Lang.tr("update.nothing"));
            return;
        }
        try {
            Optional<Path> updater = new LauncherRestartManager()
                    .restartWithUpdate(service, staged.get().stageDir(),
                            update.version(), ProcessHandle.current().pid());
            if (updater.isEmpty()) {
                statusLabel.setText(Lang.tr("update.manual",
                        staged.get().stageDir()));
                primaryButton.setDisable(true);
                return;
            }
            Platform.exit();
        } catch (IOException | RuntimeException e) {
            statusLabel.setText(Lang.tr("update.startfailed",
                    e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    /** Понятное сообщение вместо технического текста HTTP-ошибки. */
    private static String friendlyError(Throwable error) {
        String message = error.getMessage() != null
                ? error.getMessage() : error.toString();
        if (message.contains("HTTP 404")) {
            return Lang.tr("update.notfound");
        }
        if (message.contains("checksum mismatch")) {
            return Lang.tr("update.checksum");
        }
        return message;
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
        new LauncherUpdateWindow(owner, service, result).showAndWait();
    }
}
