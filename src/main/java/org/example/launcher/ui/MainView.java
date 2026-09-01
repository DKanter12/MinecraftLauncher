package org.example.launcher.ui;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.LaunchResult;
import org.example.launcher.model.MinecraftProcess;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.DefaultJavaResolutionService;
import org.example.launcher.service.ElyAuthService;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.JavaRuntimeInstaller;
import org.example.launcher.service.LauncherPreferences;
import org.example.launcher.service.MinecraftLaunchService;
import org.example.launcher.service.ModdedProfileService;
import org.example.launcher.service.ProfileService;
import org.example.launcher.service.SkinService;
import org.example.launcher.service.VersionMetadataService;
import org.example.launcher.service.VersionService;
import org.example.launcher.service.modloader.ModLoaderRegistry;
import org.example.launcher.service.modloader.ModLoaderType;
import org.example.launcher.service.modloader.ModdedProfileVerificationService;
import org.example.launcher.service.modloader.ModdedVersionService;
import org.example.launcher.version.StandardVersionType;
import org.example.launcher.version.VersionType;
import org.example.launcher.version.VersionTypeRegistry;

/**
 * Builds and manages the main launcher window.
 * <p>
 * Layout (BorderPane):
 * <pre>
 * +------------------------------------------------------------+
 * |  Header: title + status + account                          |
 * +--------+---------------------------------------------------+
 * | Instan-|                                                   |
 * | ces    |   Selected instance details                       |
 * | list   |   (badge, name, versions, game directory,        |
 * |        |    JVM arguments, last played)                    |
 * | + New  |                                                   |
 * |        |   [ Play ]  [Folder] [Edit] [Delete]              |
 * +--------+---------------------------------------------------+
 * </pre>
 * <p>
 * Everything is an instance (vanilla or modded), each with its own
 * game directory. New instances are created via a single simple
 * dialog (loader chips → version list → loader version list, like
 * Modrinth); the Mojang manifest is only fetched in the background
 * to feed that dialog.
 */
public class MainView {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final VersionService versionService;
    private final VersionMetadataService metadataService;
    private final InstallationService installationService;
    private final MinecraftLaunchService launchService;
    private final JavaResolutionService javaResolutionService;
    private final JavaRuntimeInstaller javaRuntimeInstaller;
    private final ProfileService profileService;
    private final LauncherPreferences preferences;
    private final ElyAuthService elyAuthService;
    private final SkinService skinService;
    private final VersionTypeRegistry typeRegistry;
    private final ModLoaderRegistry modLoaderRegistry;
    private final ModdedVersionService moddedVersionService;
    private final ModdedProfileService moddedProfileService;
    private final ModdedProfileVerificationService profileVerificationService;

    private BorderPane root;
    private Label statusLabel;
    private boolean versionsLoadFailed = false;

    // Sidebar
    private ListView<ModdedProfile> profileListView;
    private Button newInstanceButton;

    // Version browser (center): all versions incl. modded variants
    private TextField versionSearchField;
    private FlowPane versionFilterChips;
    private Label versionBrowserTitle;
    private ListView<VersionEntry> versionListView;
    private List<VersionEntry> versionEntries = List.of();

    // Version browser: actions for the selected entry
    private Label selectedVersionLabel;
    private Button loaderVersionButton;
    private Button playVersionButton;
    private Button folderVersionButton;
    private boolean versionPlayInFlight = false;

    // Loader versions for the selected modded entry (newest first)
    private ModLoaderVersion selectedLoaderVersion;
    private List<ModLoaderVersion> selectedLoaderVersions = List.of();
    private ModLoaderType loaderVersionsFamily;
    private String loaderVersionsMcId;
    private boolean loaderVersionChanged = false;

    // Instance details (right)
    private VBox instanceDetailsContent;
    private Label instanceEmptyLabel;
    private Label instanceBadgeLabel;
    private Label instanceNameLabel;
    private Label instanceSummaryLabel;
    private Label instanceVersionLine;
    private Label instanceDirLine;
    private Label instanceJvmLine;
    private Label instanceMetaLine;
    private List<ModdedProfile> moddedProfiles = List.of();
    private Button playProfileButton;
    private Button openProfileFolderButton;
    private Button editProfileButton;
    private Button deleteProfileButton;

    // Instance launch state
    private boolean javaDownloadAttempted = false;

    // Account
    private ComboBox<GameProfile> accountCombo;
    private Button createAccountButton;
    private Button elyLoginButton;
    private ImageView avatarView;
    private Label accountNameLabel;
    private Label accountTypeLabel;
    private GameProfile selectedProfile;
    private boolean suppressSelectionListener = false;
    private java.util.Timer elyRefreshTimer;

    /** Versions of the Mojang manifest — basis of the version browser. */
    private List<MinecraftVersion> manifestVersions = List.of();

    /**
     * Minecraft versions each registered loader exists for, as
     * reported by the loader's own metadata service. Loaded once in
     * the background; a loader's variants appear in the version
     * browser only after its set arrived (never showing variants
     * that do not exist — e.g. NeoForge below 1.20.1 or Fabric
     * below 1.14).
     */
    private final Map<ModLoaderType, Set<String>> loaderSupportedVersions =
            new EnumMap<>(ModLoaderType.class);

    public MainView(VersionService versionService,
                    VersionMetadataService metadataService,
                    InstallationService installationService,
                    MinecraftLaunchService launchService,
                    JavaResolutionService javaResolutionService,
                    JavaRuntimeInstaller javaRuntimeInstaller,
                    ProfileService profileService,
                    VersionTypeRegistry typeRegistry,
                    LauncherPreferences preferences,
                    ElyAuthService elyAuthService,
                    SkinService skinService,
                    ModLoaderRegistry modLoaderRegistry,
                    ModdedVersionService moddedVersionService,
                    ModdedProfileService moddedProfileService,
                    ModdedProfileVerificationService profileVerificationService) {
        this.versionService = versionService;
        this.metadataService = metadataService;
        this.installationService = installationService;
        this.launchService = launchService;
        this.javaResolutionService = javaResolutionService;
        this.javaRuntimeInstaller = javaRuntimeInstaller;
        this.profileService = profileService;
        this.typeRegistry = typeRegistry;
        this.preferences = preferences;
        this.elyAuthService = elyAuthService;
        this.skinService = skinService;
        this.modLoaderRegistry = modLoaderRegistry;
        this.moddedVersionService = moddedVersionService;
        this.moddedProfileService = moddedProfileService;
        this.profileVerificationService = profileVerificationService;
        buildView();
        loadLoaderSupport();
    }

    public BorderPane getView() {
        return root;
    }

    // ------------------------------------------------------------------
    //  UI construction
    // ------------------------------------------------------------------

    private void buildView() {
        root = new BorderPane();
        root.getStyleClass().add("root-pane");

        root.setTop(buildHeader());
        root.setLeft(buildSidebar());
        root.setCenter(buildVersionBrowser());
        root.setRight(buildInstanceDetailsPanel());
    }

    // --- Header ---

    private HBox buildHeader() {
        Label title = new Label("Minecraft Launcher");
        title.getStyleClass().add("title-label");

        statusLabel = new Label("Loading versions...");
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Account widget
        avatarView = new ImageView();
        avatarView.setFitWidth(32);
        avatarView.setFitHeight(32);
        avatarView.setPreserveRatio(true);
        avatarView.setVisible(false);

        Label accountLabel = new Label("Account:");
        accountLabel.getStyleClass().add("account-label");

        accountCombo = new ComboBox<>();
        accountCombo.setPrefWidth(200);
        accountCombo.setPromptText("No account");
        accountCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(GameProfile profile) {
                if (profile == null) return "";
                String type = profile.isElyBy() ? " [Ely.by]" : " [Offline]";
                return profile.name() + type;
            }

            @Override
            public GameProfile fromString(String string) {
                return null;
            }
        });
        accountCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> {
                    if (suppressSelectionListener) return;
                    if (val != null) {
                        selectedProfile = val;
                        saveLastSelectedAccount(val.name());
                        updateAvatar(val);
                    }
                });

        createAccountButton = new Button("+");
        createAccountButton.getStyleClass().add("browse-java-button");
        createAccountButton.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        createAccountButton.setOnAction(e -> onCreateAccount());

        elyLoginButton = new Button("Ely.by");
        elyLoginButton.getStyleClass().add("browse-java-button");
        elyLoginButton.setStyle("-fx-font-size: 11px;");
        elyLoginButton.setOnAction(e -> onElyLogin());

        HBox accountBox = new HBox(8, avatarView, accountLabel, accountCombo, createAccountButton, elyLoginButton);
        accountBox.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(15, title, statusLabel, accountBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.getStyleClass().add("header");
        return header;
    }

    // --- Sidebar ---

    private VBox buildSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(230);
        sidebar.setMaxWidth(300);
        sidebar.setPadding(new Insets(16));
        sidebar.getStyleClass().add("sidebar");

        // -- Instances: the playable list (vanilla + modded) --
        VBox instancesBox = buildInstancesPanel();
        sidebar.getChildren().add(instancesBox);
        return sidebar;
    }

    // --- Instances panel (sidebar) ---

    private VBox buildInstancesPanel() {
        Label title = new Label("Instances");
        title.getStyleClass().add("section-title");

        profileListView = new ListView<>();
        profileListView.getStyleClass().add("profile-list");
        profileListView.setPlaceholder(new Label(
                "No instances yet.\nPress '+ New' to create one."));
        VBox.setVgrow(profileListView, Priority.ALWAYS);
        profileListView.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ModdedProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.loaderType().displayName());
                    badge.getStyleClass().add(loaderBadgeStyle(item));
                    Label name = new Label(item.name());
                    name.getStyleClass().add("profile-name");
                    Label summary = new Label(item.summary());
                    summary.getStyleClass().add("profile-summary");
                    VBox content = new VBox(2, new HBox(6, badge, name), summary);
                    setText(null);
                    setGraphic(content);
                }
            }
        });
        profileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> {
                    updateProfileButtons();
                    updateInstanceDetails();
                });

        newInstanceButton = new Button("+ New");
        newInstanceButton.getStyleClass().add("quick-select-button");
        newInstanceButton.setMaxWidth(Double.MAX_VALUE);
        newInstanceButton.setOnAction(e -> onNewInstance());

        VBox panel = new VBox(6, title, profileListView, newInstanceButton);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    /** CSS badge style per instance type. */
    private static String loaderBadgeStyle(ModdedProfile profile) {
        return loaderBadgeStyle(profile.loaderType());
    }

    /** CSS badge style per loader family. */
    private static String loaderBadgeStyle(ModLoaderType type) {
        return switch (type) {
            case VANILLA -> "loader-badge-vanilla";
            case FABRIC -> "loader-badge-fabric";
            case FORGE -> "loader-badge-forge";
            case NEOFORGE -> "loader-badge-neoforge";
            case QUILT -> "loader-badge-quilt";
        };
    }

    private void updateProfileButtons() {
        boolean hasSelection = profileListView != null
                && profileListView.getSelectionModel().getSelectedItem() != null;
        playProfileButton.setDisable(!hasSelection);
        openProfileFolderButton.setDisable(!hasSelection);
        editProfileButton.setDisable(!hasSelection);
        deleteProfileButton.setDisable(!hasSelection);
        // The version browser's Play follows the same lifecycle: a
        // running launch attempt blocks it until a terminal callback
        // (launch end/failure, game exit) refreshes the buttons
        versionPlayInFlight = false;
        updateVersionActionBar();
    }

    // ------------------------------------------------------------------
    //  Center: version browser — ALL versions (vanilla + modded
    //  variants), filterable by text, version type and loader
    // ------------------------------------------------------------------

    /**
     * One row of the version browser: a vanilla manifest version or
     * a modded variant of one ({@code 1.20.1 Fabric}). Modded
     * variants exist for release versions only; their concrete
     * loader version is chosen at instance creation (newest by
     * default, changeable).
     */
    private record VersionEntry(MinecraftVersion version,
                                ModLoaderType variant) {

        boolean isVanilla() {
            return variant == ModLoaderType.VANILLA;
        }

        /** Row title, e.g. {@code "1.20.1"} or {@code "1.20.1 Fabric"}. */
        String displayId() {
            return isVanilla() ? version.id()
                    : version.id() + " " + variant.displayName();
        }

        /** The type badge label (Release/Snapshot/…/Fabric/…). */
        String typeLabel() {
            return isVanilla() ? version.type().displayName()
                    : variant.displayName();
        }

        String badgeStyle() {
            return isVanilla() ? typeBadgeStyle(version.type())
                    : loaderBadgeStyle(variant);
        }
    }

    /** CSS badge style per vanilla version type. */
    private static String typeBadgeStyle(VersionType type) {
        if (type == StandardVersionType.RELEASE) {
            return "type-badge-release";
        }
        if (type == StandardVersionType.SNAPSHOT) {
            return "type-badge-snapshot";
        }
        if (type == StandardVersionType.OLD_BETA) {
            return "type-badge-old-beta";
        }
        if (type == StandardVersionType.OLD_ALPHA) {
            return "type-badge-old-alpha";
        }
        return "type-badge-old-alpha"; // unknown custom type
    }

    private VBox buildVersionBrowser() {
        versionBrowserTitle = new Label("Versions");
        versionBrowserTitle.getStyleClass().add("section-title");

        versionSearchField = new TextField();
        versionSearchField.setPromptText(
                "Filter (e.g. 1.20.1, fabric, snapshot)...");
        versionSearchField.getStyleClass().add("search-field");
        versionSearchField.textProperty().addListener(
                (obs, old, val) -> applyVersionFilter());

        // Filters combine freely: several version types, several
        // loader families, or both at once (Fabric + Forge + Snapshot
        // …). No chip selected = every version is shown.
        versionFilterChips = new FlowPane(5, 5);
        for (StandardVersionType type : List.of(StandardVersionType.RELEASE,
                StandardVersionType.SNAPSHOT, StandardVersionType.OLD_BETA,
                StandardVersionType.OLD_ALPHA)) {
            addVersionFilterChip(type.displayName(), type);
        }
        for (ModLoaderType loader : ModLoaderType.values()) {
            if (loader != ModLoaderType.VANILLA
                    && modLoaderRegistry.get(loader).isPresent()) {
                addVersionFilterChip(loader.displayName(), loader);
            }
        }

        versionListView = new ListView<>();
        versionListView.getStyleClass().add("profile-list");
        versionListView.setPlaceholder(new Label("No versions match."));
        VBox.setVgrow(versionListView, Priority.ALWAYS);
        versionListView.setCellFactory(list -> {
            ListCell<VersionEntry> cell = new ListCell<>() {
                @Override
                protected void updateItem(VersionEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        Label badge = new Label(item.typeLabel());
                        badge.getStyleClass().add(item.badgeStyle());
                        Label id = new Label(item.displayId());
                        id.getStyleClass().add("profile-name");
                        Label date = new Label(
                                item.version().formattedReleaseTime());
                        date.getStyleClass().add("profile-summary");
                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);
                        HBox row = new HBox(8, badge, id, spacer, date);
                        row.setAlignment(Pos.CENTER_LEFT);
                        setText(null);
                        setGraphic(row);
                    }
                }
            };
            // Double-click a version (or modded variant) to create an
            // instance of it right away
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !cell.isEmpty()) {
                    onEntryActivated(cell.getItem());
                }
            });
            return cell;
        });

        versionListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> onVersionSelected(val));

        Label hint = new Label(
                "Filters combine freely (Fabric + Forge + Snapshot…); "
                        + "none selected shows all versions.");
        hint.getStyleClass().add("quick-select-label");

        // -- Actions for the selected version: play it, open its
        //    directory, and for modded entries pick the loader version
        //    (newest by default, changeable) --
        selectedVersionLabel = new Label("Select a version to play");
        selectedVersionLabel.getStyleClass().add("details-info");

        loaderVersionButton = new Button("Loader version");
        loaderVersionButton.getStyleClass().add("quick-select-button");
        loaderVersionButton.setOnAction(e -> onChangeLoaderVersion());

        playVersionButton = new Button("Play");
        playVersionButton.getStyleClass().add("play-button-compact");
        playVersionButton.setDisable(true);
        playVersionButton.setOnAction(e -> onPlaySelectedVersion());

        folderVersionButton = new Button("Folder");
        folderVersionButton.getStyleClass().add("quick-select-button");
        folderVersionButton.setDisable(true);
        folderVersionButton.setOnAction(e -> onOpenSelectedVersionFolder());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox versionActionBar = new HBox(8, selectedVersionLabel,
                actionSpacer, loaderVersionButton, playVersionButton,
                folderVersionButton);
        versionActionBar.setAlignment(Pos.CENTER_LEFT);

        VBox browser = new VBox(8, versionBrowserTitle, versionSearchField,
                versionFilterChips, versionListView, versionActionBar, hint);
        browser.setPadding(new Insets(16));
        return browser;
    }

    /**
     * Adds an independently toggleable filter chip: several chips can
     * be active at once (e.g. Fabric + Forge), combining their
     * results.
     */
    private void addVersionFilterChip(String label, Object filter) {
        ToggleButton chip = new ToggleButton(label);
        chip.getStyleClass().add("filter-button");
        chip.setUserData(filter);
        chip.selectedProperty().addListener((obs, old, sel) -> {
            if (sel) {
                chip.getStyleClass().add("filter-button-active");
            } else {
                chip.getStyleClass().remove("filter-button-active");
            }
            applyVersionFilter();
        });
        versionFilterChips.getChildren().add(chip);
    }

    /** Builds every browser row: vanilla versions + modded variants. */
    private void buildVersionEntries() {
        List<ModLoaderType> families = new ArrayList<>();
        for (ModLoaderType loader : ModLoaderType.values()) {
            if (loader != ModLoaderType.VANILLA
                    && modLoaderRegistry.get(loader).isPresent()) {
                families.add(loader);
            }
        }
        List<VersionEntry> entries = new ArrayList<>();
        for (MinecraftVersion v : manifestVersions) {
            entries.add(new VersionEntry(v, ModLoaderType.VANILLA));
            if (v.type() == StandardVersionType.RELEASE) {
                for (ModLoaderType family : families) {
                    Set<String> supported = loaderSupportedVersions.get(family);
                    // Unknown set (still loading / offline) or the
                    // loader does not exist for this MC version → no
                    // variant row; only real, installable combinations
                    // are ever listed
                    if (supported == null || !supported.contains(v.id())) {
                        continue;
                    }
                    entries.add(new VersionEntry(v, family));
                }
            }
        }
        versionEntries = entries;
    }

    /**
     * Loads each registered loader's set of supported Minecraft
     * versions in the background (one lightweight call per loader)
     * and refreshes the version browser as the sets arrive — the
     * authoritative source of which modded variants actually exist.
     */
    private void loadLoaderSupport() {
        for (ModLoaderType type : ModLoaderType.values()) {
            if (type == ModLoaderType.VANILLA) {
                continue;
            }
            var entry = modLoaderRegistry.get(type).orElse(null);
            if (entry == null) {
                continue;
            }
            Thread fetch = new Thread(() -> {
                Set<String> supported;
                try {
                    supported = entry.provider()
                            .fetchSupportedMinecraftVersions();
                } catch (Exception ex) {
                    return; // stays unknown → that loader's variants
                            // are not listed (offline: no wrong rows)
                }
                if (supported == null) {
                    return;
                }
                Platform.runLater(() -> {
                    loaderSupportedVersions.put(type, supported);
                    if (!manifestVersions.isEmpty()) {
                        buildVersionEntries();
                        applyVersionFilter();
                    }
                });
            }, "loader-support-" + type.name().toLowerCase());
            fetch.setDaemon(true);
            fetch.start();
        }
    }

    /**
     * Applies the active chip filters — any combination of version
     * types and loader families, combined with OR — plus the
     * free-text filter, newest versions first; the modded variants
     * cluster right below their vanilla version.
     */
    private void applyVersionFilter() {
        if (versionListView == null) return;
        Set<StandardVersionType> types = EnumSet.noneOf(StandardVersionType.class);
        Set<ModLoaderType> loaders = EnumSet.noneOf(ModLoaderType.class);
        for (var node : versionFilterChips.getChildren()) {
            if (node instanceof ToggleButton btn && btn.isSelected()) {
                if (btn.getUserData() instanceof StandardVersionType type) {
                    types.add(type);
                } else if (btn.getUserData() instanceof ModLoaderType loader) {
                    loaders.add(loader);
                }
            }
        }
        boolean noFilter = types.isEmpty() && loaders.isEmpty();
        String query = versionSearchField.getText() == null ? ""
                : versionSearchField.getText().trim().toLowerCase();

        List<VersionEntry> filtered = new ArrayList<>();
        for (VersionEntry entry : versionEntries) {
            if (!noFilter) {
                // Type chips match vanilla entries, loader chips match
                // modded variants — several active chips combine
                boolean matches = entry.isVanilla()
                        ? types.contains(entry.version().type())
                        : loaders.contains(entry.variant());
                if (!matches) {
                    continue;
                }
            }
            if (!query.isEmpty()) {
                String haystack = (entry.displayId() + " "
                        + entry.typeLabel()).toLowerCase();
                if (!haystack.contains(query)) {
                    continue;
                }
            }
            filtered.add(entry);
        }

        filtered.sort(Comparator
                .comparing((VersionEntry e) ->
                                e.version().releaseTime().orElse(null),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(e -> e.variant().ordinal())
                .thenComparing(e -> e.version().id()));

        versionListView.setItems(FXCollections.observableArrayList(filtered));
        if (versionBrowserTitle != null && !versionEntries.isEmpty()) {
            versionBrowserTitle.setText(
                    filtered.size() == versionEntries.size() ? "Versions"
                            : "Versions (" + filtered.size() + " of "
                                    + versionEntries.size() + ")");
        }
    }

    /** Creates an instance for the double-clicked browser entry. */
    private void onEntryActivated(VersionEntry entry) {
        NewInstanceDialog.Result result = NewInstanceDialog.showFor(
                (Stage) root.getScene().getWindow(),
                modLoaderRegistry, entry.version(), entry.variant());
        if (result != null) {
            createInstance(result);
        }
    }

    // --- Selected version: loader version, play, folder ---

    /**
     * Reacts to a version browser selection: shows the action bar for
     * it and, for modded entries, loads the loader versions (newest
     * first) in the background — the newest one is the default.
     */
    private void onVersionSelected(VersionEntry entry) {
        loaderVersionChanged = false;
        selectedLoaderVersion = null;
        if (entry == null || entry.isVanilla()) {
            selectedLoaderVersions = List.of();
            loaderVersionsFamily = null;
            loaderVersionsMcId = null;
            updateVersionActionBar();
            return;
        }
        ModLoaderType family = entry.variant();
        String mcId = entry.version().id();
        if (family == loaderVersionsFamily
                && mcId.equals(loaderVersionsMcId)
                && !selectedLoaderVersions.isEmpty()) {
            selectedLoaderVersion = selectedLoaderVersions.get(0);
            updateVersionActionBar();
            return;
        }
        loaderVersionsFamily = family;
        loaderVersionsMcId = mcId;
        selectedLoaderVersions = List.of();
        updateVersionActionBar();

        var regEntry = modLoaderRegistry.get(family).orElse(null);
        if (regEntry == null) {
            updateVersionActionBar();
            return;
        }
        statusLabel.setText("Loading " + family.displayName()
                + " versions for MC " + mcId + "...");
        Thread fetch = new Thread(() -> {
            List<ModLoaderVersion> versions;
            try {
                versions = regEntry.provider().fetchVersions(mcId);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (isCurrentEntry(family, mcId)) {
                        statusLabel.setText("Failed to load "
                                + family.displayName() + " versions: "
                                + ex.getMessage());
                        updateVersionActionBar();
                    }
                });
                return;
            }
            Platform.runLater(() -> {
                if (!isCurrentEntry(family, mcId)) {
                    return; // selection changed meanwhile
                }
                selectedLoaderVersions = versions;
                selectedLoaderVersion =
                        versions.isEmpty() ? null : versions.get(0);
                updateVersionActionBar();
            });
        }, "browser-loader-versions");
        fetch.setDaemon(true);
        fetch.start();
    }

    /** True if {@code family} + {@code mcId} is still selected. */
    private boolean isCurrentEntry(ModLoaderType family, String mcId) {
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        return entry != null && !entry.isVanilla()
                && entry.variant() == family
                && entry.version().id().equals(mcId);
    }

    /** Syncs the action bar with the selection + loader state. */
    private void updateVersionActionBar() {
        if (selectedVersionLabel == null) return;
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        boolean has = entry != null;
        boolean modded = has && !entry.isVanilla();

        if (has) {
            selectedVersionLabel.setText(
                    entry.isVanilla()
                            ? entry.version().id() + " · "
                                    + entry.typeLabel()
                            : entry.displayId());
        } else {
            selectedVersionLabel.setText("Select a version to play");
        }

        playVersionButton.setDisable(!has || versionPlayInFlight);
        folderVersionButton.setDisable(!has);

        loaderVersionButton.setVisible(modded);
        loaderVersionButton.setManaged(modded);
        if (!modded) {
            loaderVersionButton.setText("Loader version");
            return;
        }
        ModLoaderType family = entry.variant();
        if (selectedLoaderVersions.isEmpty()) {
            loaderVersionButton.setDisable(true);
            loaderVersionButton.setText(family.displayName()
                    + " version: loading...");
            return;
        }
        loaderVersionButton.setDisable(false);
        String suffix = loaderVersionChanged ? "" : " (latest)";
        if (selectedLoaderVersion == null) {
            loaderVersionButton.setText(family.displayName()
                    + " version: none" + suffix);
        } else {
            loaderVersionButton.setText(family.displayName()
                    + " version: "
                    + selectedLoaderVersion.loaderVersion() + suffix);
        }
    }

    /**
     * Opens the loader version picker for the selected modded entry:
     * every listed version is compatible with the chosen Minecraft
     * version; the newest one is preselected.
     */
    private void onChangeLoaderVersion() {
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        if (entry == null || entry.isVanilla()
                || selectedLoaderVersions.isEmpty()) {
            return;
        }
        ModLoaderVersion picked = LoaderVersionDialog.show(
                (Stage) root.getScene().getWindow(),
                entry.variant(), entry.version().id(),
                selectedLoaderVersions, selectedLoaderVersion);
        if (picked != null) {
            selectedLoaderVersion = picked;
            loaderVersionChanged = true;
            updateVersionActionBar();
        }
    }

    /**
     * Plays the selected version with the same launch principle as
     * instance Play: an existing matching instance is launched
     * directly (verify → repair if needed → launch); otherwise the
     * instance is created first (installing the version if needed)
     * and launched right after — with the newest loader version by
     * default, or the one picked via the loader version button.
     */
    private void onPlaySelectedVersion() {
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        if (entry == null) return;

        ModdedProfile match = findPlayableInstance(entry);
        if (match != null) {
            profileListView.getSelectionModel().select(match);
            startInstanceLaunch(match);
            return;
        }
        if (!entry.isVanilla() && selectedLoaderVersion == null) {
            statusLabel.setText("Loader versions are still loading"
                    + " — try again in a moment");
            return;
        }
        versionPlayInFlight = true;
        updateVersionActionBar();
        NewInstanceDialog.Result result = new NewInstanceDialog.Result(
                entry.variant(), entry.version(),
                entry.isVanilla() ? null : selectedLoaderVersion,
                "", List.of());
        createInstance(result, true);
    }

    /**
     * Finds an existing instance that matches the browser selection:
     * any instance of that Minecraft version for vanilla entries; for
     * modded entries preferably one with the chosen loader version,
     * otherwise any instance of that loader family (only when the
     * user did not explicitly change the loader version).
     */
    private ModdedProfile findPlayableInstance(VersionEntry entry) {
        String mcId = entry.version().id();
        if (entry.isVanilla()) {
            for (ModdedProfile p : moddedProfiles) {
                if (p.isVanilla() && p.minecraftVersion().equals(mcId)) {
                    return p;
                }
            }
            return null;
        }
        ModdedProfile exact = null;
        ModdedProfile any = null;
        for (ModdedProfile p : moddedProfiles) {
            if (p.isVanilla()
                    || p.loaderType() != entry.variant()
                    || !p.minecraftVersion().equals(mcId)) {
                continue;
            }
            if (any == null) {
                any = p;
            }
            if (selectedLoaderVersion != null
                    && p.loaderVersion().equals(
                            selectedLoaderVersion.loaderVersion())) {
                exact = p;
            }
        }
        if (loaderVersionChanged) {
            return exact;
        }
        return exact != null ? exact : any;
    }

    /**
     * Opens the selected version's directory in the system file
     * manager: the instance's game directory if one exists, otherwise
     * the version's folder under {@code versions/} (or the versions
     * root while it is not installed yet).
     */
    private void onOpenSelectedVersionFolder() {
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        if (entry == null) return;
        try {
            ModdedProfile match = findPlayableInstance(entry);
            if (match != null) {
                Path dir = moddedProfileService.resolveGameDir(match);
                ModdedProfileService.ensureProfileFolders(dir);
                java.awt.Desktop.getDesktop().open(dir.toFile());
                statusLabel.setText("Opened " + dir);
                return;
            }
            GameDirectory storage = GameDirectory.defaultDirectory();
            Path dir = entry.isVanilla()
                    ? storage.versionDir(entry.version().id())
                    : (selectedLoaderVersion != null
                            ? storage.versionDir(
                                    selectedLoaderVersion.installedVersionId())
                            : null);
            if (dir == null || !Files.isDirectory(dir)) {
                dir = storage.versionsDir();
            }
            java.awt.Desktop.getDesktop().open(dir.toFile());
            statusLabel.setText("Opened " + dir);
        } catch (Exception ex) {
            statusLabel.setText("Failed to open folder: " + ex.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Open Folder",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Right: selected instance details
    // ------------------------------------------------------------------

    private VBox buildInstanceDetailsPanel() {
        instanceEmptyLabel = new Label(
                "Select an instance —\nor create one with '+ New'.");
        instanceEmptyLabel.getStyleClass().add("quick-select-label");

        instanceBadgeLabel = new Label();
        instanceNameLabel = new Label();
        instanceNameLabel.getStyleClass().add("details-version");
        instanceSummaryLabel = new Label();
        instanceSummaryLabel.getStyleClass().add("details-info");

        instanceVersionLine = new Label();
        instanceVersionLine.getStyleClass().add("metadata-item");
        instanceDirLine = new Label();
        instanceDirLine.getStyleClass().add("metadata-item");
        instanceDirLine.setWrapText(true);
        instanceJvmLine = new Label();
        instanceJvmLine.getStyleClass().add("metadata-item");
        instanceMetaLine = new Label();
        instanceMetaLine.getStyleClass().add("metadata-item");

        playProfileButton = new Button("Play");
        playProfileButton.getStyleClass().add("play-button");
        playProfileButton.setDisable(true);
        playProfileButton.setMaxWidth(Double.MAX_VALUE);
        playProfileButton.setOnAction(e -> onPlayProfile());

        openProfileFolderButton = new Button("Folder");
        openProfileFolderButton.getStyleClass().add("quick-select-button");
        openProfileFolderButton.setDisable(true);
        HBox.setHgrow(openProfileFolderButton, Priority.ALWAYS);
        openProfileFolderButton.setMaxWidth(Double.MAX_VALUE);
        openProfileFolderButton.setOnAction(e -> onOpenProfileFolder());

        editProfileButton = new Button("Edit");
        editProfileButton.getStyleClass().add("quick-select-button");
        editProfileButton.setDisable(true);
        HBox.setHgrow(editProfileButton, Priority.ALWAYS);
        editProfileButton.setMaxWidth(Double.MAX_VALUE);
        editProfileButton.setOnAction(e -> onEditProfile());

        deleteProfileButton = new Button("Delete");
        deleteProfileButton.getStyleClass().add("quick-select-button");
        deleteProfileButton.setDisable(true);
        HBox.setHgrow(deleteProfileButton, Priority.ALWAYS);
        deleteProfileButton.setMaxWidth(Double.MAX_VALUE);
        deleteProfileButton.setOnAction(e -> onDeleteProfile());

        HBox manageButtons = new HBox(6, openProfileFolderButton,
                editProfileButton, deleteProfileButton);

        VBox infoBox = new VBox(6, instanceBadgeLabel, instanceNameLabel,
                instanceSummaryLabel, instanceVersionLine, instanceDirLine,
                instanceJvmLine, instanceMetaLine);
        infoBox.setAlignment(Pos.CENTER_LEFT);

        instanceDetailsContent = new VBox(14, infoBox, playProfileButton,
                manageButtons);
        instanceDetailsContent.setAlignment(Pos.CENTER);
        instanceDetailsContent.setMaxWidth(290);
        instanceDetailsContent.setVisible(false);
        instanceDetailsContent.setManaged(false);

        Label title = new Label("Instance");
        title.getStyleClass().add("section-title");

        VBox panel = new VBox(10, title, instanceEmptyLabel,
                instanceDetailsContent);
        panel.setPrefWidth(340);
        panel.setMinWidth(300);
        panel.setMaxWidth(380);
        panel.setPadding(new Insets(16));
        panel.getStyleClass().add("metadata-panel");
        return panel;
    }

    /** Refreshes the right-side details from the selected instance. */
    private void updateInstanceDetails() {
        ModdedProfile selected =
                profileListView.getSelectionModel().getSelectedItem();
        boolean has = selected != null;
        if (instanceEmptyLabel != null) {
            instanceEmptyLabel.setVisible(!has);
            instanceEmptyLabel.setManaged(!has);
        }
        if (instanceDetailsContent == null) {
            return;
        }
        instanceDetailsContent.setVisible(has);
        instanceDetailsContent.setManaged(has);
        if (!has) {
            return;
        }

        instanceBadgeLabel.setText(selected.loaderType().displayName());
        instanceBadgeLabel.getStyleClass().clear();
        instanceBadgeLabel.getStyleClass().add(loaderBadgeStyle(selected));

        instanceNameLabel.setText(selected.name());
        instanceSummaryLabel.setText(selected.summary());
        instanceVersionLine.setText("Version: " + selected.minecraftVersion()
                + (selected.isVanilla() ? " (vanilla)"
                        : " · " + selected.loaderType().displayName()
                                + " " + selected.loaderVersion()));
        instanceDirLine.setText("Game directory: "
                + GameDirectory.defaultDirectory().root()
                        .resolve(selected.gameDirPath()));
        instanceJvmLine.setText("JVM arguments: "
                + (selected.extraJvmArgs().isEmpty()
                        ? "none" : String.join(" ", selected.extraJvmArgs())));
        instanceMetaLine.setText("Created: "
                + selected.createdTime().map(TIME_FORMATTER::format).orElse("—")
                + " · Last played: "
                + selected.lastPlayedTime().map(TIME_FORMATTER::format)
                        .orElse("never"));
    }

    // ------------------------------------------------------------------
    //  Async: version list loading (feeds the New Instance dialog)
    // ------------------------------------------------------------------

    public void loadVersions() {
        refreshAccounts();
        startElyByRefreshTimer();
        statusLabel.setText("Loading versions...");

        Task<VersionManifest> task = new Task<>() {
            @Override
            protected VersionManifest call() throws Exception {
                return versionService.fetchVersions();
            }
        };
        task.setOnSucceeded(e -> onVersionsLoaded(task.getValue()));
        task.setOnFailed(e -> onLoadFailed(task.getException()));
        var thread = new Thread(task, "version-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void startElyByRefreshTimer() {
        if (elyRefreshTimer != null) {
            elyRefreshTimer.cancel();
        }
        elyRefreshTimer = new java.util.Timer("ely-refresh-timer", true);
        elyRefreshTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                checkAllElyByProfiles();
            }
        }, 0, 30000);
    }

    private void checkAllElyByProfiles() {
        try {
            var profiles = profileService.loadProfiles();
            boolean changed = false;
            String[] nameChange = null;
            var updated = new java.util.ArrayList<GameProfile>();

            for (GameProfile p : profiles) {
                if (p.isElyBy() && p.uuid().isPresent()) {
                    GameProfile refreshed = elyAuthService.refreshProfile(p);
                    boolean nameChanged = !refreshed.name().equals(p.name());
                    if (nameChanged) {
                        System.out.println("[ELY] === NICK CHANGED === old='" + p.name()
                                + "' new='" + refreshed.name() + "'");
                        nameChange = new String[]{p.name(), refreshed.name()};
                    }
                    if (nameChanged || !java.util.Objects.equals(
                            refreshed.skinUrl().orElse(null),
                            p.skinUrl().orElse(null))) {
                        changed = true;
                    }
                    updated.add(refreshed);
                } else {
                    updated.add(p);
                }
            }

            if (changed) {
                profileService.saveProfiles(updated);
                final String[] finalNameChange = nameChange;
                javafx.application.Platform.runLater(() -> {
                    var items = accountCombo.getItems();
                    String selectedUuid = selectedProfile != null
                            ? selectedProfile.uuid().orElse(null) : null;

                    for (int i = 0; i < items.size(); i++) {
                        GameProfile old = items.get(i);
                        for (GameProfile up : updated) {
                            if (old.uuid().isPresent() && up.uuid().isPresent()
                                    && old.uuid().get().equals(up.uuid().get())
                                    && !old.name().equals(up.name())) {
                                System.out.println("[ELY] combo item " + i + ": "
                                        + old.name() + " -> " + up.name());
                                items.set(i, up);
                                break;
                            }
                        }
                    }

                    if (selectedUuid != null) {
                        for (int i = 0; i < items.size(); i++) {
                            GameProfile p = items.get(i);
                            if (p.uuid().isPresent()
                                    && p.uuid().get().equals(selectedUuid)) {
                                suppressSelectionListener = true;
                                accountCombo.getSelectionModel().clearSelection();
                                accountCombo.getSelectionModel().select(i);
                                selectedProfile = p;
                                accountCombo.setValue(p);
                                suppressSelectionListener = false;
                                System.out.println("[ELY] re-selected: " + p.name());
                                break;
                            }
                        }
                    }

                    if (selectedProfile != null) {
                        updateAvatar(selectedProfile);
                    }
                    if (finalNameChange != null) {
                        statusLabel.setText("Nick changed: "
                                + finalNameChange[0] + " -> " + finalNameChange[1]);
                    }
                });
            }
        } catch (Exception e) {
            System.out.println("[ELY] timer exception: " + e);
        }
    }

    private void onVersionsLoaded(VersionManifest manifest) {
        manifestVersions = manifest.versions();
        versionsLoadFailed = false;
        if (versionListView != null) {
            versionListView.setPlaceholder(new Label("No versions match."));
        }
        buildVersionEntries();
        applyVersionFilter();
        statusLabel.setText(versionEntries.size()
                + " versions available (vanilla + modded variants)");
        refreshModdedProfiles(null);
    }

    private void onLoadFailed(Throwable cause) {
        versionsLoadFailed = true;
        statusLabel.setText("Version list load failed ("
                + cause.getMessage() + ")");
        if (versionListView != null) {
            Hyperlink retry = new Hyperlink(
                    "Couldn't load versions — click to retry");
            retry.setOnAction(e -> {
                statusLabel.setText("Retrying version list...");
                loadVersions();
            });
            versionListView.setPlaceholder(retry);
            versionListView.getItems().clear();
        }
    }

    /** Whether the version's metadata requires Java 8 or older. */
    private boolean needsJava8(VersionMetadata meta) {
        int required = meta.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(8);
        return required <= 8;
    }

    private void saveLastSelectedAccount(String accountName) {
        if (preferences == null) return;
        try {
            preferences.setLastSelectedAccount(accountName);
        } catch (java.io.IOException e) {
            // Non-fatal
        }
    }

    private final java.util.concurrent.atomic.AtomicLong avatarRequestId =
            new java.util.concurrent.atomic.AtomicLong();

    private void updateAvatar(GameProfile profile) {
        long requestId = avatarRequestId.incrementAndGet();
        if (profile == null || profile.skinUrl().isEmpty()) {
            avatarView.setVisible(false);
            return;
        }
        skinService.loadAvatarAsync(profile, 32)
                .thenAccept(optImg -> javafx.application.Platform.runLater(() -> {
                    if (requestId != avatarRequestId.get()) {
                        return;
                    }
                    if (optImg.isPresent()) {
                        avatarView.setImage(optImg.get());
                        avatarView.setVisible(true);
                    } else if (avatarView.getImage() == null) {
                        avatarView.setVisible(false);
                    }
                }));
    }

    // ------------------------------------------------------------------
    //  Account management
    // ------------------------------------------------------------------

    private void refreshAccounts() {
        try {
            var profiles = profileService.loadProfiles();
            suppressSelectionListener = true;
            try {
                accountCombo.getItems().setAll(profiles);

                String selectedUuid = selectedProfile != null
                        ? selectedProfile.uuid().orElse(null) : null;
                String savedName = null;
                if (preferences != null && selectedUuid == null) {
                    savedName = preferences.getLastSelectedAccount().orElse(null);
                }

                GameProfile toSelect = null;
                if (selectedUuid != null) {
                    for (GameProfile p : profiles) {
                        if (p.uuid().isPresent() && p.uuid().get().equals(selectedUuid)) {
                            toSelect = p;
                            break;
                        }
                    }
                }
                if (toSelect == null && savedName != null) {
                    for (GameProfile p : profiles) {
                        if (p.name().equals(savedName)) {
                            toSelect = p;
                            break;
                        }
                    }
                }
                if (toSelect == null && !profiles.isEmpty()) {
                    toSelect = profiles.get(0);
                }

                if (toSelect != null) {
                    accountCombo.getSelectionModel().clearSelection();
                    accountCombo.getSelectionModel().select(toSelect);
                    selectedProfile = toSelect;
                }
            } finally {
                suppressSelectionListener = false;
            }
        } catch (java.io.IOException e) {
            suppressSelectionListener = false;
        }
        if (selectedProfile != null) {
            updateAvatar(selectedProfile);
        }
    }

    private void onCreateAccount() {
        Stage launcherStage = (Stage) root.getScene().getWindow();
        String name = CreateAccountDialog.showDialog(launcherStage);
        if (name == null || name.isBlank()) return;

        try {
            profileService.addOfflineProfile(name);
            refreshAccounts();
            for (GameProfile p : accountCombo.getItems()) {
                if (p.name().equals(name)) {
                    accountCombo.getSelectionModel().select(p);
                    selectedProfile = p;
                    saveLastSelectedAccount(name);
                    break;
                }
            }
            statusLabel.setText("Account '" + name + "' created");
        } catch (java.io.IOException e) {
            ErrorDialog.show(launcherStage, "Account Error",
                    "Failed to create account: " + e.getMessage());
        }
    }

    private void onElyLogin() {
        Stage launcherStage = (Stage) root.getScene().getWindow();
        String[] creds = ElyLoginDialog.showDialog(launcherStage);
        if (creds == null) return;

        statusLabel.setText("Authenticating with Ely.by...");
        elyLoginButton.setDisable(true);

        Task<GameProfile> authTask = new Task<>() {
            @Override
            protected GameProfile call() throws Exception {
                return elyAuthService.authenticate(creds[0], creds[1]);
            }
        };
        authTask.setOnSucceeded(e -> {
            GameProfile profile = authTask.getValue();
            try {
                var profiles = new java.util.ArrayList<>(profileService.loadProfiles());
                profiles.removeIf(p -> (profile.uuid().isPresent() && p.uuid().isPresent()
                        && p.uuid().get().equals(profile.uuid().get()))
                        || p.name().equals(profile.name()));
                profiles.add(profile);
                profileService.saveProfiles(profiles);
            } catch (java.io.IOException ex) {
                // Non-fatal
            }
            refreshAccounts();
            for (GameProfile p : accountCombo.getItems()) {
                if (profile.uuid().isPresent() && p.uuid().isPresent()
                        && p.uuid().get().equals(profile.uuid().get())) {
                    suppressSelectionListener = true;
                    accountCombo.getSelectionModel().select(p);
                    selectedProfile = p;
                    saveLastSelectedAccount(profile.name());
                    suppressSelectionListener = false;
                    break;
                }
            }
            String skinInfo = profile.skinUrl().isPresent()
                    ? " (skin: " + profile.skinModel().orElse("classic") + ")"
                    : " (no skin)";
            statusLabel.setText("Ely.by login: " + profile.name() + skinInfo);
            elyLoginButton.setDisable(false);
        });
        authTask.setOnFailed(e -> {
            String msg = authTask.getException().getMessage();
            statusLabel.setText("Ely.by login failed: " + msg);
            elyLoginButton.setDisable(false);
            ErrorDialog.show(launcherStage, "Ely.by Login Failed", msg);
        });
        var thread = new Thread(authTask, "ely-auth");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Details panel
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Instance creation
    // ------------------------------------------------------------------

    /**
     * Opens the New Instance dialog — one simple screen with loader
     * chips, the version list and the loader version list — and
     * creates the instance on confirmation. The version browser's
     * double-click offers a shortcut with a fixed version/loader.
     */
    private void onNewInstance() {
        if (manifestVersions.isEmpty()) {
            if (versionsLoadFailed) {
                statusLabel.setText("Retrying version list...");
                loadVersions();
            } else {
                statusLabel.setText("Versions are still loading — please wait...");
            }
            return;
        }
        NewInstanceDialog.Result result = NewInstanceDialog.show(
                (Stage) root.getScene().getWindow(),
                modLoaderRegistry, manifestVersions);
        if (result != null) {
            createInstance(result);
        }
    }

    /**
     * Creates the instance in one background task: installs the
     * game/loader version only when it is not already installed
     * (skip-if-valid: intact files are not re-downloaded; no network
     * access at all for already-present versions) and registers the
     * instance with its own game directory.
     */
    private void createInstance(NewInstanceDialog.Result result) {
        createInstance(result, false);
    }

    /**
     * @param playWhenReady launch the instance right after creating
     *                      it (playing straight from the version
     *                      browser)
     */
    private void createInstance(NewInstanceDialog.Result result,
                                boolean playWhenReady) {
        ModLoaderType type = result.type();
        MinecraftVersion mc = result.mcVersion();
        ModLoaderVersion loader = result.loader();
        GameDirectory storage = GameDirectory.defaultDirectory();

        statusLabel.setText("Creating instance ("
                + (type == ModLoaderType.VANILLA ? "vanilla " + mc.id()
                        : type.displayName() + " " + loader.loaderVersion()
                                + " for MC " + mc.id()) + ")...");

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle("Creating Instance");
        progressDialog.show();

        Task<ModdedProfile> task = new Task<>() {
            @Override
            protected ModdedProfile call() throws Exception {
                String versionId = type == ModLoaderType.VANILLA
                        ? mc.id() : loader.installedVersionId();
                // Already-installed versions need no metadata fetch or
                // download — instance creation works offline then
                boolean alreadyInstalled = type == ModLoaderType.VANILLA
                        ? Files.isRegularFile(storage.clientJar(mc.id()))
                        : Files.isRegularFile(storage.versionMetadata(versionId));
                if (!alreadyInstalled) {
                    VersionMetadata vanillaMetadata =
                            metadataService.fetchMetadata(mc);
                    if (type == ModLoaderType.VANILLA) {
                        installationService.install(mc, vanillaMetadata,
                                storage, progressDialog);
                    } else {
                        var entry = modLoaderRegistry.get(type).orElseThrow(
                                () -> new IOException(type.displayName()
                                        + " support is not registered"));
                        entry.installer().install(mc, vanillaMetadata, loader,
                                storage, progressDialog);
                    }
                }
                return moddedProfileService.createProfile(
                        result.name(), type,
                        type == ModLoaderType.VANILLA ? "" : loader.loaderVersion(),
                        mc.id(), versionId, result.extraJvmArgs());
            }
        };
        task.setOnSucceeded(e -> {
            ModdedProfile profile = task.getValue();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 0, 0,
                    List.of()));
            refreshModdedProfiles(profile.id());
            statusLabel.setText("Instance ready: " + profile.name()
                    + " — press Play"
                    + (profile.isVanilla() ? "" : " and add mods via Folder"));
            if (playWhenReady) {
                startInstanceLaunch(profile);
            }
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 1, 0, List.of()));
            statusLabel.setText("Instance creation failed: " + cause.getMessage());
            ErrorDialog.show(owner, "Instance Creation Failed",
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        });
        var thread = new Thread(task, "instance-create");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Instances: listing, launch, folder, deletion
    // ------------------------------------------------------------------

    /**
     * Reloads the instance list into the sidebar panel.
     *
     * @param selectId instance id to select after loading, or null
     */
    private void refreshModdedProfiles(String selectId) {
        try {
            moddedProfiles = moddedProfileService.loadProfiles();
            profileListView.getItems().setAll(moddedProfiles);
            if (selectId != null) {
                for (ModdedProfile p : moddedProfiles) {
                    if (p.id().equals(selectId)) {
                        profileListView.getSelectionModel().select(p);
                        break;
                    }
                }
            }
            updateProfileButtons();
            updateInstanceDetails();
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to load instances: " + e.getMessage());
        }
    }

    /**
     * Opens the selected profile's game directory in the system file
     * manager, so the user can add mods, resource packs, shader packs,
     * worlds or check logs manually.
     */
    private void onOpenProfileFolder() {
        ModdedProfile profile = profileListView.getSelectionModel().getSelectedItem();
        if (profile == null) return;
        try {
            Path dir = moddedProfileService.resolveGameDir(profile);
            ModdedProfileService.ensureProfileFolders(dir);
            java.awt.Desktop.getDesktop().open(dir.toFile());
            statusLabel.setText("Opened " + dir);
        } catch (Exception ex) {
            statusLabel.setText("Failed to open folder: " + ex.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Open Folder",
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Opens the edit dialog for the selected profile (display name and
     * extra JVM arguments) and persists the changes. The game directory
     * and versions stay fixed.
     */
    private void onEditProfile() {
        ModdedProfile profile = profileListView.getSelectionModel().getSelectedItem();
        if (profile == null) return;

        EditProfileDialog.Result result = EditProfileDialog.show(
                (Stage) root.getScene().getWindow(), profile);
        if (result == null) return;

        try {
            Optional<ModdedProfile> updated = moddedProfileService.updateProfile(
                    profile.id(), result.name(), result.extraJvmArgs());
            if (updated.isPresent()) {
                refreshModdedProfiles(profile.id());
                statusLabel.setText("Instance updated: " + updated.get().name()
                        + (result.extraJvmArgs().isEmpty() ? ""
                                : " (" + result.extraJvmArgs().size()
                                + " JVM args)"));
            }
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to update instance: " + e.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Update Instance",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void onDeleteProfile() {
        ModdedProfile profile = profileListView.getSelectionModel().getSelectedItem();
        if (profile == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Instance");
        alert.setHeaderText("Delete instance '" + profile.name() + "'?");
        alert.setContentText(
                "The instance is removed from the list, but the game directory with "
                        + "your mods, configs and saves is kept on disk:\n"
                        + GameDirectory.defaultDirectory().root()
                                .resolve(profile.gameDirPath()));
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    var keptDir = moddedProfileService.deleteProfile(profile.id());
                    refreshModdedProfiles(null);
                    statusLabel.setText("Instance deleted"
                            + keptDir.map(d -> "; files kept at " + d).orElse(""));
                } catch (java.io.IOException e) {
                    statusLabel.setText("Failed to delete instance: " + e.getMessage());
                }
            }
        });
    }

    // --- Instance launch (verify → repair if needed → launch) ---

    private void onPlayProfile() {
        ModdedProfile profile = profileListView.getSelectionModel().getSelectedItem();
        if (profile == null) return;

        playProfileButton.setDisable(true);
        startInstanceLaunch(profile);
    }

    /**
     * Common launch entry for both Play buttons (instance panel and
     * version browser): account resolution, Ely.by refresh and then
     * the verify → repair-if-needed → launch chain.
     */
    private void startInstanceLaunch(ModdedProfile profile) {
        GameProfile account = getOrCreateProfile();
        GameDirectory storage = GameDirectory.defaultDirectory();

        // A launch attempt blocks both Play buttons until a terminal
        // callback (launch end/failure, game exit) refreshes them
        versionPlayInFlight = true;
        updateVersionActionBar();
        javaDownloadAttempted = false;
        statusLabel.setText("Verifying " + profile.name() + "...");

        if (account.isElyBy()) {
            statusLabel.setText("Refreshing Ely.by profile...");
            Task<GameProfile> refreshTask = new Task<>() {
                @Override
                protected GameProfile call() throws Exception {
                    return elyAuthService.refreshProfile(account);
                }
            };
            refreshTask.setOnSucceeded(e ->
                    verifyProfileAndLaunch(profile, refreshTask.getValue(), storage));
            refreshTask.setOnFailed(e ->
                    verifyProfileAndLaunch(profile, account, storage));
            var thread = new Thread(refreshTask, "ely-refresh");
            thread.setDaemon(true);
            thread.start();
        } else {
            verifyProfileAndLaunch(profile, account, storage);
        }
    }

    /**
     * Verifies the profile's installation; on problems, attempts an
     * automatic repair (re-downloading only missing or corrupt files)
     * and re-verifies before launching. Non-repairable problems are
     * reported with their causes.
     */
    private void verifyProfileAndLaunch(ModdedProfile profile,
                                        GameProfile account,
                                        GameDirectory storage) {
        Task<ModdedProfileVerificationService.VerificationReport> verifyTask = new Task<>() {
            @Override
            protected ModdedProfileVerificationService.VerificationReport call() throws Exception {
                return profileVerificationService.verify(profile, storage);
            }
        };
        verifyTask.setOnSucceeded(e -> {
            ModdedProfileVerificationService.VerificationReport report =
                    verifyTask.getValue();
            if (report.ok()) {
                doLaunchProfile(profile, account, report.metadata().orElseThrow(),
                        storage);
            } else {
                handleInstanceVerificationFailure(profile, account, storage,
                        report, false);
            }
        });
        verifyTask.setOnFailed(e -> {
            updateProfileButtons();
            statusLabel.setText("Verification error: "
                    + verifyTask.getException().getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Instance Verification Error",
                    verifyTask.getException().getClass().getSimpleName() + ": "
                            + verifyTask.getException().getMessage());
        });
        var thread = new Thread(verifyTask, "instance-verify");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Routes a failed verification to the right recovery path:
     * automatic Java 8 download for legacy versions that only lack a
     * runtime, a single automatic repair (re-downloading missing or
     * corrupt files), or a diagnostic report with the causes.
     *
     * @param repairAttempted whether a repair already ran for this
     *                        launch attempt (prevents repair loops)
     */
    private void handleInstanceVerificationFailure(
            ModdedProfile profile,
            GameProfile account,
            GameDirectory storage,
            ModdedProfileVerificationService.VerificationReport report,
            boolean repairAttempted) {
        // Legacy versions: everything is fine except the Java runtime
        if (!javaDownloadAttempted
                && report.metadata().isPresent()
                && needsJava8(report.metadata().get())
                && report.errors().stream().allMatch(e ->
                        e.startsWith("No suitable Java runtime"))) {
            javaDownloadAttempted = true;
            downloadAndInstallJava8ForInstance(profile, account, storage,
                    report.metadata().get());
            return;
        }
        // Automatic repair: reinstall vanilla/loader — intact files
        // are skipped, only missing/corrupt ones are downloaded — then
        // verify again
        if (report.isRepairableByInstall() && !repairAttempted) {
            repairProfileAndLaunch(profile, account, storage, report);
            return;
        }
        showProfileVerificationErrors(profile, report);
    }

    private void repairProfileAndLaunch(ModdedProfile profile,
                                        GameProfile account,
                                        GameDirectory storage,
                                        ModdedProfileVerificationService.VerificationReport original) {
        statusLabel.setText("Repairing " + profile.name()
                + " (downloading missing files)...");

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle("Repairing " + profile.name());
        progressDialog.show();

        Task<ModdedProfileVerificationService.VerificationReport> repairTask = new Task<>() {
            @Override
            protected ModdedProfileVerificationService.VerificationReport call() throws Exception {
                profileVerificationService.repair(profile, storage, progressDialog);
                return profileVerificationService.verify(profile, storage);
            }
        };
        repairTask.setOnSucceeded(e -> {
            ModdedProfileVerificationService.VerificationReport report =
                    repairTask.getValue();
            if (report.ok()) {
                doLaunchProfile(profile, account, report.metadata().orElseThrow(),
                        storage);
            } else {
                handleInstanceVerificationFailure(profile, account, storage,
                        report, true);
            }
        });
        repairTask.setOnFailed(e -> {
            updateProfileButtons();
            Throwable cause = repairTask.getException();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 1, 0, List.of()));
            statusLabel.setText("Repair failed: " + cause.getMessage());
            StringBuilder msg = new StringBuilder();
            msg.append("Automatic repair failed:\n")
                    .append(cause.getClass().getSimpleName()).append(": ")
                    .append(cause.getMessage()).append("\n\n")
                    .append("Problems found before repair:\n");
            for (String error : original.errors()) {
                msg.append(" - ").append(error).append('\n');
            }
            ErrorDialog.show(owner, "Instance Repair Failed — " + profile.name(),
                    msg.toString());
        });
        var thread = new Thread(repairTask, "instance-repair");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Reports verification problems with their causes and re-enables
     * the instance controls.
     */
    private void showProfileVerificationErrors(
            ModdedProfile profile,
            ModdedProfileVerificationService.VerificationReport report) {
        updateProfileButtons();
        statusLabel.setText("Instance not launchable: "
                + report.errors().get(0));

        StringBuilder msg = new StringBuilder(
                "The instance cannot be launched.\n\nProblems found:\n");
        for (String error : report.errors()) {
            msg.append(" - ").append(error).append('\n');
        }
        if (!report.warnings().isEmpty()) {
            msg.append("\nWarnings:\n");
            for (String warning : report.warnings()) {
                msg.append(" - ").append(warning).append('\n');
            }
        }
        ErrorDialog.show((Stage) root.getScene().getWindow(),
                "Instance Verification Failed — " + profile.name(),
                msg.toString());
    }

    private void doLaunchProfile(ModdedProfile profile,
                                 GameProfile account,
                                 VersionMetadata metadata,
                                 GameDirectory storage) {
        statusLabel.setText("Launching " + profile.name() + "...");
        Path runtimeDir = storage.root().resolve(profile.gameDirPath());

        Task<LaunchResult> launchTask = new Task<>() {
            @Override
            protected LaunchResult call() throws Exception {
                // The profile's game directory must exist before the
                // process can run inside it
                ModdedProfileService.ensureProfileFolders(runtimeDir);
                return launchService.launch(metadata, storage, account,
                        runtimeDir, profile.extraJvmArgs());
            }
        };
        launchTask.setOnSucceeded(e -> {
            LaunchResult result = launchTask.getValue();
            if (result.isSuccess()) {
                try {
                    moddedProfileService.touchLastPlayed(profile.id());
                } catch (java.io.IOException ignored) {
                    // Non-fatal
                }
                statusLabel.setText("Minecraft " + profile.versionId()
                        + " is running (" + profile.name() + ")");
                Stage launcherStage = (Stage) root.getScene().getWindow();
                launcherStage.hide();
                monitorProcess(result.process().orElseThrow(),
                        profile.versionId(), launcherStage);
            } else if (result.status() == LaunchResult.Status.FILE_CHECK_FAILED) {
                // Rare race (files vanished between verify and launch)
                ModdedProfileVerificationService.VerificationReport report =
                        new ModdedProfileVerificationService.VerificationReport(
                                false, List.of(result.message()), List.of(),
                                java.util.Optional.of(metadata));
                repairProfileAndLaunch(profile, account, storage, report);
            } else {
                updateProfileButtons();
                String msg = "Launch failed: " + result.message();
                statusLabel.setText(msg);
                Stage launcherStage = (Stage) root.getScene().getWindow();
                ErrorDialog.show(launcherStage, "Launch Failed",
                        result.status() + ": " + result.message());
            }
        });
        launchTask.setOnFailed(e -> {
            updateProfileButtons();
            String msg = "Launch error: " + launchTask.getException().getMessage();
            statusLabel.setText(msg);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage, "Launch Error",
                    launchTask.getException().getClass().getSimpleName() + ": "
                            + launchTask.getException().getMessage());
        });
        var thread = new Thread(launchTask, "profile-launch");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Java 8 auto-download (legacy instances)
    // ------------------------------------------------------------------

    /**
     * Downloads and installs a managed JRE 8 for a legacy instance
     * whose only problem is the missing runtime, then re-verifies and
     * launches.
     */
    private void downloadAndInstallJava8ForInstance(ModdedProfile profile,
                                                    GameProfile account,
                                                    GameDirectory storage,
                                                    VersionMetadata meta) {
        statusLabel.setText("Java 8 required for " + profile.name()
                + ". Downloading JRE 8...");

        Task<JavaRuntime> installTask = new Task<>() {
            @Override
            protected JavaRuntime call() throws Exception {
                Path targetDir = storage.javaRuntimeDir("jre-legacy");
                return javaRuntimeInstaller.install(8, targetDir);
            }
        };
        installTask.setOnSucceeded(e -> {
            JavaRuntime rt = installTask.getValue();
            statusLabel.setText("Java 8 installed. Verifying " + profile.name()
                    + " again...");

            if (javaResolutionService instanceof DefaultJavaResolutionService svc) {
                svc.setCustomJavaPath(rt.javaExecutable());
            }
            verifyProfileAndLaunch(profile, account, storage);
        });
        installTask.setOnFailed(e -> {
            updateProfileButtons();
            String msg = "Java 8 download failed: "
                    + installTask.getException().getMessage();
            statusLabel.setText(msg);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage, "Java Download Failed",
                    installTask.getException().getClass().getSimpleName() + ": "
                            + installTask.getException().getMessage()
                            + "\n\nPlease install Java 8 manually from adoptium.net"
                            + " and restart the launcher.");
        });
        var thread = new Thread(installTask, "java-install");
        thread.setDaemon(true);
        thread.start();
    }

    private GameProfile getOrCreateProfile() {
        if (selectedProfile != null) {
            return selectedProfile;
        }
        try {
            var profiles = profileService.loadProfiles();
            if (!profiles.isEmpty()) {
                selectedProfile = profiles.get(0);
                return selectedProfile;
            }
        } catch (java.io.IOException e) {
            // Fall through to create default
        }
        GameProfile defaultProfile = GameProfile.offline("Player");
        try {
            profileService.addOfflineProfile("Player");
        } catch (java.io.IOException e) {
            // Non-fatal — proceed with in-memory profile
        }
        selectedProfile = defaultProfile;
        return defaultProfile;
    }

    private void monitorProcess(MinecraftProcess process, String versionId,
                                 Stage launcherStage) {
        Thread monitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                javafx.application.Platform.runLater(() -> {
                    launcherStage.show();
                    if (exitCode == 0) {
                        statusLabel.setText("Minecraft " + versionId + " exited cleanly");
                    } else {
                        statusLabel.setText("Minecraft " + versionId
                                + " crashed (exit code " + exitCode + ")");
                        String err = process.stderr();
                        if (err.isBlank()) {
                            err = process.stdout();
                        }
                        if (!err.isBlank()) {
                            ErrorDialog.show(launcherStage,
                                    "Minecraft Crashed",
                                    "Exit code: " + exitCode + "\n\n"
                                            + err.lines().limit(30)
                                                    .reduce("", (a, b) -> a + b + "\n"));
                        }
                    }
                    updateProfileButtons();
                });
            } catch (InterruptedException e) {
                javafx.application.Platform.runLater(() -> {
                    launcherStage.show();
                    statusLabel.setText("Process monitoring interrupted");
                    updateProfileButtons();
                });
            }
        }, "mc-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    // ------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------
}
