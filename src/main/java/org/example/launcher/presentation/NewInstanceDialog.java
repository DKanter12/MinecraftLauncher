package org.example.launcher.presentation;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
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
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.i18n.Lang;

/**
 * РњРѕРґР°Р»СЊРЅС‹Р№ РґРёР°Р»РѕРі СЃРѕР·РґР°РЅРёСЏ РЅРѕРІРѕРіРѕ РёРіСЂРѕРІРѕРіРѕ РёРЅСЃС‚Р°РЅСЃР° вЂ” РѕРґРёРЅ РїСЂРѕСЃС‚РѕР№ СЌРєСЂР°РЅ,
 * РІСЃС‘ РІС‹Р±РёСЂР°РµС‚СЃСЏ РёР· СЃРїРёСЃРєРѕРІ (РІРІРѕРґ СЃ РєР»Р°РІРёР°С‚СѓСЂС‹ РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ):
 * <ol>
 *   <li><b>Р—Р°РіСЂСѓР·С‡РёРє</b> вЂ” С‡РёРїС‹: Vanilla, Fabric, Forge, NeoForge, Quilt.</li>
 *   <li><b>РўРёРї РІРµСЂСЃРёРё</b> вЂ” С‡РёРїС‹ (Release, Snapshot, Old Beta, Old
 *       Alpha), РїСЂРµРґР»Р°РіР°СЋС‚СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ Vanilla вЂ” РјРѕРґРѕРІС‹Рµ Р·Р°РіСЂСѓР·С‡РёРєРё
 *       СЂР°Р±РѕС‚Р°СЋС‚ РЅР° СЂРµР»РёР·Р°С….</li>
 *   <li><b>Р’РµСЂСЃРёСЏ</b> вЂ” СЃРїРёСЃРѕРє РїРѕРґС…РѕРґСЏС‰РёС… РІРµСЂСЃРёР№, СЃРЅР°С‡Р°Р»Р° РЅРѕРІС‹Рµ
 *       (РЅР°РїСЂРёРјРµСЂ, {@code 1.21.4}, {@code 25w14a}).</li>
 *   <li><b>Р’РµСЂСЃРёСЏ Р·Р°РіСЂСѓР·С‡РёРєР°</b> вЂ” РґР»СЏ РјРѕРґРѕРІС‹С… Р·Р°РіСЂСѓР·С‡РёРєРѕРІ, Р·Р°РіСЂСѓР¶Р°РµС‚СЃСЏ
 *       Р°СЃРёРЅС…СЂРѕРЅРЅРѕ РѕС‚ РїСЂРѕРІР°Р№РґРµСЂР° Р·Р°РіСЂСѓР·С‡РёРєР° (РІСЃРµ Р·Р°РїРёСЃРё
 *       РіР°СЂР°РЅС‚РёСЂРѕРІР°РЅРЅРѕ СЃРѕРІРјРµСЃС‚РёРјС‹ СЃ РІС‹Р±СЂР°РЅРЅРѕР№ РІРµСЂСЃРёРµР№).</li>
 * </ol>
 * РњРѕР¶РЅРѕ СѓРєР°Р·Р°С‚СЊ РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Рµ JVM-Р°СЂРіСѓРјРµРЅС‚С‹ (РЅР°РїСЂРёРјРµСЂ, {@code -Xmx4G});
 * РёРјСЏ РёРЅСЃС‚Р°РЅСЃР° РІРІРѕРґРёС‚СЃСЏ СЃРІРѕР±РѕРґРЅРѕ Рё РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ СЃРѕСЃС‚РѕРёС‚ РёР· Р·Р°РіСЂСѓР·С‡РёРєР°
 * Рё РІРµСЂСЃРёРё Minecraft, РµСЃР»Рё РѕСЃС‚Р°РІР»РµРЅРѕ РїСѓСЃС‚С‹Рј.
 */
public class NewInstanceDialog extends Stage {

    /**
     * РџР°СЂР°РјРµС‚СЂС‹ СЃРѕР·РґР°РЅРЅРѕРіРѕ РёРЅСЃС‚Р°РЅСЃР°.
     *
     * @param type      РІР°РЅРёР»Р»Р° РёР»Рё СЃРµРјРµР№СЃС‚РІРѕ РјРѕРґРѕРІРѕРіРѕ Р·Р°РіСЂСѓР·С‡РёРєР°
     * @param mcVersion С†РµР»РµРІР°СЏ РІРµСЂСЃРёСЏ Minecraft
     * @param loader    РІС‹Р±СЂР°РЅРЅР°СЏ РІРµСЂСЃРёСЏ Р·Р°РіСЂСѓР·С‡РёРєР° ({@code null}
     *                  РґР»СЏ РІР°РЅРёР»Р»С‹)
     * @param name      РѕС‚РѕР±СЂР°Р¶Р°РµРјРѕРµ РёРјСЏ РёРЅСЃС‚Р°РЅСЃР° (РїСѓСЃС‚РѕРµ РѕР·РЅР°С‡Р°РµС‚
     *                  Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРѕРµ РёРјСЏ В«Р—Р°РіСЂСѓР·С‡РёРє РњРљВ»)
     */
    public record Result(ModLoaderType type,
                         MinecraftVersion mcVersion,
                         ModLoaderVersion loader,
                         String name) {
    }

    private Result result;

    private final ModLoaderRegistry registry;
    private final List<MinecraftVersion> manifestVersions;

    private final ToggleGroup loaderGroup = new ToggleGroup();
    // Р§РёРїС‹ РІ FlowPane: РїСЂРё РЅРµС…РІР°С‚РєРµ С€РёСЂРёРЅС‹ РїРµСЂРµРЅРѕСЃСЏС‚СЃСЏ РЅР° СЃР»РµРґСѓСЋС‰СѓСЋ
    // СЃС‚СЂРѕРєСѓ, Р° РЅРµ РѕР±СЂРµР·Р°СЋС‚СЃСЏ РґРѕ В«F...В»
    private final FlowPane loaderChips = new FlowPane(6, 6);
    private final ToggleGroup categoryGroup = new ToggleGroup();
    private final FlowPane categoryChips = new FlowPane(6, 6);
    private final ListView<MinecraftVersion> versionList = new ListView<>();
    private final ComboBox<ModLoaderVersion> loaderCombo = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final ProgressIndicator loaderProgress = new ProgressIndicator();
    private final Label loaderStatusLabel = new Label();
    private final Button createButton = new Button();

    private final VBox loaderChipsBox;
    private final VBox categoryBox;
    private final VBox versionListBox;
    private final VBox loaderBox;

    /** РџРѕСЃР»РµРґРЅРёР№ Р·Р°РїСЂРѕС€РµРЅРЅС‹Р№ СЃРїРёСЃРѕРє РІРµСЂСЃРёР№ Р·Р°РіСЂСѓР·С‡РёРєР° (Р·Р°С‰РёС‚Р° РѕС‚ РґСѓР±Р»РµР№). */
    private ModLoaderType fetchedLoader;
    private String fetchedMcId;

    /**
     * РџРѕР»РЅС‹Р№ СЂРµР¶РёРј РІС‹Р±РѕСЂР°: С‡РёРїС‹ Р·Р°РіСЂСѓР·С‡РёРєРѕРІ, С‡РёРїС‹ С‚РёРїРѕРІ РІРµСЂСЃРёР№, СЃРїРёСЃРѕРє
     * РІРµСЂСЃРёР№ Рё СЃРїРёСЃРѕРє РІРµСЂСЃРёР№ Р·Р°РіСЂСѓР·С‡РёРєР°.
     */
    public NewInstanceDialog(Stage owner,
                             ModLoaderRegistry registry,
                             List<MinecraftVersion> manifestVersions,
                             ModLoaderType initialLoader) {
        this.registry = registry;
        this.manifestVersions = manifestVersions;

        // Transparent stage: РѕРєРЅРѕ СЂРёСЃСѓРµС‚СЃСЏ СЃР°РјРѕ (overlay + РєР°СЂС‚РѕС‡РєР°),
        // В«backdrop-filterВ» СЌРјСѓР»РёСЂСѓРµС‚СЃСЏ СЂР°Р·РјС‹С‚С‹Рј СЃРЅР°РїС€РѕС‚РѕРј РіР»Р°РІРЅРѕРіРѕ РѕРєРЅР°
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
        MinecraftVersion shownVersion =
                (manifestVersions != null && !manifestVersions.isEmpty()
                        ? manifestVersions.get(0) : null);
        setTitle(Lang.tr("new.title")
                + (shownVersion != null ? ": " + shownVersion.id() : ""));

        // -- 0. РРјСЏ РёРЅСЃС‚Р°РЅСЃР° (РЅРµРѕР±СЏР·Р°С‚РµР»СЊРЅРѕ вЂ” РїСЂРё РїСѓСЃС‚РѕРј РїРѕРґСЃС‚Р°РІРёС‚СЃСЏ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё) --
        Label nameTitle = new Label(Lang.tr("new.name"));
        nameTitle.getStyleClass().add("section-title");
        nameField.setPromptText(Lang.tr("new.name.prompt"));
        nameField.getStyleClass().add("modal-input");
        nameField.setMaxWidth(Double.MAX_VALUE);
        VBox nameBox = new VBox(4, nameTitle, nameField);

        // -- 1. Р§РёРїС‹ Р·Р°РіСЂСѓР·С‡РёРєРѕРІ --
        Label loaderTitle = new Label(Lang.tr("new.loader"));
        loaderTitle.getStyleClass().add("section-title");
        List<ModLoaderType> loaders = new ArrayList<>();
        loaders.add(ModLoaderType.VANILLA);
        for (ModLoaderType type : ModLoaderType.values()) {
            if (type != ModLoaderType.VANILLA
                    && registry.get(type).isPresent()) {
                loaders.add(type);
            }
        }
        for (ModLoaderType type : loaders) {
            ToggleButton chip = new ToggleButton(type.displayName());
            chip.getStyleClass().add("modal-chip");
            chip.setGraphic(chipIcon(type));
            chip.setToggleGroup(loaderGroup);
            chip.setUserData(type);
            chip.setOnAction(e -> onLoaderChanged());
            loaderChips.getChildren().add(chip);
            if (type == ModLoaderType.VANILLA) {
                chip.setSelected(true);
                chip.getStyleClass().add("modal-chip-active");
            }
        }
        loaderGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            if (val == null && old != null) {
                old.setSelected(true);
                return;
            }
            updateChipStyles(loaderChips);
        });
        loaderChipsBox = new VBox(4, loaderTitle, loaderChips);

        // -- 2. Р§РёРїС‹ С‚РёРїРѕРІ РІРµСЂСЃРёР№ (С‚РѕР»СЊРєРѕ РІР°РЅРёР»Р»Р°) --
        Label categoryTitle = new Label(Lang.tr("new.category"));
        categoryTitle.getStyleClass().add("section-title");
        for (VersionType type : List.of(VersionType.RELEASE,
                VersionType.SNAPSHOT, VersionType.BETA,
                VersionType.ALPHA)) {
            ToggleButton chip = new ToggleButton(categoryLabel(type));
            chip.getStyleClass().add("modal-chip");
            chip.setToggleGroup(categoryGroup);
            chip.setUserData(type);
            chip.setOnAction(e -> refreshVersionList());
            categoryChips.getChildren().add(chip);
            if (type == VersionType.RELEASE) {
                chip.setSelected(true);
                chip.getStyleClass().add("modal-chip-active");
            }
        }
        categoryGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            if (val == null && old != null) {
                old.setSelected(true);
                return;
            }
            updateChipStyles(categoryChips);
        });
        categoryBox = new VBox(4, categoryTitle, categoryChips);

        // -- 3. РЎРїРёСЃРѕРє РІРµСЂСЃРёР№ --
        Label versionTitle = new Label(Lang.tr("new.version"));
        versionTitle.getStyleClass().add("section-title");
        versionList.getStyleClass().add("profile-list");
        versionList.setPrefHeight(230);
        versionList.setPlaceholder(new Label(Lang.tr("new.empty")));
        versionList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MinecraftVersion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label id = new Label(item.id());
                    id.getStyleClass().add("profile-name");
                    Label date = new Label(item.releaseDate().orElse("\u2014"));
                    date.getStyleClass().add("profile-summary");
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    HBox cell = new HBox(8, id, spacer, date);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(cell);
                }
            }
        });
        versionList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> onInputsChanged());
        versionListBox = new VBox(4, versionTitle, versionList);

        // -- 4. Р’РµСЂСЃРёСЏ Р·Р°РіСЂСѓР·С‡РёРєР° (С‚РѕР»СЊРєРѕ РґР»СЏ РјРѕРґРѕРІС‹С…) --
        Label loaderVersionTitle = new Label(Lang.tr("new.loaderversion"));
        loaderVersionTitle.getStyleClass().add("section-title");
        loaderCombo.setMaxWidth(Double.MAX_VALUE);
        loaderCombo.setDisable(true);
        loaderCombo.getStyleClass().add("search-field");
        loaderCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ModLoaderVersion v) {
                if (v == null) return "";
                return v.loaderVersion()
                        + (v.stable() ? "" : Lang.tr("new.beta"));
            }

            @Override
            public ModLoaderVersion fromString(String string) {
                return null;
            }
        });
        loaderProgress.setMaxSize(18, 18);
        loaderProgress.setVisible(false);
        loaderStatusLabel.getStyleClass().add("quick-select-label");
        HBox loaderStatusRow = new HBox(6, loaderProgress, loaderStatusLabel);
        loaderStatusRow.setAlignment(Pos.CENTER_LEFT);
        loaderBox = new VBox(4, loaderVersionTitle, loaderCombo,
                loaderStatusRow);

        // -- РљРЅРѕРїРєРё --
        createButton.getStyleClass().add("modal-button-primary");
        createButton.setDefaultButton(true);
        createButton.setText(Lang.tr("button.create"));
        createButton.setOnAction(e -> {
            ModLoaderType type = selectedLoader();
            MinecraftVersion mc = effectiveVersion();
            ModLoaderVersion loaderVersion = type == ModLoaderType.VANILLA
                    ? null : loaderCombo.getValue();
            if (mc == null || (type != ModLoaderType.VANILLA
                    && !isConsistent(type, mc, loaderVersion))) {
                // РљР»РёРє РІ РјРѕРјРµРЅС‚ РїРµСЂРµР·Р°РіСЂСѓР·РєРё СЃРїРёСЃРєР° РІРµСЂСЃРёР№ Р·Р°РіСЂСѓР·С‡РёРєР° вЂ”
                // РЅРµ СЃРѕР·РґР°С‘Рј РїР°СЂСѓ, РєРѕС‚РѕСЂР°СЏ РЅРµ РїСЂРѕР№РґС‘С‚ РІР°Р»РёРґР°С†РёСЋ BuildRequest
                updateCreateButtonState();
                return;
            }
            result = new Result(
                    type,
                    mc,
                    loaderVersion,
                    nameField.getText().trim());
            close();
        });
        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("modal-button-ghost");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());
        HBox buttons = new HBox(8, cancelButton, createButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(16, 0, 0, 0));

        // -- РќР°С‡Р°Р»СЊРЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ: РЅРѕРІРµР№С€РёР№ СЂРµР»РёР· РїСЂРµРґРІС‹Р±СЂР°РЅ --
        refreshVersionList();
        if (versionList.getSelectionModel().getSelectedItem() == null) {
            onInputsChanged();
        }

        // -- РЁР°РїРєР° РјРѕРґР°Р»СЊРЅРѕРіРѕ РѕРєРЅР°: Р·Р°РіРѕР»РѕРІРѕРє + РєСЂСѓРіР»С‹Р№ РєСЂРµСЃС‚РёРє --
        Label headerTitle = new Label(Lang.tr("new.title"));
        headerTitle.getStyleClass().add("modal-title");
        Button closeButton = new Button("\u2715");
        closeButton.getStyleClass().add("modal-close");
        closeButton.setOnAction(e -> close());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(12, headerTitle, headerSpacer,
                closeButton);
        headerRow.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10,
                nameBox,
                loaderChipsBox,
                categoryBox,
                versionListBox,
                loaderBox,
                buttons);
        content.setPadding(new Insets(4, 0, 0, 0));
        content.setPrefWidth(480);

        VBox card = new VBox(headerRow, content);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(480);

        // -- Glassmorphism backdrop: СЂР°Р·РјС‹С‚С‹Р№ СЃРЅР°РїС€РѕС‚ РіР»Р°РІРЅРѕРіРѕ РѕРєРЅР°
        // + РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅРѕРµ Р·Р°С‚РµРјРЅРµРЅРёРµ rgba(10,12,16,0.75) --
        StackPane overlay = new StackPane();
        addBlurBackdrop(overlay, owner);
        HBox centered = new HBox(card);
        centered.setAlignment(Pos.CENTER);
        overlay.getChildren().add(centered);

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);

        // РџСЂРµРґРІС‹Р±РѕСЂ Р·Р°РіСЂСѓР·С‡РёРєР° (РЅР°РїСЂРёРјРµСЂ, РёР· В«СѓРјРЅРѕРіРѕ Р±Р°РЅРґР»Р°В») вЂ” СЃС‚СЂРѕРіРѕ
        // РїРѕСЃР»Рµ СЃР±РѕСЂРєРё РІСЃРµС… СѓР·Р»РѕРІ: onLoaderChanged() СѓРїСЂР°РІР»СЏРµС‚
        // РІРёРґРёРјРѕСЃС‚СЊСЋ С‡РёРїРѕРІ РєР°С‚РµРіРѕСЂРёР№, СЃРїРёСЃРєР° РІРµСЂСЃРёР№ Рё РІС‹РїР°РґР°СЋС‰РµРіРѕ
        // СЃРїРёСЃРєР° РІРµСЂСЃРёР№ Р·Р°РіСЂСѓР·С‡РёРєР°, СЃРѕР·РґР°РЅРЅС‹С… РІС‹С€Рµ.
        if (initialLoader != null) {
            selectLoaderChip(initialLoader);
        }
    }

    /**
     * РќР°РєР»Р°РґС‹РІР°РµС‚ РЅР° {@code overlay} С„РѕРЅРѕРІС‹Рµ СЃР»РѕРё: СЂР°Р·РјС‹С‚С‹Р№ СЃРЅР°РїС€РѕС‚
     * СЃС†РµРЅС‹ РІР»Р°РґРµР»СЊС†Р° (РёРјРёС‚Р°С†РёСЏ {@code backdrop-filter: blur(10px)})
     * Рё РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅРѕРµ Р·Р°С‚РµРјРЅРµРЅРёРµ. РџСЂРё РЅРµСѓРґР°С‡Рµ РѕСЃС‚Р°С‘С‚СЃСЏ С‚РѕР»СЊРєРѕ
     * Р·Р°С‚РµРјРЅРµРЅРёРµ.
     */
    static void addBlurBackdrop(StackPane overlay, Stage owner) {
        ImageView blurred = null;
        if (owner != null && owner.getScene() != null
                && owner.getScene().getRoot() != null
                && owner.getWidth() > 0) {
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage snap = owner.getScene().getRoot()
                        .snapshot(params, null);
                if (snap != null && snap.getWidth() > 1
                        && snap.getHeight() > 1) {
                    blurred = new ImageView(snap);
                    blurred.setFitWidth(snap.getWidth());
                    blurred.setFitHeight(snap.getHeight());
                }
            } catch (Throwable ignored) {
                blurred = null;
            }
        }
        // РЎР»РѕРё РёРґСѓС‚ РїРѕРґ РєР°СЂС‚РѕС‡РєРѕР№, РїРѕРІРµСЂС… вЂ” Р·Р°С‚РµРјРЅРµРЅРёРµ
        if (blurred != null) {
            blurred.setEffect(new GaussianBlur(10));
            overlay.getChildren().add(0, blurred);
        }
        Region dim = new Region();
        dim.setBackground(new Background(new BackgroundFill(
                Color.rgb(10, 12, 16, 0.75), CornerRadii.EMPTY,
                Insets.EMPTY)));
        overlay.getChildren().add(1, dim);
    }

    /** РРєРѕРЅРєР° Р·Р°РіСЂСѓР·С‡РёРєР° 16px РґР»СЏ С‡РёРїСЃР° (РѕС„РёС†РёР°Р»СЊРЅС‹Рµ PNG РёР· СЂРµСЃСѓСЂСЃРѕРІ). */
    private static javafx.scene.Node chipIcon(ModLoaderType type) {
        String name = switch (type) {
            case VANILLA -> "vanilla";
            case FABRIC -> "fabric";
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case QUILT -> "quilt";
        };
        var url = NewInstanceDialog.class.getResource(
                "/icons/loader/" + name + ".png");
        if (url == null) {
            return null;
        }
        ImageView icon = new ImageView(new Image(url.toExternalForm()));
        icon.setFitWidth(16);
        icon.setFitHeight(16);
        icon.setPreserveRatio(true);
        return icon;
    }

    /** Р›РѕРєР°Р»РёР·РѕРІР°РЅРЅР°СЏ РїРѕРґРїРёСЃСЊ С‡РёРїР° С‚РёРїР° РІРµСЂСЃРёРё. */
    private static String categoryLabel(VersionType type) {
        if (type == VersionType.RELEASE) {
            return Lang.tr("new.type.release");
        }
        if (type == VersionType.SNAPSHOT) {
            return Lang.tr("new.type.snapshot");
        }
        if (type == VersionType.BETA) {
            return Lang.tr("new.type.oldbeta");
        }
        return Lang.tr("new.type.oldalpha");
    }

    /**
     * РџСЂРѕРіСЂР°РјРјРЅР°СЏ РїСЂРµРґРІС‹Р±РѕСЂРєР° С‡РёРїР° Р·Р°РіСЂСѓР·С‡РёРєР°: СЃСЂР°Р±Р°С‚С‹РІР°РµС‚ РєР°Рє РѕР±С‹С‡РЅС‹Р№
     * РєР»РёРє вЂ” СЃРєСЂС‹РІР°РµС‚ С‡РёРїС‹ С‚РёРїРѕРІ РІРµСЂСЃРёР№ Рё РіСЂСѓР·РёС‚ РІРµСЂСЃРёРё Р·Р°РіСЂСѓР·С‡РёРєР°.
     */
    private void selectLoaderChip(ModLoaderType type) {
        for (var node : loaderChips.getChildren()) {
            if (node instanceof ToggleButton chip
                    && chip.getUserData() == type) {
                chip.setSelected(true);
                chip.getStyleClass().add("modal-chip-active");
                break;
            }
        }
        onLoaderChanged();
    }

    /** РўРµРєСѓС‰РµРµ РІС‹Р±СЂР°РЅРЅРѕРµ СЃРµРјРµР№СЃС‚РІРѕ Р·Р°РіСЂСѓР·С‡РёРєРѕРІ (РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ Vanilla). */
    private ModLoaderType selectedLoader() {
        var selected = loaderGroup.getSelectedToggle();
        if (selected instanceof ToggleButton btn
                && btn.getUserData() instanceof ModLoaderType type) {
            return type;
        }
        return ModLoaderType.VANILLA;
    }

    /** Р’РµСЂСЃРёСЏ РњРљ СЃРѕР·РґР°РІР°РµРјРѕРіРѕ РёРЅСЃС‚Р°РЅСЃР°. */
    private MinecraftVersion effectiveVersion() {
        return versionList.getSelectionModel().getSelectedItem();
    }

    /**
     * РџРµСЂРµРєР»СЋС‡РµРЅРёРµ Р·Р°РіСЂСѓР·С‡РёРєР°: РјРѕРґРѕРІС‹Рµ СЂР°Р±РѕС‚Р°СЋС‚ С‚РѕР»СЊРєРѕ РЅР° СЂРµР»РёР·Р°С… (С‡РёРїС‹ С‚РёРїРѕРІ
     * РІРµСЂСЃРёР№ СЃРєСЂС‹РІР°СЋС‚СЃСЏ Рё РїСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ РІС‹Р±РёСЂР°РµС‚СЃСЏ Release); РІР°РЅРёР»Р»Р° РїСЂРµРґР»Р°РіР°РµС‚
     * РІСЃРµ С‚РёРїС‹ РІРµСЂСЃРёР№.
     */
    private void onLoaderChanged() {
        boolean modded = selectedLoader() != ModLoaderType.VANILLA;
        setVisibleManaged(categoryBox, !modded);
        if (modded) {
            for (var node : categoryChips.getChildren()) {
                if (node instanceof ToggleButton btn
                        && btn.getUserData() == VersionType.RELEASE) {
                    btn.setSelected(true);
                    break;
                }
            }
        }
        refreshVersionList();
        onInputsChanged();
    }

    private VersionType activeCategory() {
        for (var node : categoryChips.getChildren()) {
            if (node instanceof ToggleButton btn && btn.isSelected()
                    && btn.getUserData() instanceof VersionType type) {
                return type;
            }
        }
        return VersionType.RELEASE;
    }

    /**
     * РџРµСЂРµСЃС‚СЂР°РёРІР°РµС‚ СЃРїРёСЃРѕРє РІРµСЂСЃРёР№ РїРѕРґ Р°РєС‚РёРІРЅС‹Рµ Р·Р°РіСЂСѓР·С‡РёРє + РєР°С‚РµРіРѕСЂРёСЋ,
     * СЃРѕС…СЂР°РЅСЏСЏ С‚РµРєСѓС‰РёР№ РІС‹Р±РѕСЂ, РµСЃР»Рё РѕРЅ РїРѕРґС…РѕРґРёС‚, Рё РІС‹Р±РёСЂР°СЏ РЅРѕРІРµР№С€СѓСЋ РІРµСЂСЃРёСЋ
     * РІ РїСЂРѕС‚РёРІРЅРѕРј СЃР»СѓС‡Р°Рµ.
     */
    private void refreshVersionList() {
        boolean modded = selectedLoader() != ModLoaderType.VANILLA;
        VersionType category =
                modded ? VersionType.RELEASE : activeCategory();
        List<MinecraftVersion> filtered = new ArrayList<>();
        for (MinecraftVersion v : manifestVersions) {
            if (v.type() == category) {
                filtered.add(v);
            }
        }
        MinecraftVersion previous =
                versionList.getSelectionModel().getSelectedItem();
        versionList.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty()) {
            return;
        }
        if (previous != null && previous.type() == category) {
            for (MinecraftVersion v : filtered) {
                if (v.id().equals(previous.id())) {
                    versionList.getSelectionModel().select(v);
                    return;
                }
            }
        }
        // РЎРЅР°С‡Р°Р»Р° РЅРѕРІС‹Рµ (РјР°РЅРёС„РµСЃС‚ СѓР¶Рµ РѕС‚СЃРѕСЂС‚РёСЂРѕРІР°РЅ РѕС‚ РЅРѕРІС‹С… Рє СЃС‚Р°СЂС‹Рј)
        versionList.getSelectionModel().selectFirst();
    }

    /**
     * Р РµР°РєС†РёСЏ РЅР° СЃРјРµРЅСѓ Р·Р°РіСЂСѓР·С‡РёРєР°/РІРµСЂСЃРёРё: СЃС‚СЂРѕРєР° РІРµСЂСЃРёРё Р·Р°РіСЂСѓР·С‡РёРєР°
     * РїРѕРєР°Р·С‹РІР°РµС‚СЃСЏ С‚РѕР»СЊРєРѕ РґР»СЏ РјРѕРґРѕРІС‹С…, РёС… РІРµСЂСЃРёРё РїРѕРґРіСЂСѓР¶Р°СЋС‚СЃСЏ Р°СЃРёРЅС…СЂРѕРЅРЅРѕ
     * (СЃ РґРµРґСѓРїР»РёРєР°С†РёРµР№ РїРѕ РїР°СЂРµ Р·Р°РіСЂСѓР·С‡РёРє + РІРµСЂСЃРёСЏ), РєРЅРѕРїРєР° СЃРѕР·РґР°РЅРёСЏ
     * РґРµСЂР¶РёС‚СЃСЏ РІ Р°РєС‚СѓР°Р»СЊРЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё.
     */
    private void onInputsChanged() {
        ModLoaderType loader = selectedLoader();
        boolean modded = loader != ModLoaderType.VANILLA;
        MinecraftVersion mc = effectiveVersion();

        setVisibleManaged(loaderBox, modded && mc != null);
        loaderCombo.setDisable(true);
        updateCreateButtonState();

        if (!modded || mc == null) {
            loaderStatusLabel.setText("");
            loaderCombo.getItems().clear();
            loaderCombo.setValue(null);
            fetchedLoader = null;
            fetchedMcId = null;
            return;
        }

        // РўРѕС‚ Р¶Рµ Р·Р°РіСЂСѓР·С‡РёРє + РІРµСЂСЃРёСЏ: СЌР»РµРјРµРЅС‚С‹ СѓР¶Рµ Р·Р°РіСЂСѓР¶РµРЅС‹ РёР»Рё Р·Р°РїСЂРѕСЃ
        // РІ РїСѓС‚Рё вЂ” РїРѕРІС‚РѕСЂРЅС‹Р№ Р·Р°РїСЂРѕСЃ РЅРµ РЅСѓР¶РµРЅ
        if (loader == fetchedLoader && mc.id().equals(fetchedMcId)) {
            loaderCombo.setDisable(loaderCombo.getItems().isEmpty());
            return;
        }

        // Р—Р°РіСЂСѓР·С‡РёРє РёР»Рё РІРµСЂСЃРёСЏ РёРіСЂС‹ СЃРјРµРЅРёР»РёСЃСЊ: РїСЂРµР¶РЅРёР№ СЃРїРёСЃРѕРє РІРµСЂСЃРёР№
        // Р·Р°РіСЂСѓР·С‡РёРєР° РЅРµ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓРµС‚ РЅРѕРІРѕР№ РїР°СЂРµ (MC, Р·Р°РіСЂСѓР·С‡РёРє) вЂ”
        // РѕС‡РёСЃС‚РєР° РЅРµ РґР°С‘С‚ СЃРѕР·РґР°С‚СЊ СЃР±РѕСЂРєСѓ СЃ СѓРІРѕРґРёРІС€РёРјСЃСЏ Р·РЅР°С‡РµРЅРёРµРј
        loaderCombo.getItems().clear();
        loaderCombo.setValue(null);

        var regEntry = registry.get(loader).orElse(null);
        if (regEntry == null) {
            loaderStatusLabel.setText(Lang.tr("new.unavailable",
                    loader.displayName()));
            return;
        }

        fetchedLoader = loader;
        fetchedMcId = mc.id();
        loaderProgress.setVisible(true);
        loaderStatusLabel.setText(Lang.tr("new.loading",
                loader.displayName(), mc.id()));

        Thread fetch = new Thread(() -> {
            List<ModLoaderVersion> versions;
            try {
                versions = regEntry.provider().fetchVersions(mc.id());
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (selectedLoader() == loader && isCurrent(mc)) {
                        loaderProgress.setVisible(false);
                        loaderStatusLabel.setText(Lang.tr("new.loadfailed",
                                ex.getMessage()));
                        fetchedLoader = null;
                        fetchedMcId = null;
                        loaderCombo.getItems().clear();
                        loaderCombo.setValue(null);
                        updateCreateButtonState();
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (selectedLoader() != loader || !isCurrent(mc)) {
                    return; // РІС‹Р±РѕСЂ С‚РµРј РІСЂРµРјРµРЅРµРј РёР·РјРµРЅРёР»СЃСЏ
                }
                loaderProgress.setVisible(false);
                if (versions.isEmpty()) {
                    loaderStatusLabel.setText(Lang.tr("new.none",
                            loader.displayName(), mc.id()));
                } else {
                    loaderCombo.getItems().setAll(versions);
                    loaderCombo.getSelectionModel().selectFirst();
                    loaderCombo.setDisable(false);
                    loaderStatusLabel.setText(Lang.tr("new.count",
                            versions.size(), mc.id()));
                }
                updateCreateButtonState();
            });
        }, "loader-versions-fetch");
        fetch.setDaemon(true);
        fetch.start();
    }

    /** True, РµСЃР»Рё {@code mc} вЂ” РІРµСЂСЃРёСЏ, РєРѕС‚РѕСЂР°СЏ Р±СѓРґРµС‚ СЃРѕР·РґР°РЅР° СЃРµР№С‡Р°СЃ. */
    private boolean isCurrent(MinecraftVersion mc) {
        MinecraftVersion current = effectiveVersion();
        return current != null && current.id().equals(mc.id());
    }

    private void updateCreateButtonState() {
        MinecraftVersion mc = effectiveVersion();
        ModLoaderType loader = selectedLoader();
        createButton.setDisable(mc == null
                || (loader != ModLoaderType.VANILLA
                        && !isConsistent(loader, mc,
                                loaderCombo.getValue())));
    }

    /**
     * Р’РµСЂСЃРёСЏ Р·Р°РіСЂСѓР·С‡РёРєР° РѕС‚РЅРѕСЃРёС‚СЃСЏ Рє С‚РµРєСѓС‰РµР№ РїР°СЂРµ (MC, Р·Р°РіСЂСѓР·С‡РёРє):
     * Р·Р°С‰РёС‚Р° РѕС‚ РіРѕРЅРѕРє Рё РѕС‚ РІС‹Р±РѕСЂР° РёР· СѓСЃС‚Р°СЂРµРІС€РµРіРѕ СЃРїРёСЃРєР°.
     */
    private static boolean isConsistent(ModLoaderType loader,
                                        MinecraftVersion mc,
                                        ModLoaderVersion value) {
        return value != null && loader != ModLoaderType.VANILLA
                && value.loaderType() == loader
                && mc != null && mc.id().equals(value.minecraftVersion());
    }

    private static void updateChipStyles(javafx.scene.layout.Pane chips) {
        for (var node : chips.getChildren()) {
            if (node instanceof ToggleButton btn) {
                if (btn.isSelected()) {
                    btn.getStyleClass().add("modal-chip-active");
                } else {
                    btn.getStyleClass().remove("modal-chip-active");
                }
            }
        }
    }

    private static void setVisibleManaged(javafx.scene.Node node,
                                          boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /**
     * РџРѕРєР°Р·С‹РІР°РµС‚ РґРёР°Р»РѕРі Рё РІРѕР·РІСЂР°С‰Р°РµС‚ РїР°СЂР°РјРµС‚СЂС‹ СЃРѕР·РґР°РЅРёСЏ, Р»РёР±Рѕ
     * {@code null}, РµСЃР»Рё РѕРЅ Р±С‹Р» РѕС‚РјРµРЅС‘РЅ.
     */
    public static Result show(Stage owner,
                              ModLoaderRegistry registry,
                              List<MinecraftVersion> manifestVersions) {
        return show(owner, registry, manifestVersions, null);
    }

    /**
     * РљР°Рє {@link #show(Stage, ModLoaderRegistry, List)}, РЅРѕ СЃ
     * РїСЂРµРґРІС‹Р±СЂР°РЅРЅС‹Рј Р·Р°РіСЂСѓР·С‡РёРєРѕРј (РёР· В«СѓРјРЅРѕРіРѕ Р±Р°РЅРґР»Р°В»).
     */
    public static Result show(Stage owner,
                              ModLoaderRegistry registry,
                              List<MinecraftVersion> manifestVersions,
                              ModLoaderType initialLoader) {
        NewInstanceDialog dialog = new NewInstanceDialog(
                owner, registry, manifestVersions, initialLoader);
        dialog.showAndWait();
        return dialog.result;
    }
}
