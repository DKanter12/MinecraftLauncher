package org.example.launcher.ui;

import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import org.example.launcher.distribution.RemoteBuildService;
import org.example.launcher.distribution.ServerAuthService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.api.AdminLauncherServerApi;
import org.example.launcher.distribution.api.OfflineLauncherServerApi;
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
import org.example.launcher.service.BuildService;
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

    private static final String APP_VERSION = "v1.0";

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

    // Navigation rail + views
    private enum View {
        INSTANCES, VERSIONS, PROFILES, SETTINGS
    }

    private View currentView = View.INSTANCES;
    private final List<Button> navButtons = new ArrayList<>();
    private StackPane viewStack;
    private Label viewTitleLabel;
    private TextField instanceSearchField;

    // Instances view (cards grid)
    private FlowPane instanceCards;
    private ScrollPane instancesScroll;
    private HBox instanceFilterChips;
    private ToggleGroup instanceFilterGroup;
    private String selectedInstanceId;
    private String instanceSearchText = "";
    private ModLoaderType instanceLoaderFilter;

    // Other views
    private VBox instancesView;
    private VBox versionsView;
    private VBox profilesView;
    private VBox profilesBox;
    private VBox settingsView;
    private TextField javaPathField;
    private Label serverSessionLabel;
    private Label footerVersionLabel;

    // Version browser (center): all versions incl. modded variants
    private TextField versionSearchField;
    private FlowPane versionFilterChips;
    private Label versionBrowserTitle;
    private ListView<VersionEntry> versionListView;
    private List<VersionEntry> versionEntries = List.of();

    // Version browser: actions for the selected entry
    private Label selectedVersionLabel;
    private Button loaderVersionButton;
    private ComboBox<ModdedProfile> instanceChooser;
    private Button playVersionButton;
    private Button folderVersionButton;
    private boolean versionPlayInFlight = false;

    // Loader versions for the selected modded entry (newest first)
    private ModLoaderVersion selectedLoaderVersion;
    private List<ModLoaderVersion> selectedLoaderVersions = List.of();
    private ModLoaderType loaderVersionsFamily;
    private String loaderVersionsMcId;
    private boolean loaderVersionChanged = false;

    // Instances (cards grid)
    private List<ModdedProfile> moddedProfiles = List.of();

    // Builds (mods/configs sets) of the selected instance
    private final BuildService buildService = new BuildService();

    // Launcher server: admin-distributed builds. Without a configured
    // server the offline API keeps the launcher fully local.
    private final AdminLauncherServerApi serverApi = new OfflineLauncherServerApi();
    private final ServerAuthService serverAuthService = new ServerAuthService(
            serverApi, GameDirectory.defaultDirectory().root()
                    .resolve(ServerAuthService.SESSION_FILE_NAME));
    private final RemoteBuildService remoteBuildService = new RemoteBuildService(serverApi);
    private ServerSession serverSession;
    private Button serverButton;

    // Instance launch state
    private boolean javaDownloadAttempted = false;

    // Account
    private ComboBox<GameProfile> accountCombo;
    private Button createAccountButton;
    private Button elyLoginButton;
    private ImageView avatarView;
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
        serverSession = serverAuthService.restoreSession().orElse(null);
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

        instancesView = buildInstancesView();
        versionsView = buildVersionBrowser();
        profilesView = buildProfilesView();
        settingsView = buildSettingsView();
        viewStack = new StackPane(instancesView, versionsView,
                profilesView, settingsView);

        root.setTop(buildTopBar());
        root.setLeft(buildNavRail());
        root.setCenter(viewStack);
        root.setBottom(buildFooter());
        showView(View.INSTANCES);
    }

    // --- Top bar ---

    private HBox buildTopBar() {
        viewTitleLabel = new Label("My Instances");
        viewTitleLabel.getStyleClass().add("view-title");

        instanceSearchField = new TextField();
        instanceSearchField.setPromptText("Search Instances...");
        instanceSearchField.getStyleClass().add("search-field");
        instanceSearchField.setPrefWidth(260);
        instanceSearchField.textProperty().addListener((obs, old, val) -> {
            instanceSearchText = val == null ? "" : val.trim().toLowerCase();
            refreshInstanceCards();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Account widget (quick switcher; full management lives in Profiles)
        avatarView = new ImageView();
        avatarView.setFitWidth(28);
        avatarView.setFitHeight(28);
        avatarView.setPreserveRatio(true);
        avatarView.setVisible(false);

        accountCombo = new ComboBox<>();
        accountCombo.setPrefWidth(180);
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
        // Right-click a row asks whether to delete that account
        accountCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(GameProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setOnContextMenuRequested(null);
                } else {
                    setText(item.name()
                            + (item.isElyBy() ? " [Ely.by]" : " [Offline]"));
                    setOnContextMenuRequested(e -> {
                        onDeleteAccount(item);
                        e.consume();
                    });
                }
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

        serverButton = new Button("Server");
        serverButton.getStyleClass().add("browse-java-button");
        serverButton.setStyle("-fx-font-size: 11px;");
        serverButton.setOnAction(e -> onServerLogin());
        updateServerButtonText();

        HBox accountBox = new HBox(8, avatarView, accountCombo,
                createAccountButton, elyLoginButton, serverButton);
        accountBox.setAlignment(Pos.CENTER);
        accountBox.getStyleClass().add("account-pill");

        HBox topBar = new HBox(16, viewTitleLabel, instanceSearchField,
                spacer, accountBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.getStyleClass().add("topbar");
        return topBar;
    }

    // --- Navigation rail ---

    private VBox buildNavRail() {
        VBox rail = new VBox(6);
        rail.setPrefWidth(190);
        rail.setPadding(new Insets(16, 10, 16, 10));
        rail.getStyleClass().add("nav-rail");
        addNavButton(rail, "My Instances", View.INSTANCES);
        addNavButton(rail, "Game Versions", View.VERSIONS);
        addNavButton(rail, "Profiles", View.PROFILES);
        addNavButton(rail, "Settings", View.SETTINGS);
        return rail;
    }

    private void addNavButton(VBox rail, String text, View view) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setUserData(view);
        button.setOnAction(e -> showView(view));
        navButtons.add(button);
        rail.getChildren().add(button);
    }

    // --- Footer (status left, launcher info right) ---

    private HBox buildFooter() {
        statusLabel = new Label("Loading versions...");
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        footerVersionLabel = new Label("Launcher " + APP_VERSION);
        footerVersionLabel.getStyleClass().add("footer-version");

        HBox footer = new HBox(12, statusLabel, footerVersionLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 20, 8, 20));
        footer.getStyleClass().add("footer");
        return footer;
    }

    // --- Instances view: filter chips + cards grid ---

    private VBox buildInstancesView() {
        instanceFilterChips = new HBox(8);
        instanceFilterChips.getStyleClass().add("filter-row");
        instanceFilterGroup = new ToggleGroup();
        addInstanceFilterChip("All", null);
        for (ModLoaderType loader : ModLoaderType.values()) {
            addInstanceFilterChip(loader.displayName(), loader);
        }

        instanceCards = new FlowPane();
        instanceCards.setHgap(14);
        instanceCards.setVgap(14);
        instanceCards.setPadding(new Insets(4));
        instanceCards.getStyleClass().add("cards-flow");

        instancesScroll = new ScrollPane(instanceCards);
        instancesScroll.setFitToWidth(true);
        instancesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        instancesScroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(instancesScroll, Priority.ALWAYS);

        VBox view = new VBox(10, instanceFilterChips, instancesScroll);
        view.setPadding(new Insets(16));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private void addInstanceFilterChip(String text, ModLoaderType loader) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("filter-button");
        chip.setToggleGroup(instanceFilterGroup);
        chip.setUserData(loader);
        chip.setSelected(loader == null);
        chip.setOnAction(e -> {
            // A single-choice filter never goes empty: clicking the
            // active chip again keeps it
            if (!chip.isSelected()) {
                chip.setSelected(true);
                return;
            }
            instanceLoaderFilter = (ModLoaderType) chip.getUserData();
            refreshInstanceCards();
        });
        instanceFilterChips.getChildren().add(chip);
    }

    /** Switches the center view and updates the title, search and nav. */
    private void showView(View view) {
        currentView = view;
        instancesView.setVisible(view == View.INSTANCES);
        instancesView.setManaged(view == View.INSTANCES);
        versionsView.setVisible(view == View.VERSIONS);
        versionsView.setManaged(view == View.VERSIONS);
        profilesView.setVisible(view == View.PROFILES);
        profilesView.setManaged(view == View.PROFILES);
        settingsView.setVisible(view == View.SETTINGS);
        settingsView.setManaged(view == View.SETTINGS);
        viewTitleLabel.setText(switch (view) {
            case INSTANCES -> "My Instances";
            case VERSIONS -> "Game Versions";
            case PROFILES -> "Profiles";
            case SETTINGS -> "Settings";
        });
        instanceSearchField.setVisible(view == View.INSTANCES);
        instanceSearchField.setManaged(view == View.INSTANCES);
        for (Button nav : navButtons) {
            boolean active = nav.getUserData() == view;
            if (active) {
                if (!nav.getStyleClass().contains("nav-button-active")) {
                    nav.getStyleClass().add("nav-button-active");
                }
            } else {
                nav.getStyleClass().remove("nav-button-active");
            }
        }
        if (view == View.INSTANCES) {
            refreshInstanceCards();
        } else if (view == View.PROFILES) {
            refreshProfilesView();
        } else if (view == View.SETTINGS) {
            refreshSettingsView();
        }
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

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** The selected instance (cards grid), or null. */
    private ModdedProfile selectedInstance() {
        if (selectedInstanceId == null) return null;
        for (ModdedProfile p : moddedProfiles) {
            if (p.id().equals(selectedInstanceId)) return p;
        }
        return null;
    }

    /**
     * Selects a card in place (no rebuild, so double-click to play
     * keeps working); full rebuilds happen after data changes.
     */
    /**
     * Jumps to a freshly created instance: reloads the list, moves
     * the loader filter to its family (so the card is visible even
     * when another family was filtered), clears the search, opens
     * the Instances view and expands the card.
     */
    private void jumpToInstance(ModdedProfile profile) {
        instanceLoaderFilter = profile.loaderType();
        syncInstanceFilterChips();
        if (!instanceSearchField.getText().isEmpty()) {
            instanceSearchField.clear();
        }
        showView(View.INSTANCES);
        refreshModdedProfiles(profile.id());
    }

    /** Reflects {@link #instanceLoaderFilter} in the filter chips. */
    private void syncInstanceFilterChips() {
        if (instanceFilterChips == null) return;
        for (var node : instanceFilterChips.getChildren()) {
            if (node instanceof ToggleButton chip) {
                Object family = chip.getUserData();
                chip.setSelected(family == null
                        ? instanceLoaderFilter == null
                        : family == instanceLoaderFilter);
            }
        }
    }

    private void selectInstance(String id) {
        if (id != null && id.equals(selectedInstanceId)) return;
        selectedInstanceId = id;
        if (instanceCards == null) return;
        for (javafx.scene.Node node : instanceCards.getChildren()) {
            Object key = node.getUserData();
            if (!(key instanceof String)) continue;
            boolean sel = key.equals(selectedInstanceId);
            if (sel) {
                if (!node.getStyleClass().contains("instance-card-selected")) {
                    node.getStyleClass().add("instance-card-selected");
                }
            } else {
                node.getStyleClass().remove("instance-card-selected");
            }
            Object details = node.getProperties().get("details");
            if (details instanceof Region region) {
                region.setVisible(sel);
                region.setManaged(sel);
            }
        }
    }

    /**
     * Re-enables every Play button after a launch attempt reaches a
     * terminal state (launched, failed, repaired or game exited).
     */
    private void refreshLaunchButtons() {
        versionPlayInFlight = false;
        updateVersionActionBar();
        refreshInstanceCards();
    }

    /** Rebuilds the cards grid from the current filter + search. */
    private void refreshInstanceCards() {
        if (instanceCards == null) return;
        double scroll = instancesScroll != null
                ? instancesScroll.getVvalue() : 0;
        instanceCards.getChildren().clear();
        List<ModdedProfile> shown = new ArrayList<>();
        for (ModdedProfile p : moddedProfiles) {
            if (instanceLoaderFilter != null
                    && p.loaderType() != instanceLoaderFilter) {
                continue;
            }
            if (!instanceSearchText.isEmpty()) {
                String haystack = (p.name() + " " + p.minecraftVersion()
                        + " " + p.loaderType().displayName()
                        + " " + p.versionId()).toLowerCase();
                if (!haystack.contains(instanceSearchText)) continue;
            }
            shown.add(p);
        }
        shown.sort(Comparator
                .comparing((ModdedProfile p) -> p.lastPlayedTime().orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(p -> p.name().toLowerCase()));
        for (ModdedProfile p : shown) {
            instanceCards.getChildren().add(buildInstanceCard(p));
        }
        instanceCards.getChildren().add(buildAddInstanceCard());
        if (scroll > 0 && instancesScroll != null) {
            double restore = scroll;
            Platform.runLater(() -> instancesScroll.setVvalue(restore));
        }
    }

    private VBox buildInstanceCard(ModdedProfile profile) {
        boolean selected = profile.id().equals(selectedInstanceId);
        VBox card = new VBox(8);
        card.setPrefWidth(300);
        card.setUserData(profile.id());
        card.getStyleClass().addAll("instance-card",
                accentClass(profile.loaderType()));
        if (selected) {
            card.getStyleClass().add("instance-card-selected");
        }

        Label icon = new Label(iconText(profile.loaderType()));
        icon.getStyleClass().addAll("instance-icon",
                accentClass(profile.loaderType()));

        Label name = new Label(profile.name());
        name.getStyleClass().add("instance-card-name");
        name.setWrapText(true);
        Label sub = new Label(cardSubLine(profile));
        sub.getStyleClass().add("instance-card-sub");
        Label played = new Label(cardPlayedLine(profile));
        played.getStyleClass().add("instance-card-sub");
        VBox titles = new VBox(2, name, sub, played);

        HBox top = new HBox(10, icon, titles);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titles, Priority.ALWAYS);
        card.getChildren().add(top);

        if (!profile.isVanilla()) {
            Label buildLine = new Label(activeBuildText(profile));
            buildLine.getStyleClass().add("instance-card-build");
            buildLine.setWrapText(true);
            card.getChildren().add(buildLine);
        }

        Button play = new Button("Play");
        play.getStyleClass().add("card-play");
        play.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(play, Priority.ALWAYS);
        play.setDisable(versionPlayInFlight);
        play.setOnAction(e -> {
            selectInstance(profile.id());
            startInstanceLaunch(profile);
        });

        Button folder = new Button("Folder");
        folder.getStyleClass().add("card-ghost-button");
        folder.setOnAction(e -> onOpenProfileFolder(profile));

        Button edit = new Button("Edit");
        edit.getStyleClass().add("card-ghost-button");
        edit.setOnAction(e -> onEditProfile(profile));

        Button more = new Button("...");
        more.getStyleClass().add("card-ghost-button");
        more.setOnAction(e -> showCardMenu(more, profile));

        HBox actions = new HBox(6, play, folder, edit, more);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(actions);

        VBox details = buildInstanceDetails(profile);
        details.setVisible(selected);
        details.setManaged(selected);
        card.getProperties().put("details", details);
        card.getChildren().add(details);

        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                selectInstance(profile.id());
                if (!versionPlayInFlight) {
                    startInstanceLaunch(profile);
                }
            } else {
                selectInstance(profile.id());
            }
        });
        return card;
    }

    /** Expanded block of the selected card: facts + builds + delete. */
    private VBox buildInstanceDetails(ModdedProfile profile) {
        VBox details = new VBox(3);
        details.getStyleClass().add("instance-card-details");
        details.getChildren().add(detailLine("MC " + profile.minecraftVersion()
                + (profile.isVanilla() ? " (vanilla)"
                        : "  ·  " + profile.loaderType().displayName()
                                + " " + profile.loaderVersion())));
        Path dir = moddedProfileService.resolveGameDir(profile);
        details.getChildren().add(detailLine("Game Dir:  .../"
                + shortGameDir(dir)));
        details.getChildren().add(detailLine("Memory:  "
                + ModdedProfileService.formatMemory(profile.memoryMb())));
        details.getChildren().add(detailLine("JVM Args:  "
                + (profile.extraJvmArgs().isEmpty()
                        ? "None" : String.join(" ", profile.extraJvmArgs()))));
        details.getChildren().add(detailLine("Created:  "
                + profile.createdTime().map(MainView::formatDate).orElse("—")));

        if (!profile.isVanilla()) {
            Button selectBuild = new Button("Select Build");
            selectBuild.getStyleClass().add("card-small-button");
            selectBuild.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(selectBuild, Priority.ALWAYS);
            selectBuild.setOnAction(e -> onSelectBuild(profile));
            Button saveBuild = new Button("Save Build");
            saveBuild.getStyleClass().add("card-small-button");
            saveBuild.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(saveBuild, Priority.ALWAYS);
            saveBuild.setOnAction(e -> onSaveBuild(profile));
            Button serverBuilds = new Button("Server Builds");
            serverBuilds.getStyleClass().add("card-small-button");
            serverBuilds.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(serverBuilds, Priority.ALWAYS);
            serverBuilds.setOnAction(e -> onServerBuilds(profile));
            HBox buildsRow = new HBox(6, selectBuild, saveBuild, serverBuilds);
            details.getChildren().add(buildsRow);
        }

        Button delete = new Button("Delete");
        delete.getStyleClass().add("card-danger-button");
        delete.setMaxWidth(Double.MAX_VALUE);
        delete.setOnAction(e -> onDeleteProfile(profile));
        details.getChildren().add(delete);
        return details;
    }

    private static Label detailLine(String text) {
        Label line = new Label(text);
        line.getStyleClass().add("instance-card-detail-line");
        line.setWrapText(true);
        return line;
    }

    private void showCardMenu(Button anchor, ModdedProfile profile) {
        ContextMenu menu = new ContextMenu();
        if (!profile.isVanilla()) {
            MenuItem selectBuild = new MenuItem("Select Build");
            selectBuild.setOnAction(e -> onSelectBuild(profile));
            MenuItem saveBuild = new MenuItem("Save Build");
            saveBuild.setOnAction(e -> onSaveBuild(profile));
            MenuItem serverBuilds = new MenuItem("Server Builds");
            serverBuilds.setOnAction(e -> onServerBuilds(profile));
            menu.getItems().addAll(selectBuild, saveBuild, serverBuilds);
        }
        MenuItem delete = new MenuItem("Delete Instance");
        delete.getStyleClass().add("menu-item-danger");
        delete.setOnAction(e -> onDeleteProfile(profile));
        menu.getItems().add(delete);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private VBox buildAddInstanceCard() {
        Label plus = new Label("+");
        plus.getStyleClass().add("add-instance-plus");
        Label text = new Label("Add New Instance");
        text.getStyleClass().add("add-instance-text");
        VBox card = new VBox(6, plus, text);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(300);
        card.setMinHeight(190);
        card.getStyleClass().add("add-instance-card");
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> onNewInstance());
        return card;
    }

    /** Accent style per loader family, like the reference mockup. */
    private static String accentClass(ModLoaderType type) {
        return switch (type) {
            case VANILLA -> "accent-vanilla";
            case FABRIC -> "accent-fabric";
            case FORGE -> "accent-forge";
            case NEOFORGE -> "accent-neoforge";
            case QUILT -> "accent-quilt";
        };
    }

    private static String iconText(ModLoaderType type) {
        return switch (type) {
            case VANILLA -> "V";
            case FABRIC -> "Fb";
            case FORGE -> "Fo";
            case NEOFORGE -> "N";
            case QUILT -> "Q";
        };
    }

    private String cardSubLine(ModdedProfile profile) {
        if (profile.isVanilla()) {
            return "MC " + profile.minecraftVersion();
        }
        int mods = modsCount(profile);
        return profile.loaderType().displayName() + " "
                + profile.minecraftVersion()
                + (mods < 0 ? "" : "  (" + mods + " Mods)");
    }

    private String cardPlayedLine(ModdedProfile profile) {
        if (profile.lastPlayedTime().isPresent()) {
            return "Last played: "
                    + relativeTime(profile.lastPlayedTime().get());
        }
        return profile.createdTime()
                .map(t -> "Created: " + relativeTime(t))
                .orElse("Never played");
    }

    /** Counts mod jars; negative when unreadable. */
    private int modsCount(ModdedProfile profile) {
        try {
            Path mods = moddedProfileService.resolveGameDir(profile)
                    .resolve("mods");
            if (!Files.isDirectory(mods)) {
                return profile.isVanilla() ? -1 : 0;
            }
            try (var stream = Files.list(mods)) {
                return (int) stream
                        .filter(f -> f.getFileName().toString()
                                .endsWith(".jar"))
                        .count();
            }
        } catch (IOException e) {
            return -1;
        }
    }

    private String activeBuildText(ModdedProfile profile) {
        Path dir = moddedProfileService.resolveGameDir(profile);
        String active = buildService.selectedBuild(dir).orElse(null);
        return active == null ? "No build selected"
                : "Active build: " + active;
    }

    private static String shortGameDir(Path dir) {
        Path root = GameDirectory.defaultDirectory().root();
        try {
            return root.relativize(dir).toString();
        } catch (IllegalArgumentException e) {
            return dir.toString();
        }
    }

    private static String relativeTime(OffsetDateTime time) {
        Duration age = Duration.between(time, OffsetDateTime.now());
        if (age.isNegative()) {
            age = Duration.ZERO;
        }
        long minutes = age.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }
        long hours = age.toHours();
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = age.toDays();
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        long weeks = days / 7;
        if (weeks < 5) {
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }
        return formatDate(time);
    }

    private static String formatDate(OffsetDateTime time) {
        return DATE_FORMATTER.format(time);
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

        // -- Actions for the selected version: pick the instance to
        //    play (named automatically after the version), open its
        //    directory, and for modded entries pick the loader
        //    version (newest by default, changeable). Mods sets are
        //    managed as builds in the instance panel --
        selectedVersionLabel = new Label("Select a version to play");
        selectedVersionLabel.getStyleClass().add("details-info");

        loaderVersionButton = new Button("Loader version");
        loaderVersionButton.getStyleClass().add("quick-select-button");
        loaderVersionButton.setOnAction(e -> onChangeLoaderVersion());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox versionActionTop = new HBox(8, selectedVersionLabel,
                actionSpacer, loaderVersionButton);
        versionActionTop.setAlignment(Pos.CENTER_LEFT);

        instanceChooser = new ComboBox<>();
        instanceChooser.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(instanceChooser, Priority.ALWAYS);
        instanceChooser.setPromptText("Not installed yet — Play creates it");
        instanceChooser.setConverter(new StringConverter<>() {
            @Override
            public String toString(ModdedProfile profile) {
                if (profile == null) {
                    return "";
                }
                return profile.name() + " · last played "
                        + profile.lastPlayedTime().map(TIME_FORMATTER::format)
                                .orElse("never");
            }

            @Override
            public ModdedProfile fromString(String string) {
                return null;
            }
        });
        // Choosing an instance also selects its card, so its
        // details (incl. builds) show up expanded
        instanceChooser.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> {
                    if (val != null) {
                        selectInstance(val.id());
                    }
                });

        playVersionButton = new Button("Play");
        playVersionButton.getStyleClass().add("play-button-compact");
        playVersionButton.setDisable(true);
        playVersionButton.setOnAction(e -> onPlaySelectedVersion());

        folderVersionButton = new Button("Folder");
        folderVersionButton.getStyleClass().add("quick-select-button");
        folderVersionButton.setDisable(true);
        folderVersionButton.setOnAction(e -> onOpenSelectedVersionFolder());

        HBox versionActionBar = new HBox(8, instanceChooser,
                playVersionButton, folderVersionButton);
        versionActionBar.setAlignment(Pos.CENTER_LEFT);

        VBox browser = new VBox(8, versionBrowserTitle, versionSearchField,
                versionFilterChips, versionListView, versionActionTop,
                versionActionBar, hint);
        browser.setPadding(new Insets(16));
        browser.getStyleClass().add("center-panel");
        versionsView = browser;
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
            updateInstanceChooser();
            updateVersionActionBar();
            return;
        }
        ModLoaderType family = entry.variant();
        String mcId = entry.version().id();
        if (family == loaderVersionsFamily
                && mcId.equals(loaderVersionsMcId)
                && !selectedLoaderVersions.isEmpty()) {
            selectedLoaderVersion = selectedLoaderVersions.get(0);
            updateInstanceChooser();
            updateVersionActionBar();
            return;
        }
        loaderVersionsFamily = family;
        loaderVersionsMcId = mcId;
        selectedLoaderVersions = List.of();
        updateInstanceChooser();
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
                updateInstanceChooser();
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
            // An instance with this loader version becomes the
            // preferred choice in the instance chooser
            updateInstanceChooser();
            updateVersionActionBar();
        }
    }

    /**
     * Plays the selected version: the instance chosen in the instance
     * chooser is launched (same principle as instance Play: verify →
     * repair if needed → launch; a selected build replaces the mods
     * and configs right before the game starts); with no instance
     * yet, it is created first (installing the version if needed) and
     * launched right after — with the newest loader version by
     * default, or the one picked via the loader version button.
     */
    private void onPlaySelectedVersion() {
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        if (entry == null) return;

        ModdedProfile chosen =
                instanceChooser.getSelectionModel().getSelectedItem();
        if (chosen != null) {
            selectInstance(chosen.id());
            startInstanceLaunch(chosen);
            return;
        }
        ModdedProfile match = findPlayableInstance(entry);
        if (match != null) {
            selectInstance(match.id());
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
                List.of(), "");
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
     * Existing instances of the selected browser entry: vanilla
     * entries match vanilla instances, modded entries match instances
     * of the same loader family (each with its own game directory).
     * Most recently played first.
     */
    private List<ModdedProfile> matchingInstances(VersionEntry entry) {
        String mcId = entry.version().id();
        List<ModdedProfile> matches = new ArrayList<>();
        for (ModdedProfile p : moddedProfiles) {
            if (!p.minecraftVersion().equals(mcId)) {
                continue;
            }
            if (entry.isVanilla() ? p.isVanilla()
                    : !p.isVanilla() && p.loaderType() == entry.variant()) {
                matches.add(p);
            }
        }
        matches.sort(Comparator
                .comparing((ModdedProfile p) -> p.lastPlayedTime().orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(p -> p.name().toLowerCase()));
        return matches;
    }

    /**
     * Syncs the instance chooser with the current browser selection
     * and the instance list. Preference: the instance with the chosen
     * loader version, then the previously chosen one, then the most
     * recently played.
     */
    private void updateInstanceChooser() {
        if (instanceChooser == null) return;
        VersionEntry entry =
                versionListView.getSelectionModel().getSelectedItem();
        List<ModdedProfile> matches =
                entry == null ? List.of() : matchingInstances(entry);
        ModdedProfile previous =
                instanceChooser.getSelectionModel().getSelectedItem();
        instanceChooser.getItems().setAll(matches);
        if (matches.isEmpty()) {
            instanceChooser.getSelectionModel().clearSelection();
            return;
        }
        ModdedProfile pick = null;
        if (entry != null && !entry.isVanilla()
                && selectedLoaderVersion != null) {
            for (ModdedProfile p : matches) {
                if (p.loaderVersion().equals(
                        selectedLoaderVersion.loaderVersion())) {
                    pick = p;
                    break;
                }
            }
        }
        if (pick == null && previous != null) {
            for (ModdedProfile p : matches) {
                if (p.id().equals(previous.id())) {
                    pick = p;
                    break;
                }
            }
        }
        if (pick == null) {
            pick = matches.get(0);
        }
        instanceChooser.getSelectionModel().select(pick);
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
            ModdedProfile chosen =
                    instanceChooser.getSelectionModel().getSelectedItem();
            ModdedProfile match = chosen != null ? chosen
                    : findPlayableInstance(entry);
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
    //  Profiles view: accounts management
    // ------------------------------------------------------------------

    private VBox buildProfilesView() {
        Button addOffline = new Button("+ Offline");
        addOffline.getStyleClass().add("quick-select-button");
        addOffline.setOnAction(e -> onCreateAccount());
        Button ely = new Button("Ely.by");
        ely.getStyleClass().add("quick-select-button");
        ely.setOnAction(e -> onElyLogin());
        Button server = new Button("Server");
        server.getStyleClass().add("quick-select-button");
        server.setOnAction(e -> {
            onServerLogin();
            refreshProfilesView();
        });
        HBox buttons = new HBox(8, addOffline, ely, server);
        buttons.setAlignment(Pos.CENTER_LEFT);

        profilesBox = new VBox(10);
        VBox.setVgrow(profilesBox, Priority.ALWAYS);

        Label hint = new Label("The active account launches the game. "
                + "Offline accounts need no password; Ely.by accounts "
                + "bring skins and capes.");
        hint.getStyleClass().add("quick-select-label");
        hint.setWrapText(true);

        VBox view = new VBox(12, buttons, profilesBox, hint);
        view.setPadding(new Insets(16));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private void refreshProfilesView() {
        if (profilesBox == null) return;
        profilesBox.getChildren().clear();
        List<GameProfile> accounts;
        try {
            accounts = profileService.loadProfiles();
        } catch (IOException e) {
            statusLabel.setText("Failed to load accounts: " + e.getMessage());
            return;
        }

        String serverText = serverSession == null
                ? "Launcher server: not signed in (local mode)"
                : "Launcher server: " + serverSession.accountName()
                        + (serverSession.isAdmin() ? " (administrator)"
                                : " (user)");
        Label serverLabel = new Label(serverText);
        serverLabel.getStyleClass().add("account-row-sub");
        HBox serverRow = new HBox(10, serverLabel);
        serverRow.setAlignment(Pos.CENTER_LEFT);
        serverRow.getStyleClass().add("account-row");
        profilesBox.getChildren().add(serverRow);

        for (GameProfile account : accounts) {
            boolean active = selectedProfile != null
                    && selectedProfile.equals(account);
            String initial = account.name().isEmpty() ? "?"
                    : account.name().substring(0, 1).toUpperCase();
            Label tile = new Label(initial);
            tile.getStyleClass().addAll("account-tile", account.isElyBy()
                    ? "account-tile-ely" : "account-tile-offline");
            Label name = new Label(account.name()
                    + (active ? "  (active)" : ""));
            name.getStyleClass().add("account-row-name");
            String sub = account.isElyBy() ? "Ely.by" : "Offline";
            if (account.uuid().isPresent()) {
                String uuid = account.uuid().get();
                sub += "  ·  " + uuid.substring(0, Math.min(8, uuid.length()));
            }
            Label subLabel = new Label(sub);
            subLabel.getStyleClass().add("account-row-sub");
            VBox texts = new VBox(2, name, subLabel);
            HBox.setHgrow(texts, Priority.ALWAYS);
            Button use = new Button("Use");
            use.getStyleClass().add("card-ghost-button");
            use.setDisable(active);
            use.setOnAction(e -> onUseAccount(account));
            Button delete = new Button("Delete");
            delete.getStyleClass().add("card-danger-button");
            delete.setOnAction(e -> onDeleteAccount(account));
            HBox row = new HBox(10, tile, texts, use, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("account-row");
            if (active) {
                row.getStyleClass().add("account-row-active");
            }
            profilesBox.getChildren().add(row);
        }
    }

    private void onUseAccount(GameProfile account) {
        selectedProfile = account;
        saveLastSelectedAccount(account.name());
        updateAvatar(account);
        refreshAccounts();
        refreshProfilesView();
        statusLabel.setText("Active account: " + account.name());
    }

    private void onDeleteAccount(GameProfile account) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Account");
        alert.setHeaderText("Delete account '" + account.name() + "'?");
        alert.setContentText("It is removed from the launcher. "
                + "Ely.by credentials stay valid on ely.by itself.");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    profileService.deleteProfile(account.name());
                    if (selectedProfile != null
                            && selectedProfile.equals(account)) {
                        selectedProfile = null;
                    }
                    refreshAccounts();
                    refreshProfilesView();
                    statusLabel.setText("Account deleted: " + account.name());
                } catch (IOException e) {
                    statusLabel.setText("Failed to delete account: "
                            + e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------------
    //  Settings view: folders, Java override, server session
    // ------------------------------------------------------------------

    private VBox buildSettingsView() {
        Label dirTitle = new Label("Game Directory");
        dirTitle.getStyleClass().add("section-title");
        Label dirPath = new Label(
                GameDirectory.defaultDirectory().root().toString());
        dirPath.getStyleClass().add("settings-value");
        dirPath.setWrapText(true);
        Button openDir = new Button("Open Folder");
        openDir.getStyleClass().add("quick-select-button");
        openDir.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(
                        GameDirectory.defaultDirectory().root().toFile());
            } catch (Exception ex) {
                statusLabel.setText("Failed to open folder: "
                        + ex.getMessage());
            }
        });
        VBox dirBox = new VBox(6, dirTitle, dirPath, openDir);
        dirBox.getStyleClass().add("settings-group");

        Label javaTitle = new Label("Java Executable");
        javaTitle.getStyleClass().add("section-title");
        javaPathField = new TextField();
        javaPathField.setPromptText("Auto-detect (recommended)");
        javaPathField.getStyleClass().add("search-field");
        javaPathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(javaPathField, Priority.ALWAYS);
        Button browse = new Button("Browse...");
        browse.getStyleClass().add("quick-select-button");
        browse.setOnAction(e -> onBrowseJava());
        Button apply = new Button("Apply");
        apply.getStyleClass().add("quick-select-button");
        apply.setOnAction(e -> onApplyJava());
        Button clear = new Button("Clear");
        clear.getStyleClass().add("quick-select-button");
        clear.setOnAction(e -> {
            javaPathField.clear();
            onApplyJava();
        });
        HBox javaRow = new HBox(8, javaPathField, browse, apply, clear);
        javaRow.setAlignment(Pos.CENTER_LEFT);
        Label javaHint = new Label("Overrides Java auto-detection until "
                + "the launcher restarts. Leave empty for auto-detect.");
        javaHint.getStyleClass().add("quick-select-label");
        javaHint.setWrapText(true);
        VBox javaBox = new VBox(6, javaTitle, javaRow, javaHint);
        javaBox.getStyleClass().add("settings-group");

        Label serverTitle = new Label("Launcher Server");
        serverTitle.getStyleClass().add("section-title");
        serverSessionLabel = new Label();
        serverSessionLabel.getStyleClass().add("settings-value");
        serverSessionLabel.setWrapText(true);
        Button serverButton = new Button("Sign In / Out");
        serverButton.getStyleClass().add("quick-select-button");
        serverButton.setOnAction(e -> {
            onServerLogin();
            refreshSettingsView();
        });
        VBox serverBox = new VBox(6, serverTitle, serverSessionLabel,
                serverButton);
        serverBox.getStyleClass().add("settings-group");

        Label aboutTitle = new Label("About");
        aboutTitle.getStyleClass().add("section-title");
        Label about = new Label("Minecraft Launcher " + APP_VERSION
                + "  ·  instances, local builds and server-distributed builds.");
        about.getStyleClass().add("settings-value");
        about.setWrapText(true);
        VBox aboutBox = new VBox(6, aboutTitle, about);
        aboutBox.getStyleClass().add("settings-group");

        VBox view = new VBox(12, dirBox, javaBox, serverBox, aboutBox);
        view.setPadding(new Insets(16));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private void refreshSettingsView() {
        if (serverSessionLabel == null) return;
        serverSessionLabel.setText(serverSession == null
                ? "Not signed in — the launcher runs in local mode."
                : "Signed in as " + serverSession.accountName()
                        + (serverSession.isAdmin() ? " (administrator)"
                                : " (user)"));
    }

    private void onBrowseJava() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Java Executable");
        java.io.File picked =
                chooser.showOpenDialog(root.getScene().getWindow());
        if (picked != null) {
            javaPathField.setText(picked.getAbsolutePath());
        }
    }

    private void onApplyJava() {
        String path = javaPathField.getText() == null ? ""
                : javaPathField.getText().trim();
        if (javaResolutionService instanceof DefaultJavaResolutionService svc) {
            svc.setCustomJavaPath(path.isEmpty() ? null : Path.of(path));
            statusLabel.setText(path.isEmpty()
                    ? "Java executable: auto-detect"
                    : "Java executable override: " + path);
        } else {
            statusLabel.setText(
                    "Custom Java path is not supported by the active resolver");
        }
    }

    /**
     * Opens the build picker for the selected modded instance: the
     * chosen build (or none) replaces the instance's mods/config
     * folders at launch.
     */
    private void onSelectBuild(ModdedProfile profile) {
        if (profile == null || profile.isVanilla()) return;
        Path dir = moddedProfileService.resolveGameDir(profile);
        String active = buildService.selectedBuild(dir).orElse(null);

        SelectBuildDialog.Result result = SelectBuildDialog.show(
                (Stage) root.getScene().getWindow(), profile.name(),
                active, buildService, dir);
        if (result == null) return; // cancelled

        try {
            buildService.selectBuild(dir, result.buildName());
            statusLabel.setText(result.buildName() == null
                    ? "Build cleared — " + profile.name()
                            + " launches with its current folders"
                    : "Build selected: " + result.buildName()
                            + " — applied at launch");
            refreshInstanceCards();
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to select build: " + e.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Select Build",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Saves the current mods/configs of the selected modded instance
     * as a new build: the user names it, chooses the contents (mods
     * only, or mods together with configs) and whether to activate it
     * right away — the checkbox is off by default, so saving alone
     * never changes the active build.
     */
    private void onSaveBuild(ModdedProfile profile) {
        if (profile == null || profile.isVanilla()) return;

        SaveBuildDialog.Result result = SaveBuildDialog.show(
                (Stage) root.getScene().getWindow());
        if (result == null) return; // cancelled

        Path dir = moddedProfileService.resolveGameDir(profile);
        try {
            ModdedProfileService.ensureProfileFolders(dir);
            BuildService.BuildInfo saved = buildService.saveBuild(
                    dir, result.name(), result.mods(), result.configs());
            // Activating is the user's explicit choice; all other
            // builds stay saved and untouched either way
            if (result.activate()) {
                buildService.selectBuild(dir, saved.name());
                statusLabel.setText("Build saved and activated: "
                        + saved.name() + " (" + saved.description()
                        + ") — applied at launch");
            } else {
                statusLabel.setText("Build saved: " + saved.name()
                        + " (" + saved.description() + ")"
                        + " — choose it via Select Build");
            }
            refreshInstanceCards();
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to save build: " + e.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Save Build",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Signs in to (or out of) the launcher server. The dialog performs
     * the whole flow; afterwards only the token is stored — the role
     * it brought decides whether administrative functions (publishing
     * builds) become available.
     */
    private void onServerLogin() {
        ServerSession result = ServerLoginDialog.show(
                (Stage) root.getScene().getWindow(), serverAuthService, serverSession);
        boolean changed = result != serverSession;
        serverSession = result;
        updateServerButtonText();
        if (changed) {
            statusLabel.setText(serverSession == null
                    ? "Signed out of the launcher server — running in local mode"
                    : "Signed in to the launcher server as "
                            + serverSession.accountName()
                            + (serverSession.isAdmin()
                                    ? " (administrator)"
                                    : ""));
        }
        refreshProfilesView();
        refreshSettingsView();
    }

    private void updateServerButtonText() {
        if (serverButton == null) {
            return;
        }
        serverButton.setText(serverSession == null ? "Server"
                : "Server: " + serverSession.accountName()
                        + (serverSession.isAdmin() ? " (admin)" : ""));
    }

    /**
     * Opens the catalog of builds the administrator published and
     * installs the chosen one into the selected modded instance. Each
     * build gets its own folder — saved builds and other server
     * builds are never touched; afterwards it is activated via Select
     * Build like any local build. Requires a signed-in session (the
     * sign-in dialog opens automatically when none exists).
     */
    private void onServerBuilds(ModdedProfile profile) {
        if (profile == null || profile.isVanilla()) return;
        if (serverSession == null) {
            onServerLogin();
            if (serverSession == null) {
                statusLabel.setText(
                        "Sign in to the launcher server first (Server, top right)");
                return;
            }
        }
        Path dir = moddedProfileService.resolveGameDir(profile);

        ServerBuildsDialog.Outcome outcome = ServerBuildsDialog.show(
                (Stage) root.getScene().getWindow(), serverSession,
                remoteBuildService, serverApi, profile, dir);

        if (outcome != null) {
            statusLabel.setText((outcome.wasUpdate() ? "Build updated: "
                    : "Build installed: ")
                    + outcome.displayName() + " " + outcome.version()
                    + " — activate it via Select Build");
            refreshInstanceCards();
        }
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
        footerVersionLabel.setText("Launcher " + APP_VERSION + "  |  All "
                + versionEntries.size() + " versions available");
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
            refreshProfilesView();
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
            refreshProfilesView();
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

        // Several instances may share one version (each has its own
        // name, game directory, mods and builds) — every creation
        // makes a new one; the directory name stays unique
        // automatically
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
                // The display name is typed in freely (automatic
                // "Loader MC" when empty); the memory limit defaults
                // to automatic and is tuned in the Edit dialog
                return moddedProfileService.createProfile(
                        type,
                        type == ModLoaderType.VANILLA ? "" : loader.loaderVersion(),
                        mc.id(), versionId, result.extraJvmArgs(),
                        result.name(), 0);
            }
        };
        task.setOnSucceeded(e -> {
            ModdedProfile profile = task.getValue();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 0, 0,
                    List.of()));
            jumpToInstance(profile);
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
            if (selectId != null) {
                selectedInstanceId = selectId;
            } else if (selectedInstance() == null) {
                selectedInstanceId = null;
            }
            refreshInstanceCards();
            updateInstanceChooser();
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to load instances: " + e.getMessage());
        }
    }

    /**
     * Opens the selected profile's game directory in the system file
     * manager, so the user can add mods, resource packs, shader packs,
     * worlds or check logs manually.
     */
    private void onOpenProfileFolder(ModdedProfile profile) {
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
     * Opens the edit dialog for the selected profile (display name,
     * memory limit and extra JVM arguments) and persists the changes.
     * Renaming also renames the game directory (mods, saves and
     * builds move along); the versions stay fixed.
     */
    private void onEditProfile(ModdedProfile profile) {
        if (profile == null) return;

        EditProfileDialog.Result result = EditProfileDialog.show(
                (Stage) root.getScene().getWindow(), profile);
        if (result == null) return;

        try {
            // Renaming also renames the folder (mods/saves/builds
            // move along), so select by the new id afterwards
            Optional<ModdedProfile> updated = moddedProfileService.updateProfile(
                    profile.id(), result.extraJvmArgs(), result.name(),
                    result.memoryMb());
            if (updated.isPresent()) {
                refreshModdedProfiles(updated.get().id());
                statusLabel.setText("Instance updated: " + updated.get().name()
                        + "  ·  Memory: "
                        + ModdedProfileService.formatMemory(
                                updated.get().memoryMb())
                        + (result.extraJvmArgs().isEmpty() ? ""
                                : "  ·  " + result.extraJvmArgs().size()
                                        + " JVM args"));
            }
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to update instance: " + e.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Update Instance",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void onDeleteProfile(ModdedProfile profile) {
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

    /**
     * Common launch entry for every Play button (cards and version
     * browser): account resolution, Ely.by refresh and then the
     * verify → repair-if-needed → launch chain.
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
            refreshLaunchButtons();
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
            refreshLaunchButtons();
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
        refreshLaunchButtons();
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
                // A selected build replaces the instance's mods and
                // config folders right before the game starts
                if (!profile.isVanilla()) {
                    String build = buildService.selectedBuild(runtimeDir)
                            .orElse(null);
                    if (build != null) {
                        buildService.applyBuild(runtimeDir, build);
                    }
                }
                return launchService.launch(metadata, storage, account,
                        runtimeDir,
                        ModdedProfileService.effectiveJvmArgs(profile));
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
                refreshLaunchButtons();
                String msg = "Launch failed: " + result.message();
                statusLabel.setText(msg);
                Stage launcherStage = (Stage) root.getScene().getWindow();
                ErrorDialog.show(launcherStage, "Launch Failed",
                        result.status() + ": " + result.message());
            }
        });
        launchTask.setOnFailed(e -> {
            refreshLaunchButtons();
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
            refreshLaunchButtons();
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
                    refreshLaunchButtons();
                });
            } catch (InterruptedException e) {
                javafx.application.Platform.runLater(() -> {
                    launcherStage.show();
                    statusLabel.setText("Process monitoring interrupted");
                    refreshLaunchButtons();
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
