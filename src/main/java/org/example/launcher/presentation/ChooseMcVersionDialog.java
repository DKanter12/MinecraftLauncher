package org.example.launcher.presentation;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.i18n.Lang;
import org.example.launcher.infrastructure.server.BuildSummary;

/**
 * Выбор версии Minecraft для сборки, в которой он не указан.
 *
 * <p>Случай «сборка с администратором опубликовала на Яндекс Диске
 * папку с модами без {@code build.json}»: из имени и содержимого
 * версию игры надёжно вычислить нельзя, а установка и запуск без неё
 * невозможны. Диалог предлагает все известные версии из файла версий
 * лаунчера (релизы первыми) и подставляет выбор в установку.</p>
 */
public class ChooseMcVersionDialog extends Stage {

    private final ComboBox<MinecraftVersion> combo = new ComboBox<>();

    private ChooseMcVersionDialog(Stage owner, BuildSummary summary,
                                  List<MinecraftVersion> versions) {
        initStyle(StageStyle.TRANSPARENT);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        if (owner != null) {
            setWidth(owner.getWidth());
            setHeight(owner.getHeight());
            setX(owner.getX());
            setY(owner.getY());
        }

        // --- Шапка: заголовок + круглый крестик ---
        Label headerTitle = new Label(Lang.tr("install.pickmc.title"));
        headerTitle.getStyleClass().add("modal-title");
        Button closeButton = new Button("\u2715");
        closeButton.getStyleClass().add("modal-close");
        closeButton.setOnAction(e -> close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(12, headerTitle, headerSpacer,
                closeButton);
        headerRow.setAlignment(Pos.CENTER_RIGHT);

        Label description = new Label(Lang.tr("install.pickmc.desc",
                summary.displayName()));
        description.getStyleClass().add("import-text");
        description.setWrapText(true);

        // Комбобокс версий: «1.21.11 — релиз», релиз выбран по умолчанию
        combo.getStyleClass().add("search-field");
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(MinecraftVersion version) {
                if (version == null) {
                    return "";
                }
                return version.id() + " \u2014 " + typeLabel(version.type());
            }

            @Override
            public MinecraftVersion fromString(String string) {
                return null;
            }
        });

        // Релизы первыми — это выбор 90% пользователей; внутри групп
        // сохраняется порядок манифеста (от новых к старым)
        List<MinecraftVersion> releases = versions.stream()
                .filter(v -> v.type() == VersionType.RELEASE)
                .toList();
        List<MinecraftVersion> other = versions.stream()
                .filter(v -> v.type() != VersionType.RELEASE)
                .toList();
        combo.setItems(FXCollections.observableArrayList());
        combo.getItems().addAll(releases);
        combo.getItems().addAll(other);
        if (!releases.isEmpty()) {
            combo.getSelectionModel().selectFirst();
        } else if (!other.isEmpty()) {
            combo.getSelectionModel().selectFirst();
        }

        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("modal-button-ghost");
        cancelButton.setCancelButton(true);
        cancelButton.setPrefWidth(140);
        cancelButton.setMinWidth(Region.USE_PREF_SIZE);
        cancelButton.setOnAction(e -> close());

        Button okButton = new Button(Lang.tr("install.pickmc.ok"));
        okButton.getStyleClass().add("modal-button-primary");
        okButton.setDefaultButton(true);
        okButton.setPrefWidth(140);
        okButton.setMinWidth(Region.USE_PREF_SIZE);
        okButton.setDisable(combo.getValue() == null);
        okButton.setOnAction(e -> {
            chosen = combo.getValue();
            close();
        });
        combo.valueProperty().addListener((obs, o, v) ->
                okButton.setDisable(v == null));

        HBox buttons = new HBox(8, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(16, 0, 0, 0));

        VBox content = new VBox(12, headerRow, description, combo, buttons);
        content.setPrefWidth(420);

        VBox card = new VBox(content);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(420);

        // Glassmorphism backdrop: общий каркас с NewInstanceDialog
        StackPane overlay = new StackPane();
        NewInstanceDialog.addBlurBackdrop(overlay, owner);
        HBox centered = new HBox(card);
        centered.setAlignment(Pos.CENTER);
        overlay.getChildren().add(centered);

        Scene scene = new Scene(overlay, owner != null ? owner.getWidth()
                : 480, owner != null ? owner.getHeight() : 420);
        scene.setFill(Color.TRANSPARENT);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
        AppIcon.apply(this);
    }

    /** Выбранная в диалоге версия игры ({@code null} — отмена). */
    private MinecraftVersion chosen;

    /** Локализованный тип версии для подписи комбо-пункта. */
    private static String typeLabel(VersionType type) {
        if (type == VersionType.RELEASE) {
            return Lang.tr("new.type.release");
        }
        if (type == VersionType.SNAPSHOT) {
            return Lang.tr("new.type.snapshot");
        }
        if (type == VersionType.BETA) {
            return Lang.tr("new.type.oldbeta");
        }
        if (type == VersionType.ALPHA) {
            return Lang.tr("new.type.oldalpha");
        }
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Показывает диалог.
     *
     * @return выбранная версия игры, либо {@code null} при отмене
     */
    public static MinecraftVersion show(Stage owner, BuildSummary summary,
                                        List<MinecraftVersion> versions) {
        ChooseMcVersionDialog dialog = new ChooseMcVersionDialog(owner,
                summary, versions);
        dialog.showAndWait();
        return dialog.chosen;
    }
}
