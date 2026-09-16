package org.example.launcher.ui;

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
import javafx.scene.control.TextArea;
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

import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.service.modloader.ModLoaderRegistry;
import org.example.launcher.service.modloader.ModLoaderType;
import org.example.launcher.version.StandardVersionType;

/**
 * Modal dialog for creating a new game instance — one simple screen,
 * everything chosen from lists (no typing required):
 * <ol>
 *   <li><b>Loader</b> — chips: Vanilla, Fabric, Forge, NeoForge, Quilt.</li>
 *   <li><b>Version type</b> — chips (Release, Snapshot, Old Beta, Old
 *       Alpha), offered for Vanilla only — mod loaders run on
 *       releases.</li>
 *   <li><b>Version</b> — a list of the matching versions, newest
 *       first (e.g. {@code 1.21.4}, {@code 25w14a}).</li>
 *   <li><b>Loader version</b> — for modded loaders, fetched
 *       asynchronously from the loader's provider (all entries are
 *       guaranteed compatible with the chosen version).</li>
 * </ol>
 * Extra JVM arguments (e.g. {@code -Xmx4G}) can be given; the
 * instance name is typed in freely and defaults to the loader and
 * the Minecraft version when left empty.
 *
 * <p>There is also a <b>fixed-entry mode</b> (double-clicked version
 * browser row): loader family and Minecraft version are preset and
 * only the name, the loader version and JVM arguments are chosen.</p>
 */
public class NewInstanceDialog extends Stage {

    /**
     * The created instance's parameters.
     *
     * @param type         vanilla or the mod loader family
     * @param mcVersion    the target Minecraft version
     * @param loader       the selected loader version ({@code null}
     *                     for vanilla)
     * @param extraJvmArgs additional JVM launch parameters
     * @param name         instance display name (blank means the
     *                     automatic "Loader MC" name)
     */
    public record Result(ModLoaderType type,
                         MinecraftVersion mcVersion,
                         ModLoaderVersion loader,
                         List<String> extraJvmArgs,
                         String name) {
    }

    private Result result;

    private final ModLoaderRegistry registry;
    private final List<MinecraftVersion> manifestVersions;

    /** Fixed version (double-clicked browser entry) or null = full picker. */
    private final MinecraftVersion fixedVersion;
    /** Fixed loader family for the fixed version, or null. */
    private final ModLoaderType fixedLoader;

    private final ToggleGroup loaderGroup = new ToggleGroup();
    private final HBox loaderChips = new HBox(6);
    private final ToggleGroup categoryGroup = new ToggleGroup();
    private final HBox categoryChips = new HBox(6);
    private final ListView<MinecraftVersion> versionList = new ListView<>();
    private final ComboBox<ModLoaderVersion> loaderCombo = new ComboBox<>();
    private final TextArea jvmArgsArea = new TextArea();
    private final TextField nameField = new TextField();
    private final ProgressIndicator loaderProgress = new ProgressIndicator();
    private final Label loaderStatusLabel = new Label();
    private final Button createButton = new Button("Create");

    private final VBox loaderChipsBox;
    private final VBox categoryBox;
    private final VBox versionListBox;
    private final VBox fixedInfoBox;
    private final VBox loaderBox;

    /** Last requested loader-version fetch (deduplicates requests). */
    private ModLoaderType fetchedLoader;
    private String fetchedMcId;

    /**
     * Full picker mode: loader chips, version type chips, version
     * list and loader version list.
     */
    public NewInstanceDialog(Stage owner,
                             ModLoaderRegistry registry,
                             List<MinecraftVersion> manifestVersions) {
        this(owner, registry, manifestVersions, null, null);
    }

    /**
     * Fixed-entry mode (double-clicked version browser row): the
     * Minecraft version and loader family are already chosen; only
     * the loader version (newest by default, changeable) and JVM
     * arguments are picked here.
     */
    private NewInstanceDialog(Stage owner,
                              ModLoaderRegistry registry,
                              MinecraftVersion fixedVersion,
                              ModLoaderType fixedLoader) {
        this(owner, registry, null, fixedVersion, fixedLoader);
    }

    private NewInstanceDialog(Stage owner,
                              ModLoaderRegistry registry,
                              List<MinecraftVersion> manifestVersions,
                              MinecraftVersion fixedVersion,
                              ModLoaderType fixedLoader) {
        this.registry = registry;
        this.manifestVersions = manifestVersions;
        this.fixedVersion = fixedVersion;
        this.fixedLoader = fixedLoader;

        initStyle(StageStyle.UTILITY);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setResizable(false);
        MinecraftVersion shownVersion =
                fixedVersion != null ? fixedVersion
                        : (manifestVersions != null && !manifestVersions.isEmpty()
                                ? manifestVersions.get(0) : null);
        setTitle("New Instance"
                + (shownVersion != null ? ": " + shownVersion.id() : ""));

        // -- 0. Instance name (optional — automatic when empty) --
        Label nameTitle = new Label("Instance name");
        nameTitle.getStyleClass().add("section-title");
        nameField.setPromptText("e.g. My Pack (optional)");
        nameField.getStyleClass().add("search-field");
        nameField.setMaxWidth(Double.MAX_VALUE);
        VBox nameBox = new VBox(4, nameTitle, nameField);

        // -- 1. Loader chips --
        Label loaderTitle = new Label("Loader");
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

        // -- 2. Version type chips (vanilla only) --
        Label categoryTitle = new Label("Version type");
        categoryTitle.getStyleClass().add("section-title");
        for (StandardVersionType type : List.of(StandardVersionType.RELEASE,
                StandardVersionType.SNAPSHOT, StandardVersionType.OLD_BETA,
                StandardVersionType.OLD_ALPHA)) {
            ToggleButton chip = new ToggleButton(type.displayName());
            chip.getStyleClass().add("filter-button");
            chip.setToggleGroup(categoryGroup);
            chip.setUserData(type);
            chip.setOnAction(e -> refreshVersionList());
            categoryChips.getChildren().add(chip);
            if (type == StandardVersionType.RELEASE) {
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

        // -- 3. Version list --
        Label versionTitle = new Label("Version");
        versionTitle.getStyleClass().add("section-title");
        versionList.getStyleClass().add("profile-list");
        versionList.setPrefHeight(230);
        versionList.setPlaceholder(new Label("No versions in this category."));
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
                    Label date = new Label(item.formattedReleaseTime());
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

        // -- 4. Loader version (modded only) --
        Label loaderVersionTitle = new Label("Loader version");
        loaderVersionTitle.getStyleClass().add("section-title");
        loaderCombo.setMaxWidth(Double.MAX_VALUE);
        loaderCombo.setDisable(true);
        loaderCombo.getStyleClass().add("search-field");
        loaderCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(ModLoaderVersion v) {
                if (v == null) return "";
                return v.loaderVersion()
                        + (v.stable() ? "" : "  (beta)");
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

        // -- Fixed entry (double-clicked browser row): version + type --
        VBox fixedBox;
        if (fixedVersion != null) {
            Label fixedTitle = new Label("Version");
            fixedTitle.getStyleClass().add("section-title");
            Label idLabel = new Label(fixedVersion.id());
            idLabel.getStyleClass().add("profile-name");
            String familyText = fixedLoader != null
                    && fixedLoader != ModLoaderType.VANILLA
                            ? fixedLoader.displayName()
                            : fixedVersion.type().displayName();
            Label detailsLabel = new Label(familyText + " · "
                    + fixedVersion.formattedReleaseTime());
            detailsLabel.getStyleClass().add("profile-summary");
            Region fixedSpacer = new Region();
            HBox.setHgrow(fixedSpacer, Priority.ALWAYS);
            HBox versionRow = new HBox(8, idLabel, fixedSpacer, detailsLabel);
            versionRow.setAlignment(Pos.CENTER_LEFT);
            versionRow.getStyleClass().add("slot-box");
            fixedBox = new VBox(4, fixedTitle, versionRow);
        } else {
            fixedBox = new VBox();
        }
        fixedInfoBox = fixedBox;

        // -- JVM args --
        Label jvmLabel = new Label(
                "Extra JVM arguments (one per line, optional)");
        jvmLabel.getStyleClass().add("section-title");
        jvmArgsArea.setPromptText("-Xmx4G");
        jvmArgsArea.setPrefRowCount(3);
        jvmArgsArea.setPrefColumnCount(30);
        jvmArgsArea.setWrapText(false);
        jvmArgsArea.getStyleClass().add("profile-jvm-args");

        // -- Buttons --
        createButton.getStyleClass().add("install-close-button");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> {
            ModLoaderType type = effectiveLoader();
            result = new Result(
                    type,
                    effectiveVersion(),
                    type == ModLoaderType.VANILLA ? null
                            : loaderCombo.getValue(),
                    parseJvmArgs(jvmArgsArea.getText()),
                    nameField.getText().trim());
            close();
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("install-close-button");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> close());
        HBox buttons = new HBox(8, cancelButton, createButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        // -- Initial state: newest release preselected --
        if (fixedVersion == null) {
            refreshVersionList();
            if (versionList.getSelectionModel().getSelectedItem() == null) {
                onInputsChanged();
            }
        } else {
            onInputsChanged();
        }

        VBox content = new VBox(6,
                nameBox,
                loaderChipsBox,
                categoryBox,
                versionListBox,
                fixedInfoBox,
                loaderBox,
                jvmLabel, jvmArgsArea,
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

    /** The currently selected loader family (Vanilla by default). */
    private ModLoaderType selectedLoader() {
        var selected = loaderGroup.getSelectedToggle();
        if (selected instanceof ToggleButton btn
                && btn.getUserData() instanceof ModLoaderType type) {
            return type;
        }
        return ModLoaderType.VANILLA;
    }

    /**
     * The loader family of the instance to create: fixed in
     * fixed-entry mode, otherwise the picked chip.
     */
    private ModLoaderType effectiveLoader() {
        if (fixedLoader != null) {
            return fixedLoader;
        }
        return fixedVersion != null ? ModLoaderType.VANILLA
                : selectedLoader();
    }

    /** The MC version of the instance to create. */
    private MinecraftVersion effectiveVersion() {
        if (fixedVersion != null) {
            return fixedVersion;
        }
        return versionList.getSelectionModel().getSelectedItem();
    }

    /**
     * Loader switch: modded loaders run on releases only (the version
     * type chips are hidden and Release is forced); vanilla offers
     * all version types.
     */
    private void onLoaderChanged() {
        boolean modded = selectedLoader() != ModLoaderType.VANILLA;
        setVisibleManaged(categoryBox, !modded);
        if (modded) {
            for (var node : categoryChips.getChildren()) {
                if (node instanceof ToggleButton btn
                        && btn.getUserData() == StandardVersionType.RELEASE) {
                    btn.setSelected(true);
                    break;
                }
            }
        }
        refreshVersionList();
        onInputsChanged();
    }

    private StandardVersionType activeCategory() {
        for (var node : categoryChips.getChildren()) {
            if (node instanceof ToggleButton btn && btn.isSelected()
                    && btn.getUserData() instanceof StandardVersionType type) {
                return type;
            }
        }
        return StandardVersionType.RELEASE;
    }

    /**
     * Rebuilds the version list for the active loader + category,
     * keeping the current selection when it still matches and
     * selecting the newest version otherwise.
     */
    private void refreshVersionList() {
        boolean modded = selectedLoader() != ModLoaderType.VANILLA;
        StandardVersionType category =
                modded ? StandardVersionType.RELEASE : activeCategory();
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
        // Newest first (the manifest is newest-first)
        versionList.getSelectionModel().selectFirst();
    }

    /**
     * Reacts to loader/version changes: shows the loader version row
     * only for modded loaders, fetches their versions asynchronously
     * (deduplicated per loader + version) and keeps the Create button
     * in sync.
     */
    private void onInputsChanged() {
        boolean fixed = fixedVersion != null;
        setVisibleManaged(loaderChipsBox, !fixed);
        setVisibleManaged(categoryBox, !fixed);
        setVisibleManaged(versionListBox, !fixed);
        setVisibleManaged(fixedInfoBox, fixed);

        ModLoaderType loader = effectiveLoader();
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

        // Same loader + version: items are already loaded or the
        // request is in flight — no second fetch
        if (loader == fetchedLoader && mc.id().equals(fetchedMcId)) {
            loaderCombo.setDisable(loaderCombo.getItems().isEmpty());
            return;
        }

        var regEntry = registry.get(loader).orElse(null);
        if (regEntry == null) {
            loaderStatusLabel.setText(loader.displayName()
                    + " is not available in this launcher");
            return;
        }

        fetchedLoader = loader;
        fetchedMcId = mc.id();
        loaderProgress.setVisible(true);
        loaderStatusLabel.setText("Loading " + loader.displayName()
                + " versions for MC " + mc.id() + "...");

        Thread fetch = new Thread(() -> {
            List<ModLoaderVersion> versions;
            try {
                versions = regEntry.provider().fetchVersions(mc.id());
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (effectiveLoader() == loader && isCurrent(mc)) {
                        loaderProgress.setVisible(false);
                        loaderStatusLabel.setText("Failed to load versions: "
                                + ex.getMessage());
                        fetchedLoader = null;
                        fetchedMcId = null;
                        updateCreateButtonState();
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (effectiveLoader() != loader || !isCurrent(mc)) {
                    return; // selection changed meanwhile
                }
                loaderProgress.setVisible(false);
                if (versions.isEmpty()) {
                    loaderStatusLabel.setText("No " + loader.displayName()
                            + " versions for MC " + mc.id());
                } else {
                    loaderCombo.getItems().setAll(versions);
                    loaderCombo.getSelectionModel().selectFirst();
                    loaderCombo.setDisable(false);
                    loaderStatusLabel.setText(versions.size()
                            + " versions for MC " + mc.id()
                            + " (all compatible)");
                }
                updateCreateButtonState();
            });
        }, "loader-versions-fetch");
        fetch.setDaemon(true);
        fetch.start();
    }

    /** True if {@code mc} is the version the dialog would create now. */
    private boolean isCurrent(MinecraftVersion mc) {
        MinecraftVersion current = effectiveVersion();
        return current != null && current.id().equals(mc.id());
    }

    private void updateCreateButtonState() {
        MinecraftVersion mc = effectiveVersion();
        boolean modded = effectiveLoader() != ModLoaderType.VANILLA;
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

    private static List<String> parseJvmArgs(String text) {
        List<String> args = new ArrayList<>();
        if (text == null) return List.of();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                args.add(trimmed);
            }
        }
        return List.copyOf(args);
    }

    /**
     * Shows the dialog and returns the creation parameters, or
     * {@code null} if it was cancelled.
     */
    public static Result show(Stage owner,
                              ModLoaderRegistry registry,
                              List<MinecraftVersion> manifestVersions) {
        NewInstanceDialog dialog = new NewInstanceDialog(
                owner, registry, manifestVersions);
        dialog.showAndWait();
        return dialog.result;
    }

    /**
     * Shows the dialog for a version (or modded variant of one)
     * double-clicked in the version browser: loader family and
     * Minecraft version are fixed, the loader version defaults to
     * the newest (changeable). Returns the creation parameters, or
     * {@code null} if it was cancelled.
     */
    public static Result showFor(Stage owner,
                                 ModLoaderRegistry registry,
                                 MinecraftVersion version,
                                 ModLoaderType family) {
        NewInstanceDialog dialog = new NewInstanceDialog(
                owner, registry, version,
                family != null ? family : ModLoaderType.VANILLA);
        dialog.showAndWait();
        return dialog.result;
    }
}
