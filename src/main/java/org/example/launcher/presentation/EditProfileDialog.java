package org.example.launcher.presentation;



import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.i18n.Lang;

/**
 * РњРѕРґР°Р»СЊРЅС‹Р№ РґРёР°Р»РѕРі СЂРµРґР°РєС‚РёСЂРѕРІР°РЅРёСЏ РёРіСЂРѕРІРѕРіРѕ РёРЅСЃС‚Р°РЅСЃР°: РѕС‚РѕР±СЂР°Р¶Р°РµРјРѕРµ РёРјСЏ,
 * Р»РёРјРёС‚ РІС‹РґРµР»РµРЅРЅРѕР№ РїР°РјСЏС‚Рё (РћР—РЈ) Рё РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Рµ Р°СЂРіСѓРјРµРЅС‚С‹ Р·Р°РїСѓСЃРєР° JVM.
 * <p>
 * РџРµСЂРµРёРјРµРЅРѕРІР°РЅРёРµ С‚Р°РєР¶Рµ РїРµСЂРµРёРјРµРЅРѕРІС‹РІР°РµС‚ РїР°РїРєСѓ РёРЅСЃС‚Р°РЅСЃР°, С‡С‚РѕР±С‹ РѕРЅР° РІСЃРµРіРґР°
 * СЃРѕРІРїР°РґР°Р»Р° СЃ РёРјРµРЅРµРј РІ Р»Р°СѓРЅС‡РµСЂРµ (РјРѕРґС‹, СЃРѕС…СЂР°РЅРµРЅРёСЏ Рё СЃР±РѕСЂРєРё РїРµСЂРµРµР·Р¶Р°СЋС‚ РІРјРµСЃС‚Рµ).
 * Р’РµСЂСЃРёРё Р·Р°РіСЂСѓР·С‡РёРєР°/Minecraft вЂ” С„РёРєСЃРёСЂРѕРІР°РЅРЅС‹Рµ СЃРІРѕР№СЃС‚РІР° РёРЅСЃС‚Р°РЅСЃР°.
 */
public class EditProfileDialog extends Stage {

    /**
     * РћС‚СЂРµРґР°РєС‚РёСЂРѕРІР°РЅРЅС‹Рµ Р·РЅР°С‡РµРЅРёСЏ. Р›РёРјРёС‚ РїР°РјСЏС‚Рё вЂ” РІ РјРµРіР°Р±Р°Р№С‚Р°С…
     * ({@code 0} РѕР·РЅР°С‡Р°РµС‚ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРёР№).
     */
    public record Result(String name, int memoryMb) {
    }

    private Result result;

    private final TextField nameField = new TextField();
    private final TextField memoryField = new TextField();
    private final FlowPane memoryChips = new FlowPane();
    private final ToggleGroup memoryGroup = new ToggleGroup();
    private final Label errorLabel = new Label();
    private final Button saveButton = new Button(Lang.tr("button.save"));

    public EditProfileDialog(Stage owner, ModdedProfile profile) {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("edit.title"));

        Label infoLabel = new Label(profile.name() + " \u00b7 "
                + profile.summary() + " \u00b7 " + profile.versionId());
        infoLabel.getStyleClass().add("quick-select-label");
        infoLabel.setWrapText(true);

        Label nameLabel = new Label(Lang.tr("edit.name"));
        nameLabel.getStyleClass().add("section-title");
        nameField.setText(profile.name());
        nameField.getStyleClass().add("search-field");
        nameField.setMaxWidth(Double.MAX_VALUE);

        Label memoryLabel = new Label(Lang.tr("edit.memory"));
        memoryLabel.getStyleClass().add("section-title");
        memoryChips.setHgap(8);
        memoryChips.setVgap(8);
        // РљРѕРјРїР°РєС‚РЅС‹Р№ СЂСЏРґ РїСЂРµСЃРµС‚РѕРІ вЂ” 2 / 4 / 8 Рё С‚.Рґ. Р±РµР· РјРЅРѕРіРѕС‚РѕС‡РёСЏ (FlowPane РЅРµ РґР°С‘С‚ СЃС…Р»РѕРїРЅСѓС‚СЊСЃСЏ РІ В«2...В»)
        addMemoryChip(Lang.tr("common.auto"), 0);
        addMemoryChip("2 " + Lang.tr("unit.gb"), 2048);
        addMemoryChip("4 " + Lang.tr("unit.gb"), 4096);
        addMemoryChip("6 " + Lang.tr("unit.gb"), 6144);
        addMemoryChip("8 " + Lang.tr("unit.gb"), 8192);
        addMemoryChip("12 " + Lang.tr("unit.gb"), 12288);
        addMemoryChip("16 " + Lang.tr("unit.gb"), 16384);
        addMemoryChip("32 " + Lang.tr("unit.gb"), 32768);
        memoryField.setText(profile.memoryMb() > 0
                ? String.valueOf(profile.memoryMb()) : "");
        memoryField.setPromptText(Lang.tr("edit.memory.prompt"));
        memoryField.getStyleClass().add("search-field");
        memoryField.setMaxWidth(Double.MAX_VALUE);

        errorLabel.getStyleClass().add("account-error");
        errorLabel.setWrapText(true);

        nameField.textProperty().addListener((obs, old, val) ->
                updateSaveButton());
        memoryField.textProperty().addListener((obs, old, val) -> {
            syncMemoryChips();
            updateSaveButton();
        });

        saveButton.getStyleClass().add("install-close-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            result = new Result(nameField.getText().trim(),
                    parseMemory(memoryField.getText()));
            close();
        });

        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        HBox buttons = new HBox(8, cancelButton, saveButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6, 0, 0, 0));

        VBox content = new VBox(6,
                infoLabel, nameLabel, nameField,
                memoryLabel, memoryChips, memoryField,
                errorLabel,
                buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(440);

        Scene scene = new Scene(content);
        ThemeManager.apply(scene);
        setScene(scene);
        AppIcon.apply(this);
        syncMemoryChips();
        updateSaveButton();
    }

    private void addMemoryChip(String text, int megabytes) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("filter-button");
        // РўРµРєСЃС‚ РЅРµ РґРѕР»Р¶РµРЅ СЃС…Р»РѕРїС‹РІР°С‚СЊСЃСЏ РІ В«2...В» вЂ” СЃРѕС…СЂР°РЅСЏРµРј РµСЃС‚РµСЃС‚РІРµРЅРЅСѓСЋ С€РёСЂРёРЅСѓ
        chip.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        chip.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        chip.setToggleGroup(memoryGroup);
        chip.setUserData(megabytes);
        chip.setOnAction(e -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
            }
            memoryField.setText(
                    megabytes == 0 ? "" : String.valueOf(megabytes));
        });
        memoryChips.getChildren().add(chip);
    }

    /** РџРѕРґСЃРІРµС‡РёРІР°РµС‚ РїСЂРµСЃРµС‚, СЃРѕРІРїР°РґР°СЋС‰РёР№ СЃ РїРѕР»РµРј (РґР»СЏ РїСЂРѕРёР·РІРѕР»СЊРЅРѕРіРѕ вЂ” РЅРё РѕРґРёРЅ). */
    private void syncMemoryChips() {
        int current = parseLenient(memoryField.getText());
        for (var node : memoryChips.getChildren()) {
            if (node instanceof ToggleButton chip
                    && chip.getUserData() instanceof Integer preset) {
                chip.setSelected(current >= 0 && preset == current);
            }
        }
    }

    private void updateSaveButton() {
        if (nameField.getText().isBlank()) {
            errorLabel.setText(Lang.tr("edit.error.noname"));
            saveButton.setDisable(true);
            return;
        }
        if (parseMemory(memoryField.getText()) < 0) {
            errorLabel.setText(Lang.tr("edit.error.memory"));
            saveButton.setDisable(true);
            return;
        }
        errorLabel.setText("");
        saveButton.setDisable(false);
    }

    /**
     * Р Р°Р·Р±РёСЂР°РµС‚ РїРѕР»Рµ РїР°РјСЏС‚Рё: РїСѓСЃС‚РѕРµ РѕР·РЅР°С‡Р°РµС‚ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРёР№ СЂРµР¶РёРј ({@code 0}),
     * РёРЅР°С‡Рµ РјРµРіР°Р±Р°Р№С‚С‹; {@code -1} РїСЂРё РЅРµРєРѕСЂСЂРµРєС‚РЅРѕРј РІРІРѕРґРµ.
     */
    private static int parseMemory(String text) {
        int value = parseLenient(text);
        if (value == 0) {
            return 0;
        }
        return value < 256 ? -1 : value;
    }

    /** РњСЏРіРєРёР№ СЂР°Р·Р±РѕСЂ РґР»СЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё С‡РёРїРѕРІ: РїСѓСЃС‚РѕРµ вЂ” 0, РјСѓСЃРѕСЂ вЂ” -1. */
    private static int parseLenient(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * РџРѕРєР°Р·С‹РІР°РµС‚ РґРёР°Р»РѕРі Рё РІРѕР·РІСЂР°С‰Р°РµС‚ РѕС‚СЂРµРґР°РєС‚РёСЂРѕРІР°РЅРЅС‹Рµ Р·РЅР°С‡РµРЅРёСЏ, Р»РёР±Рѕ {@code null},
     * РµСЃР»Рё РґРёР°Р»РѕРі Р±С‹Р» РѕС‚РјРµРЅС‘РЅ.
     */
    public static Result show(Stage owner, ModdedProfile profile) {
        EditProfileDialog dialog = new EditProfileDialog(owner, profile);
        dialog.showAndWait();
        return dialog.result;
    }
}
