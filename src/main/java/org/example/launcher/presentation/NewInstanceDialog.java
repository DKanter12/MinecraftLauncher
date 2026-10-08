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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
 * Модальный диалог создания нового игрового инстанса — один простой экран,
 * всё выбирается из списков (ввод с клавиатуры не требуется):
 * <ol>
 *   <li><b>Загрузчик</b> — чипы: Vanilla, Fabric, Forge, NeoForge, Quilt.</li>
 *   <li><b>Тип версии</b> — чипы (Release, Snapshot, Old Beta, Old
 *       Alpha), предлагаются только для Vanilla — модовые загрузчики
 *       работают на релизах.</li>
 *   <li><b>Версия</b> — список подходящих версий, сначала новые
 *       (например, {@code 1.21.4}, {@code 25w14a}).</li>
 *   <li><b>Версия загрузчика</b> — для модовых загрузчиков, загружается
 *       асинхронно от провайдера загрузчика (все записи
 *       гарантированно совместимы с выбранной версией).</li>
 * </ol>
 * Можно указать дополнительные JVM-аргументы (например, {@code -Xmx4G});
 * имя инстанса вводится свободно и по умолчанию состоит из загрузчика
 * и версии Minecraft, если оставлено пустым.
 */
public class NewInstanceDialog extends Stage {

    /**
     * Параметры созданного инстанса.
     *
     * @param type      ванилла или семейство модового загрузчика
     * @param mcVersion целевая версия Minecraft
     * @param loader    выбранная версия загрузчика ({@code null}
     *                  для ваниллы)
     * @param name      отображаемое имя инстанса (пустое означает
     *                  автоматическое имя «Загрузчик МК»)
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
    private final HBox loaderChips = new HBox(6);
    private final ToggleGroup categoryGroup = new ToggleGroup();
    private final HBox categoryChips = new HBox(6);
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

    /** Последний запрошенный список версий загрузчика (защита от дублей). */
    private ModLoaderType fetchedLoader;
    private String fetchedMcId;

    /**
     * Полный режим выбора: чипы загрузчиков, чипы типов версий, список
     * версий и список версий загрузчика.
     */
    public NewInstanceDialog(Stage owner,
                             ModLoaderRegistry registry,
                             List<MinecraftVersion> manifestVersions) {
        this.registry = registry;
        this.manifestVersions = manifestVersions;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        MinecraftVersion shownVersion =
                (manifestVersions != null && !manifestVersions.isEmpty()
                        ? manifestVersions.get(0) : null);
        setTitle(Lang.tr("new.title")
                + (shownVersion != null ? ": " + shownVersion.id() : ""));

        // -- 0. Имя инстанса (необязательно — при пустом подставится автоматически) --
        Label nameTitle = new Label(Lang.tr("new.name"));
        nameTitle.getStyleClass().add("section-title");
        nameField.setPromptText(Lang.tr("new.name.prompt"));
        nameField.getStyleClass().add("search-field");
        nameField.setMaxWidth(Double.MAX_VALUE);
        VBox nameBox = new VBox(4, nameTitle, nameField);

        // -- 1. Чипы загрузчиков --
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
            chip.getStyleClass().add("filter-button");
            chip.setToggleGroup(loaderGroup);
            chip.setUserData(type);
            chip.setOnAction(e -> onLoaderChanged());
            loaderChips.getChildren().add(chip);
            if (type == ModLoaderType.VANILLA) {
                chip.setSelected(true);
                chip.getStyleClass().add("filter-button-active");
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

        // -- 2. Чипы типов версий (только ванилла) --
        Label categoryTitle = new Label(Lang.tr("new.category"));
        categoryTitle.getStyleClass().add("section-title");
        for (VersionType type : List.of(VersionType.RELEASE,
                VersionType.SNAPSHOT, VersionType.BETA,
                VersionType.ALPHA)) {
            ToggleButton chip = new ToggleButton(categoryLabel(type));
            chip.getStyleClass().add("filter-button");
            chip.setToggleGroup(categoryGroup);
            chip.setUserData(type);
            chip.setOnAction(e -> refreshVersionList());
            categoryChips.getChildren().add(chip);
            if (type == VersionType.RELEASE) {
                chip.setSelected(true);
                chip.getStyleClass().add("filter-button-active");
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

        // -- 3. Список версий --
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

        // -- 4. Версия загрузчика (только для модовых) --
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

        // -- Кнопки --
        createButton.getStyleClass().add("install-close-button");
        createButton.setDefaultButton(true);
        createButton.setText(Lang.tr("button.create"));
        createButton.setOnAction(e -> {
            ModLoaderType type = selectedLoader();
            result = new Result(
                    type,
                    effectiveVersion(),
                    type == ModLoaderType.VANILLA ? null
                            : loaderCombo.getValue(),
                    nameField.getText().trim());
            close();
        });
        Button cancelButton = new Button(Lang.tr("button.cancel"));
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());
        HBox buttons = new HBox(8, cancelButton, createButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        // -- Начальное состояние: новейший релиз предвыбран --
        refreshVersionList();
        if (versionList.getSelectionModel().getSelectedItem() == null) {
            onInputsChanged();
        }

        VBox content = new VBox(6,
                nameBox,
                loaderChipsBox,
                categoryBox,
                versionListBox,
                loaderBox,
                buttons);
        content.setPadding(new Insets(18));
        content.setPrefWidth(440);

        Scene scene = new Scene(content);
        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        setScene(scene);
    }

    /** Локализованная подпись чипа типа версии. */
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

    /** Текущее выбранное семейство загрузчиков (по умолчанию Vanilla). */
    private ModLoaderType selectedLoader() {
        var selected = loaderGroup.getSelectedToggle();
        if (selected instanceof ToggleButton btn
                && btn.getUserData() instanceof ModLoaderType type) {
            return type;
        }
        return ModLoaderType.VANILLA;
    }

    /** Версия МК создаваемого инстанса. */
    private MinecraftVersion effectiveVersion() {
        return versionList.getSelectionModel().getSelectedItem();
    }

    /**
     * Переключение загрузчика: модовые работают только на релизах (чипы типов
     * версий скрываются и принудительно выбирается Release); ванилла предлагает
     * все типы версий.
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
     * Перестраивает список версий под активные загрузчик + категорию,
     * сохраняя текущий выбор, если он подходит, и выбирая новейшую версию
     * в противном случае.
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
        // Сначала новые (манифест уже отсортирован от новых к старым)
        versionList.getSelectionModel().selectFirst();
    }

    /**
     * Реакция на смену загрузчика/версии: строка версии загрузчика
     * показывается только для модовых, их версии подгружаются асинхронно
     * (с дедупликацией по паре загрузчик + версия), кнопка создания
     * держится в актуальном состоянии.
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
            fetchedLoader = null;
            fetchedMcId = null;
            return;
        }

        // Тот же загрузчик + версия: элементы уже загружены или запрос
        // в пути — повторный запрос не нужен
        if (loader == fetchedLoader && mc.id().equals(fetchedMcId)) {
            loaderCombo.setDisable(loaderCombo.getItems().isEmpty());
            return;
        }

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
                        updateCreateButtonState();
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (selectedLoader() != loader || !isCurrent(mc)) {
                    return; // выбор тем временем изменился
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

    /** True, если {@code mc} — версия, которая будет создана сейчас. */
    private boolean isCurrent(MinecraftVersion mc) {
        MinecraftVersion current = effectiveVersion();
        return current != null && current.id().equals(mc.id());
    }

    private void updateCreateButtonState() {
        MinecraftVersion mc = effectiveVersion();
        boolean modded = selectedLoader() != ModLoaderType.VANILLA;
        createButton.setDisable(mc == null
                || (modded && loaderCombo.getValue() == null));
    }

    private static void updateChipStyles(HBox chips) {
        for (var node : chips.getChildren()) {
            if (node instanceof ToggleButton btn) {
                if (btn.isSelected()) {
                    btn.getStyleClass().add("filter-button-active");
                } else {
                    btn.getStyleClass().remove("filter-button-active");
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
     * Показывает диалог и возвращает параметры создания, либо
     * {@code null}, если он был отменён.
     */
    public static Result show(Stage owner,
                              ModLoaderRegistry registry,
                              List<MinecraftVersion> manifestVersions) {
        NewInstanceDialog dialog = new NewInstanceDialog(
                owner, registry, manifestVersions);
        dialog.showAndWait();
        return dialog.result;
    }
}
