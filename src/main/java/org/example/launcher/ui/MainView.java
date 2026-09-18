package org.example.launcher.ui;

import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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

/**
 * Builds and manages the main launcher window.
 * <p>
 * Layout (BorderPane):
 * <pre>
 * +--------+--------------------------------------------------+
 * | Nav    | Top bar: view title + search + account pill      |
 * | rail   +--------------------------------------------------+
 * | Inst.  | Views (center stack):                            |
 * | Prof.  |  My Instances — filter chips + instance cards    |
 * | Sett.  |  Profiles — accounts management                  |
 * |        |  Settings — folders, Java, server, about         |
 * |        +--------------------------------------------------+
 * |        | Footer: status + launcher info                   |
 * +--------+--------------------------------------------------+
 * </pre>
 * <p>
 * Everything is an instance (vanilla or modded), each with its own
 * game directory. New instances are created via a single simple
 * dialog (name → loader chips → version list → loader version
 * list); the Mojang manifest is only fetched in the background
 * to feed that dialog.
 */
public class MainView {

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
    private final ModLoaderRegistry modLoaderRegistry;
    private final ModdedProfileService moddedProfileService;
    private final ModdedProfileVerificationService profileVerificationService;

    private BorderPane root;
    private Label statusLabel;
    private boolean versionsLoadFailed = false;

    // Navigation rail + views
    private enum View {
        INSTANCES, PROFILES, SETTINGS
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
    private VBox profilesView;
    private VBox profilesBox;
    private VBox settingsView;
    private TextField javaPathField;
    private Label serverSessionLabel;
    private Label footerVersionLabel;

    // Single-launch gate: a launch attempt blocks every Play button
    private boolean versionPlayInFlight = false;
    /** Instance currently going through launch, if any (single launch). */
    private String launchingProfileId;
    private String launchingStatusText = "Launching...";

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
    private Button deleteAccountButton;
    private Button elyLoginButton;
    private ImageView avatarView;
    private GameProfile selectedProfile;
    private boolean suppressSelectionListener = false;
    private java.util.Timer elyRefreshTimer;

    /** Versions of the Mojang manifest — feeds the creation dialog. */
    private List<MinecraftVersion> manifestVersions = List.of();

    public MainView(VersionService versionService,
                    VersionMetadataService metadataService,
                    InstallationService installationService,
                    MinecraftLaunchService launchService,
                    JavaResolutionService javaResolutionService,
                    JavaRuntimeInstaller javaRuntimeInstaller,
                     ProfileService profileService,
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
        this.preferences = preferences;
        this.elyAuthService = elyAuthService;
        this.skinService = skinService;
        this.modLoaderRegistry = modLoaderRegistry;
        this.moddedProfileService = moddedProfileService;
        this.profileVerificationService = profileVerificationService;
        serverSession = serverAuthService.restoreSession().orElse(null);
        buildView();
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
        profilesView = buildProfilesView();
        settingsView = buildSettingsView();
        viewStack = new StackPane(instancesView, profilesView,
                settingsView);

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
        createAccountButton.setTooltip(new Tooltip("Create an offline account"));

        deleteAccountButton = new Button("Delete");
        deleteAccountButton.getStyleClass().add("account-delete-button");
        deleteAccountButton.setTooltip(
                new Tooltip("Delete the selected account"));
        deleteAccountButton.setOnAction(e -> {
            if (selectedProfile != null) {
                onDeleteAccount(selectedProfile);
            } else {
                statusLabel.setText("No account selected");
            }
        });

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
                createAccountButton, deleteAccountButton, elyLoginButton,
                serverButton);
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
        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        Button quickBuildButton = new Button("+ Build");
        quickBuildButton.getStyleClass().add("quick-select-button");
        quickBuildButton.setOnAction(e -> onQuickSaveBuild());
        instanceFilterChips.getChildren().addAll(filterSpacer,
                quickBuildButton);

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
        profilesView.setVisible(view == View.PROFILES);
        profilesView.setManaged(view == View.PROFILES);
        settingsView.setVisible(view == View.SETTINGS);
        settingsView.setManaged(view == View.SETTINGS);
        viewTitleLabel.setText(switch (view) {
            case INSTANCES -> "My Instances";
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
        launchingProfileId = null;
        refreshInstanceCards();
    }

    /**
     * Shows the launch stage on the instance card (all calls happen
     * on the FX thread).
     */
    private void setLaunchStatus(String profileId, String text) {
        launchingProfileId = profileId;
        launchingStatusText = text;
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
        boolean launching = profile.id().equals(launchingProfileId);
        if (launching) {
            card.getStyleClass().add("instance-card-launching");
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

        Button play = new Button(launching ? launchingStatusText : "Play");
        play.getStyleClass().add("card-play");
        play.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(play, Priority.ALWAYS);
        play.setDisable(versionPlayInFlight || launching);
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

        HBox actions = new HBox(6, play, folder, edit);
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
            HBox buildsRow = new HBox(6, saveBuild, serverBuilds);
            details.getChildren().add(buildsRow);
        }

        Button delete = new Button("Delete Instance");
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

    /**
     * Icon letters: the first letter, two letters where it collides
     * (Fabric/Forge share "F").
     */
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
    //  Profiles view: accounts management
    // ------------------------------------------------------------------

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
     * Saves the current mods/configs of the selected modded instance
     * as a new build: the user names it and chooses the contents
     * (mods only, or mods together with configs).
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
            statusLabel.setText("Build saved: " + saved.name()
                    + " (" + saved.description() + ")");
            refreshInstanceCards();
        } catch (java.io.IOException e) {
            statusLabel.setText("Failed to save build: " + e.getMessage());
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    "Cannot Save Build",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Fast build saving without scrolling the cards: the instance is
     * picked by typing right in the dialog.
     */
    private void onQuickSaveBuild() {
        boolean anyModded = false;
        for (ModdedProfile p : moddedProfiles) {
            if (!p.isVanilla()) {
                anyModded = true;
                break;
            }
        }
        if (!anyModded) {
            statusLabel.setText(
                    "Create a modded instance first — builds need mods");
            return;
        }
        QuickSaveBuildDialog.Result result = QuickSaveBuildDialog.show(
                (Stage) root.getScene().getWindow(), moddedProfiles,
                selectedInstanceId);
        if (result == null) return; // cancelled

        Path dir = moddedProfileService.resolveGameDir(result.profile());
        try {
            ModdedProfileService.ensureProfileFolders(dir);
            BuildService.BuildInfo saved = buildService.saveBuild(
                    dir, result.name(), result.mods(), result.configs());
            statusLabel.setText("Build saved: " + saved.name()
                    + " (" + saved.description() + ") for "
                    + result.profile().name());
            selectInstance(result.profile().id());
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
     * builds are never touched. Requires a signed-in session (the
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
                    + outcome.displayName() + " " + outcome.version());
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
        statusLabel.setText(manifestVersions.size()
                + " versions available for new instances");
        footerVersionLabel.setText("Launcher " + APP_VERSION + "  |  All "
                + manifestVersions.size() + " versions available");
        refreshModdedProfiles(null);
    }

    private void onLoadFailed(Throwable cause) {
        versionsLoadFailed = true;
        statusLabel.setText("Version list load failed ("
                + cause.getMessage() + ")");
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
     * Opens the New Instance dialog — one simple screen with the
     * instance name, loader chips, the version list and the loader
     * version list — and creates the instance on confirmation.
     * Version picking lives here, on the main tab.
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
     *                      it
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
     * Common launch entry for every Play button on the cards:
     * account resolution, Ely.by refresh and then the
     * verify → repair-if-needed → launch chain.
     */
    private void startInstanceLaunch(ModdedProfile profile) {
        // Only one game at a time per launcher window — a second
        // attempt while anything is launching is ignored
        if (versionPlayInFlight) {
            statusLabel.setText("Already launching "
                    + launchingStatusText.toLowerCase() + " — please wait");
            return;
        }
        GameProfile account = getOrCreateProfile();
        GameDirectory storage = GameDirectory.defaultDirectory();

        // A launch attempt blocks every Play button immediately
        // (until a terminal callback refreshes them)
        versionPlayInFlight = true;
        javaDownloadAttempted = false;
        setLaunchStatus(profile.id(), "Verifying...");
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
        setLaunchStatus(profile.id(), "Repairing...");

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
        setLaunchStatus(profile.id(), "Launching...");
        Path runtimeDir = storage.root().resolve(profile.gameDirPath());

        Task<LaunchResult> launchTask = new Task<>() {
            @Override
            protected LaunchResult call() throws Exception {
                // The profile's game directory must exist before the
                // process can run inside it
                ModdedProfileService.ensureProfileFolders(runtimeDir);
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
        setLaunchStatus(profile.id(), "Installing Java...");

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
