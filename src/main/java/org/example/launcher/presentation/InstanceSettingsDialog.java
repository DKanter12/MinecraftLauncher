package org.example.launcher.presentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.i18n.Lang;

/**
 * Модальная панель настроек игрового инстанса (Prism/GDLauncher-стиль):
 * вкладки «Общие», «Моды», «Java/Память», «Снимки/Бэкапы». В нижнем углу
 * — кнопка «Удалить сборку» с подтверждением; сохранение возвращает
 * {@link Result}. Длинные системные пути в сетке библиотеки больше не
 * показываются — вся настройка живёт здесь.
 */
public final class InstanceSettingsDialog extends Stage {

    /** Отредактированные значения: имя, память (МБ) и JVM-аргументы. */
    public record Result(String name, int memoryMb, List<String> jvmArgs) {
    }

    private final ModdedProfile profile;
    private final Consumer<ModdedProfile> onDeleteRequest;
    private final Runnable onChangeIcon;

    private final TextField nameField = new TextField();
    private final TextField memoryField = new TextField();
    private final MemoryChips memoryChips = new MemoryChips(memoryField);
    private final TextField jvmField = new TextField();

    private Result result;
    private boolean deleteRequested = false;

    private InstanceSettingsDialog(Stage owner, ModdedProfile profile,
                                   Path gameDir,
                                   Consumer<ModdedProfile> onDeleteRequest,
                                   Runnable onChangeIcon) {
        this.profile = profile;
        this.onDeleteRequest = onDeleteRequest;
        this.onChangeIcon = onChangeIcon;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        setTitle(Lang.tr("settings.instance.title", profile.name()));

        // --- Вкладки ---
        ToggleGroup tabGroup = new ToggleGroup();
        ToggleButton generalTab = tabButton(tabGroup, "settings.tabs.general");
        ToggleButton modsTab = tabButton(tabGroup, "settings.tabs.mods");
        ToggleButton javaTab = tabButton(tabGroup, "settings.tabs.java");
        generalTab.setSelected(true);

        VBox generalPane = buildGeneralPane();
        VBox modsPane = buildModsPane(gameDir);
        VBox javaPane = buildJavaPane();

        HBox tabRow = new HBox(6, generalTab, modsTab, javaTab);
        tabRow.getStyleClass().add("filter-row");

        StackPane tabContent = new StackPane(generalPane, modsPane,
                javaPane);

        // Синхронизация видимости вкладок
        List<ToggleButton> tabs = List.of(generalTab, modsTab, javaTab);
        List<VBox> panes = List.of(generalPane, modsPane, javaPane);
        for (int i = 0; i < tabs.size(); i++) {
            VBox pane = panes.get(i);
            ToggleButton tab = tabs.get(i);
            pane.setVisible(i == 0);
            pane.setManaged(i == 0);
            tab.setOnAction(e -> {
                if (!tab.isSelected()) {
                    tab.setSelected(true);
                    return;
                }
                for (int j = 0; j < panes.size(); j++) {
                    boolean show = panes.get(j) == pane;
                    panes.get(j).setVisible(show);
                    panes.get(j).setManaged(show);
                }
            });
        }

        // --- Нижняя строка: удаление слева, отмена/сохранение справа ---
        Button delete = new Button(Lang.tr("settings.instance.delete"));
        delete.getStyleClass().add("card-danger-button");
        delete.setOnAction(e -> confirmDelete(profile));

        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());

        Button saveButton = new Button(Lang.tr("button.save"));
        saveButton.getStyleClass().add("install-close-button");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(e -> {
            result = new Result(nameField.getText().trim(),
                    memoryChips.parse(),
                    parseJvmArgs(jvmField.getText()));
            close();
        });

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, delete, bottomSpacer, cancelButton,
                saveButton);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(12, 0, 0, 0));

        nameField.textProperty().addListener(
                (obs, old, val) -> validateSave(saveButton));
        nameField.setText(profile.name());
        memoryField.textProperty().addListener(
                (obs, old, val) -> validateSave(saveButton));

        VBox content = new VBox(10, tabRow, tabContent, bottom);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
        validateSave(saveButton);
    }

    /**
     * Открывает модальное окно настроек. Возвращает изменения или
     * {@code null} (отмена либо сработавшее подтверждение удаления —
     * удаление выполняется через колбэк {@code onDeleteRequest}).
     */
    public static Result show(Stage owner,
                              ModdedProfile profile,
                              Path gameDir,
                              Consumer<ModdedProfile> onDeleteRequest,
                              Runnable onChangeIcon) {
        InstanceSettingsDialog dialog = new InstanceSettingsDialog(
                owner, profile, gameDir, onDeleteRequest, onChangeIcon);
        dialog.showAndWait();
        return dialog.deleteRequested ? null : dialog.result;
    }

    // ------------------------------------------------------------------
    //  Вкладки
    // ------------------------------------------------------------------

    private VBox buildGeneralPane() {
        Label nameLabel = new Label(Lang.tr("edit.name"));
        nameLabel.getStyleClass().add("section-title");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.getStyleClass().add("search-field");

        Label info = new Label(profile.summary() + " \u00b7 "
                + profile.versionId());
        info.getStyleClass().add("settings-value");
        info.setWrapText(true);

        Button changeIcon = new Button(Lang.tr("instance.icon.change"));
        changeIcon.getStyleClass().add("install-close-button");
        changeIcon.setOnAction(e -> {
            if (onChangeIcon != null) {
                onChangeIcon.run();
            }
        });

        VBox pane = new VBox(8, nameLabel, nameField, changeIcon, info);
        pane.getStyleClass().add("settings-group");
        return pane;
    }

    private VBox buildModsPane(Path gameDir) {
        Label title = new Label(Lang.tr("settings.mods.header"));
        title.getStyleClass().add("section-title");

        List<String> files = listMods(gameDir);
        Label summary = new Label(Lang.tr("settings.mods.count", files.size()));
        summary.getStyleClass().add("settings-value");

        VBox rows = new VBox(3);
        if (files.isEmpty()) {
            Label empty = new Label(Lang.tr("settings.mods.empty"));
            empty.getStyleClass().add("settings-value");
            rows.getChildren().add(empty);
        } else {
            for (String name : files) {
                Label row = new Label(name);
                row.getStyleClass().add("install-file");
                rows.getChildren().add(row);
            }
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(180);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox pane = new VBox(8, title, summary, scroll);
        pane.getStyleClass().add("settings-group");
        return pane;
    }

    private VBox buildJavaPane() {
        Label memoryLabel = new Label(Lang.tr("edit.memory"));
        memoryLabel.getStyleClass().add("section-title");
        memoryChips.sync(profile.memoryMb());
        memoryField.setMaxWidth(Double.MAX_VALUE);
        memoryField.getStyleClass().add("search-field");

        Label argsLabel = new Label(Lang.tr("settings.java.args"));
        argsLabel.getStyleClass().add("section-title");
        jvmField.setText(String.join(" ", profile.extraJvmArgs()));
        jvmField.setPromptText(Lang.tr("settings.java.args.prompt"));
        jvmField.setMaxWidth(Double.MAX_VALUE);
        jvmField.getStyleClass().add("search-field");

        VBox pane = new VBox(8, memoryLabel, memoryChips.flow(),
                memoryField, argsLabel, jvmField);
        pane.getStyleClass().add("settings-group");
        return pane;
    }

    // ------------------------------------------------------------------
    //  Вспомогательное
    // ------------------------------------------------------------------

    private static ToggleButton tabButton(ToggleGroup group, String key) {
        ToggleButton tab = new ToggleButton(Lang.tr(key));
        tab.getStyleClass().addAll("filter-button");
        tab.setToggleGroup(group);
        return tab;
    }

    /**
     * Удаление: делегирует колбэку onDeleteRequest (в MainView это
     * onDeleteProfile с его модальным подтверждением) и закрывает окно
     * без сохранения.
     */
    private void confirmDelete(ModdedProfile profileToConfirm) {
        deleteRequested = true;
        onDeleteRequest.accept(profileToConfirm);
        close();
    }

    private void validateSave(Button saveButton) {
        boolean valid = !nameField.getText().isBlank()
                && memoryChips.parse() >= 0;
        saveButton.setDisable(!valid);
    }

    /** Мягкий разбор JVM-аргументов: позволяем кавычки, разделяем пробелом. */
    private static List<String> parseJvmArgs(String text) {
        if (text == null) {
            return List.of();
        }
        String trimmed = text.replaceAll("\\s+", " ").trim();
        return trimmed.isEmpty() ? List.of()
                : List.of(trimmed.split(" "));
    }

    /** Список модов (jar и jar.disabled) из каталога mods. */
    private static List<String> listMods(Path gameDir) {
        List<String> names = new ArrayList<>();
        if (gameDir == null) {
            return names;
        }
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return names;
        }
        try (var stream = Files.list(mods)) {
            final int limit = 200;
            for (Path file : stream.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) {
                    names.add(name);
                }
            }
            if (names.size() > limit) {
                List<String> cut = new ArrayList<>(names.subList(0, limit));
                cut.add(Lang.tr("settings.mods.more",
                        names.size() - limit));
                return cut;
            }
            return names;
        } catch (IOException e) {
            return names;
        }
    }

    /**
     * Пресеты памяти: кнопки той же логики, что в EditProfileDialog
     * (Авто / 2 / 4 / 8 / 12 / 16 / 32 ГБ) + ручной ввод в поле.
     */
    private static final class MemoryChips {
        private final TextField field;
        private final FlowPane chips = new FlowPane();
        private final ToggleGroup group = new ToggleGroup();

        MemoryChips(TextField field) {
            this.field = field;
            chips.setHgap(8);
            chips.setVgap(8);
            add(Lang.tr("common.auto"), 0);
            add("2 " + Lang.tr("unit.gb"), 2048);
            add("4 " + Lang.tr("unit.gb"), 4096);
            add("6 " + Lang.tr("unit.gb"), 6144);
            add("8 " + Lang.tr("unit.gb"), 8192);
            add("12 " + Lang.tr("unit.gb"), 12288);
            add("16 " + Lang.tr("unit.gb"), 16384);
            add("32 " + Lang.tr("unit.gb"), 32768);
            // Ручной ввод подсвечивает совпавший пресет
            field.textProperty().addListener((obs, old, val) -> {
                int mb = parse();
                for (var node : chips.getChildren()) {
                    if (node instanceof ToggleButton chip
                            && chip.getUserData() instanceof Integer preset) {
                        chip.setSelected(preset == mb);
                    }
                }
            });
            for (var node : chips.getChildren()) {
                if (node instanceof ToggleButton chip) {
                    chip.setOnAction(e -> {
                        if (!chip.isSelected()) {
                            chip.setSelected(true);
                            return;
                        }
                        int preset = (Integer) chip.getUserData();
                        field.setText(preset == 0 ? ""
                                : String.valueOf(preset));
                    });
                }
            }
        }

        void add(String text, int megabytes) {
            ToggleButton chip = new ToggleButton(text);
            chip.getStyleClass().add("filter-button");
            chip.setMinWidth(Region.USE_PREF_SIZE);
            chip.setMaxWidth(Region.USE_PREF_SIZE);
            chip.setToggleGroup(group);
            chip.setUserData(megabytes);
            chips.getChildren().add(chip);
        }

        /** Подсвечивает пресет по заданному лимиту. */
        void sync(int currentMb) {
            field.setText(currentMb > 0 ? String.valueOf(currentMb) : "");
            int flag = currentMb > 0 ? currentMb : 0;
            for (var node : chips.getChildren()) {
                if (node instanceof ToggleButton chip
                        && chip.getUserData() instanceof Integer preset) {
                    chip.setSelected(preset == flag);
                }
            }
        }

        FlowPane flow() {
            return chips;
        }

        /**
         * Разбор поля: пустое — авто ({@code 0}); мусор/меньше 256 МБ
         * — {@code -1} (некорректно).
         */
        int parse() {
            String text = field.getText();
            if (text == null || text.isBlank()) {
                return 0;
            }
            try {
                int value = Integer.parseInt(text.trim());
                if (value == 0) {
                    return 0;
                }
                return value < 256 ? -1 : value;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }
}
