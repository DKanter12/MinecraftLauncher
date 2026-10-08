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
 * Модальный диалог редактирования игрового инстанса: отображаемое имя,
 * лимит выделенной памяти (ОЗУ) и дополнительные аргументы запуска JVM.
 * <p>
 * Переименование также переименовывает папку инстанса, чтобы она всегда
 * совпадала с именем в лаунчере (моды, сохранения и сборки переезжают вместе).
 * Версии загрузчика/Minecraft — фиксированные свойства инстанса.
 */
public class EditProfileDialog extends Stage {

    /**
     * Отредактированные значения. Лимит памяти — в мегабайтах
     * ({@code 0} означает автоматический).
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
        // Компактный ряд пресетов — 2 / 4 / 8 и т.д. без многоточия (FlowPane не даёт схлопнуться в «2...»)
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
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
        syncMemoryChips();
        updateSaveButton();
    }

    private void addMemoryChip(String text, int megabytes) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("filter-button");
        // Текст не должен схлопываться в «2...» — сохраняем естественную ширину
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

    /** Подсвечивает пресет, совпадающий с полем (для произвольного — ни один). */
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
     * Разбирает поле памяти: пустое означает автоматический режим ({@code 0}),
     * иначе мегабайты; {@code -1} при некорректном вводе.
     */
    private static int parseMemory(String text) {
        int value = parseLenient(text);
        if (value == 0) {
            return 0;
        }
        return value < 256 ? -1 : value;
    }

    /** Мягкий разбор для синхронизации чипов: пустое — 0, мусор — -1. */
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
     * Показывает диалог и возвращает отредактированные значения, либо {@code null},
     * если диалог был отменён.
     */
    public static Result show(Stage owner, ModdedProfile profile) {
        EditProfileDialog dialog = new EditProfileDialog(owner, profile);
        dialog.showAndWait();
        return dialog.result;
    }
}
