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
 * Р’С‹Р±РѕСЂ РІРµСЂСЃРёРё Minecraft РґР»СЏ СЃР±РѕСЂРєРё, РІ РєРѕС‚РѕСЂРѕР№ РѕРЅ РЅРµ СѓРєР°Р·Р°РЅ.
 *
 * <p>РЎР»СѓС‡Р°Р№ В«СЃР±РѕСЂРєР° СЃ Р°РґРјРёРЅРёСЃС‚СЂР°С‚РѕСЂРѕРј РѕРїСѓР±Р»РёРєРѕРІР°Р»Р° РЅР° РЇРЅРґРµРєСЃ Р”РёСЃРєРµ
 * РїР°РїРєСѓ СЃ РјРѕРґР°РјРё Р±РµР· {@code build.json}В»: РёР· РёРјРµРЅРё Рё СЃРѕРґРµСЂР¶РёРјРѕРіРѕ
 * РІРµСЂСЃРёСЋ РёРіСЂС‹ РЅР°РґС‘Р¶РЅРѕ РІС‹С‡РёСЃР»РёС‚СЊ РЅРµР»СЊР·СЏ, Р° СѓСЃС‚Р°РЅРѕРІРєР° Рё Р·Р°РїСѓСЃРє Р±РµР· РЅРµС‘
 * РЅРµРІРѕР·РјРѕР¶РЅС‹. Р”РёР°Р»РѕРі РїСЂРµРґР»Р°РіР°РµС‚ РІСЃРµ РёР·РІРµСЃС‚РЅС‹Рµ РІРµСЂСЃРёРё РёР· С„Р°Р№Р»Р° РІРµСЂСЃРёР№
 * Р»Р°СѓРЅС‡РµСЂР° (СЂРµР»РёР·С‹ РїРµСЂРІС‹РјРё) Рё РїРѕРґСЃС‚Р°РІР»СЏРµС‚ РІС‹Р±РѕСЂ РІ СѓСЃС‚Р°РЅРѕРІРєСѓ.</p>
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

        // --- РЁР°РїРєР°: Р·Р°РіРѕР»РѕРІРѕРє + РєСЂСѓРіР»С‹Р№ РєСЂРµСЃС‚РёРє ---
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

        // РљРѕРјР±РѕР±РѕРєСЃ РІРµСЂСЃРёР№: В«1.21.11 вЂ” СЂРµР»РёР·В», СЂРµР»РёР· РІС‹Р±СЂР°РЅ РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ
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

        // Р РµР»РёР·С‹ РїРµСЂРІС‹РјРё вЂ” СЌС‚Рѕ РІС‹Р±РѕСЂ 90% РїРѕР»СЊР·РѕРІР°С‚РµР»РµР№; РІРЅСѓС‚СЂРё РіСЂСѓРїРї
        // СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ РїРѕСЂСЏРґРѕРє РјР°РЅРёС„РµСЃС‚Р° (РѕС‚ РЅРѕРІС‹С… Рє СЃС‚Р°СЂС‹Рј)
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

        // Glassmorphism backdrop: РѕР±С‰РёР№ РєР°СЂРєР°СЃ СЃ NewInstanceDialog
        StackPane overlay = new StackPane();
        NewInstanceDialog.addBlurBackdrop(overlay, owner);
        HBox centered = new HBox(card);
        centered.setAlignment(Pos.CENTER);
        overlay.getChildren().add(centered);

        Scene scene = new Scene(overlay, owner != null ? owner.getWidth()
                : 480, owner != null ? owner.getHeight() : 420);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);
    }

    /** Р’С‹Р±СЂР°РЅРЅР°СЏ РІ РґРёР°Р»РѕРіРµ РІРµСЂСЃРёСЏ РёРіСЂС‹ ({@code null} вЂ” РѕС‚РјРµРЅР°). */
    private MinecraftVersion chosen;

    /** Р›РѕРєР°Р»РёР·РѕРІР°РЅРЅС‹Р№ С‚РёРї РІРµСЂСЃРёРё РґР»СЏ РїРѕРґРїРёСЃРё РєРѕРјР±Рѕ-РїСѓРЅРєС‚Р°. */
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
     * РџРѕРєР°Р·С‹РІР°РµС‚ РґРёР°Р»РѕРі.
     *
     * @return РІС‹Р±СЂР°РЅРЅР°СЏ РІРµСЂСЃРёСЏ РёРіСЂС‹, Р»РёР±Рѕ {@code null} РїСЂРё РѕС‚РјРµРЅРµ
     */
    public static MinecraftVersion show(Stage owner, BuildSummary summary,
                                        List<MinecraftVersion> versions) {
        ChooseMcVersionDialog dialog = new ChooseMcVersionDialog(owner,
                summary, versions);
        dialog.showAndWait();
        return dialog.chosen;
    }
}
