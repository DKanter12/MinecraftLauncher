package org.example.launcher.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.AssetIndex;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.LaunchResult;
import org.example.launcher.model.MinecraftProcess;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.VersionManifest;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.DefaultJavaResolutionService;
import org.example.launcher.service.ElyAuthService;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.service.JavaRuntimeInstaller;
import org.example.launcher.service.LauncherPreferences;
import org.example.launcher.service.MinecraftLaunchService;
import org.example.launcher.service.ProfileService;
import org.example.launcher.service.SkinService;
import org.example.launcher.service.VersionMetadataService;
import org.example.launcher.service.VersionService;
import org.example.launcher.util.OsDetector;
import org.example.launcher.version.VersionType;
import org.example.launcher.version.VersionTypeRegistry;

/**
 * Builds and manages the main launcher window.
 * <p>
 * Layout (BorderPane):
 * <pre>
 * +------------------------------------------------------------+
 * |  Header: title + version count                             |
 * +--------+------------------------------------+--------------+
 * | Sidebar|  Version table                     |  Metadata    |
 * |        |                                    |  panel       |
 * | Quick  |  Version | Type   | Release Date   |  (async)     |
 * | Select |  1.21    | Release| 13 Jun 2024    |              |
 * |        |  ...     | ...    | ...            |              |
 * | Filter |                                    |              |
 * | by Type|                                    |              |
 * |        |                                    |              |
 * | Search |                                    |              |
 * +--------+------------------------------------+--------------+
 * |  Details panel: selected version info + Play button        |
 * +------------------------------------------------------------+
 * </pre>
 */
public class MainView {

    private static final String STABLE_BADGE = "stable-badge";
    private static final String UNSTABLE_BADGE = "unstable-badge";

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

    private BorderPane root;
    private TableView<MinecraftVersion> table;
    private TextField searchField;
    private Label statusLabel;
    private Button playButton;
    private Button retryButton;
    private ProgressIndicator progressIndicator;
    private VBox errorBox;
    private Label errorLabel;

    // Sidebar
    private ToggleGroup typeFilterGroup;
    private final Map<VersionType, ToggleButton> typeFilterButtons = new HashMap<>();
    private ToggleButton allTypesButton;
    private Button latestReleaseButton;
    private Button latestSnapshotButton;
    private final Map<VersionType, Label> typeCountLabels = new HashMap<>();

    // Details panel
    private Label detailsVersionLabel;
    private Label detailsTypeLabel;
    private Label detailsDateLabel;
    private Label detailsUrlLabel;
    private VBox detailsPanel;

    // Metadata panel
    private VBox metadataPanel;
    private ProgressIndicator metadataProgress;
    private Label metadataErrorLabel;
    private Label metaMainClassLabel;
    private Label metaJavaVersionLabel;
    private Label metaJavaStatusLabel;
    private Label metaClientJarLabel;
    private Label metaClientSizeLabel;
    private Label metaLibrariesLabel;
    private Label metaNativesLabel;
    private Label metaAssetsLabel;
    private Label metaGameArgsLabel;
    private Label metaJvmArgsLabel;
    private Label metaLegacyArgsLabel;

    // Custom Java path
    private Button browseJavaButton;
    private Label customJavaLabel;
    private boolean javaDownloadAttempted = false;

    // Account
    private ComboBox<GameProfile> accountCombo;
    private Button createAccountButton;
    private Button elyLoginButton;
    private ImageView avatarView;
    private Label accountNameLabel;
    private Label accountTypeLabel;
    private GameProfile selectedProfile;

    private List<MinecraftVersion> allVersions = List.of();
    private VersionManifest currentManifest;
    private MinecraftVersion currentlySelectedVersion;
    private VersionMetadata currentlySelectedMetadata;
    private JavaResolutionResult currentJavaResolution;

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
                    SkinService skinService) {
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

        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setLeft(buildSidebar());
        root.setBottom(buildDetailsPanel());
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
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(220);
        sidebar.setMaxWidth(280);
        sidebar.setPadding(new Insets(16));
        sidebar.getStyleClass().add("sidebar");

        // -- Quick Select section --
        Label quickSelectTitle = new Label("Quick Select");
        quickSelectTitle.getStyleClass().add("section-title");

        latestReleaseButton = new Button("—");
        latestReleaseButton.getStyleClass().add("quick-select-button");
        latestReleaseButton.setMaxWidth(Double.MAX_VALUE);
        latestReleaseButton.setDisable(true);
        latestReleaseButton.setOnAction(e -> selectLatestRelease());

        latestSnapshotButton = new Button("—");
        latestSnapshotButton.getStyleClass().add("quick-select-button");
        latestSnapshotButton.setMaxWidth(Double.MAX_VALUE);
        latestSnapshotButton.setDisable(true);
        latestSnapshotButton.setOnAction(e -> selectLatestSnapshot());

        VBox quickSelect = new VBox(6,
                quickSelectTitle,
                labeledButton("Latest Release", latestReleaseButton),
                labeledButton("Latest Snapshot", latestSnapshotButton));

        // -- Filter by Type section --
        Label filterTitle = new Label("Filter by Type");
        filterTitle.getStyleClass().add("section-title");

        typeFilterGroup = new ToggleGroup();

        allTypesButton = createTypeFilterButton("All Types", null);
        allTypesButton.setSelected(true);
        allTypesButton.getStyleClass().add("filter-button-active");

        VBox filterBox = new VBox(4, filterTitle, allTypesButton);
        for (VersionType type : typeRegistry.all()) {
            ToggleButton btn = createTypeFilterButton(type.displayName(), type);
            filterBox.getChildren().add(btn);
        }

        // -- Search --
        Label searchTitle = new Label("Search");
        searchTitle.getStyleClass().add("section-title");

        searchField = new TextField();
        searchField.setPromptText("Search version...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());

        VBox searchBox = new VBox(6, searchTitle, searchField);

        // -- Spacer to push search to bottom --
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(quickSelect, filterBox, spacer, searchBox);
        return sidebar;
    }

    private ToggleButton createTypeFilterButton(String label, VersionType type) {
        HBox buttonContent = new HBox(8);
        buttonContent.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("filter-button-label");

        Label countLabel = new Label("0");
        countLabel.getStyleClass().add("filter-button-count");

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);

        buttonContent.getChildren().addAll(nameLabel, btnSpacer, countLabel);

        ToggleButton button = new ToggleButton();
        button.setGraphic(buttonContent);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("filter-button");
        button.setToggleGroup(typeFilterGroup);
        button.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                button.getStyleClass().add("filter-button-active");
            } else {
                button.getStyleClass().remove("filter-button-active");
            }
            applyFilter();
        });

        if (type != null) {
            typeFilterButtons.put(type, button);
            typeCountLabels.put(type, countLabel);
        } else {
            allTypesButton = button;
        }
        return button;
    }

    private VBox labeledButton(String labelText, Button button) {
        Label label = new Label(labelText);
        label.getStyleClass().add("quick-select-label");
        return new VBox(2, label, button);
    }

    // --- Center (table + metadata panel) ---

    private HBox buildCenter() {
        StackPane tableArea = buildTableArea();
        metadataPanel = buildMetadataPanel();

        HBox center = new HBox(0, tableArea, metadataPanel);
        HBox.setHgrow(tableArea, Priority.ALWAYS);
        return center;
    }

    private StackPane buildTableArea() {
        table = buildVersionTable();

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(64, 64);

        errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("error-label");
        retryButton = new Button("Retry");
        retryButton.getStyleClass().add("retry-button");
        retryButton.setOnAction(e -> loadVersions());
        errorBox = new VBox(15, errorLabel, retryButton);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setVisible(false);

        StackPane pane = new StackPane(table, progressIndicator, errorBox);
        pane.setPadding(new Insets(0, 0, 0, 0));
        return pane;
    }

    private TableView<MinecraftVersion> buildVersionTable() {
        TableView<MinecraftVersion> tableView = new TableView<>();
        tableView.setPlaceholder(new Label("No versions found."));
        tableView.getStyleClass().add("version-table");

        TableColumn<MinecraftVersion, String> idCol = new TableColumn<>("Version");
        idCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().id()));
        idCol.setPrefWidth(220);

        TableColumn<MinecraftVersion, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().type().displayName()));
        typeCol.setPrefWidth(120);

        TableColumn<MinecraftVersion, String> dateCol = new TableColumn<>("Release Date");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedReleaseTime()));
        dateCol.setPrefWidth(180);

        tableView.getColumns().setAll(List.of(idCol, typeCol, dateCol));
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        tableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> onVersionSelected(selected));

        return tableView;
    }

    // --- Metadata panel (right side of center) ---

    private VBox buildMetadataPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(320);
        panel.setMinWidth(280);
        panel.setMaxWidth(380);
        panel.setPadding(new Insets(16));
        panel.getStyleClass().add("metadata-panel");

        Label title = new Label("Version Metadata");
        title.getStyleClass().add("section-title");

        metadataProgress = new ProgressIndicator();
        metadataProgress.setMaxSize(36, 36);
        metadataProgress.setVisible(false);

        metadataErrorLabel = new Label();
        metadataErrorLabel.setWrapText(true);
        metadataErrorLabel.getStyleClass().add("metadata-error");
        metadataErrorLabel.setVisible(false);

        metaMainClassLabel = createMetaLabel("Main Class");
        metaJavaVersionLabel = createMetaLabel("Java Version");
        metaJavaStatusLabel = createMetaLabel("Java Status");
        metaClientJarLabel = createMetaLabel("Client JAR");
        metaClientSizeLabel = createMetaLabel("Client Size");
        metaLibrariesLabel = createMetaLabel("Libraries");
        metaNativesLabel = createMetaLabel("Natives");
        metaAssetsLabel = createMetaLabel("Assets Index");
        metaGameArgsLabel = createMetaLabel("Game Arguments");
        metaJvmArgsLabel = createMetaLabel("JVM Arguments");
        metaLegacyArgsLabel = createMetaLabel("Legacy Arguments");

        browseJavaButton = new Button("Browse Java...");
        browseJavaButton.getStyleClass().add("browse-java-button");
        browseJavaButton.setOnAction(e -> browseForJava());

        customJavaLabel = new Label("Custom Java: not set");
        customJavaLabel.getStyleClass().add("metadata-item");
        customJavaLabel.setWrapText(true);

        VBox metaContent = new VBox(10,
                metaMainClassLabel,
                metaJavaVersionLabel,
                metaJavaStatusLabel,
                customJavaLabel,
                browseJavaButton,
                metaClientJarLabel,
                metaClientSizeLabel,
                metaLibrariesLabel,
                metaNativesLabel,
                metaAssetsLabel,
                metaGameArgsLabel,
                metaJvmArgsLabel,
                metaLegacyArgsLabel);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        panel.getChildren().addAll(title, metadataProgress, metadataErrorLabel, metaContent, spacer);
        panel.setVisible(false);
        return panel;
    }

    private Label createMetaLabel(String title) {
        Label label = new Label(title + ": —");
        label.getStyleClass().add("metadata-item");
        label.setWrapText(true);
        return label;
    }

    // --- Details panel (bottom) ---

    private HBox buildDetailsPanel() {
        detailsPanel = new VBox(4);
        detailsPanel.setPadding(new Insets(12, 20, 12, 20));
        detailsPanel.getStyleClass().add("details-panel");
        detailsPanel.setVisible(false);

        Label detailsTitle = new Label("Selected Version");
        detailsTitle.getStyleClass().add("section-title");

        detailsVersionLabel = new Label("—");
        detailsVersionLabel.getStyleClass().add("details-version");

        detailsTypeLabel = new Label("—");
        detailsTypeLabel.getStyleClass().add("type-badge");

        detailsDateLabel = new Label("—");
        detailsDateLabel.getStyleClass().add("details-info");

        detailsUrlLabel = new Label("—");
        detailsUrlLabel.getStyleClass().add("details-info");
        detailsUrlLabel.setWrapText(true);

        VBox infoBox = new VBox(6, detailsTitle, detailsVersionLabel,
                new HBox(8, detailsTypeLabel, detailsDateLabel),
                detailsUrlLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        playButton = new Button("Play");
        playButton.getStyleClass().add("play-button");
        playButton.setDisable(true);
        playButton.setOnAction(e -> onPlay());

        VBox playBox = new VBox(playButton);
        playBox.setAlignment(Pos.CENTER);

        HBox panel = new HBox(20, infoBox, playBox);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.getStyleClass().add("details-panel-outer");
        panel.setPadding(new Insets(0));

        detailsPanel.getChildren().add(panel);
        return new HBox(detailsPanel);
    }

    // ------------------------------------------------------------------
    //  Async: version list loading
    // ------------------------------------------------------------------

    public void loadVersions() {
        refreshAccounts();
        showLoading();

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

    private void showLoading() {
        progressIndicator.setVisible(true);
        errorBox.setVisible(false);
        table.setVisible(false);
        statusLabel.setText("Loading versions...");
        latestReleaseButton.setDisable(true);
        latestSnapshotButton.setDisable(true);
    }

    private void onVersionsLoaded(VersionManifest manifest) {
        progressIndicator.setVisible(false);
        table.setVisible(true);
        currentManifest = manifest;
        allVersions = manifest.versions();

        updateTypeCounts();
        updateQuickSelectButtons();
        applyFilter();

        int count = allVersions.size();
        statusLabel.setText(count + " versions available");

        restoreLastSelectedVersion();
    }

    private void onLoadFailed(Throwable cause) {
        progressIndicator.setVisible(false);
        table.setVisible(false);
        errorLabel.setText("Failed to load versions:\n" + cause.getMessage());
        errorBox.setVisible(true);
        statusLabel.setText("Load failed.");
    }

    // ------------------------------------------------------------------
    //  Async: metadata loading on version selection
    // ------------------------------------------------------------------

    private void onVersionSelected(MinecraftVersion selected) {
        updateDetailsPanel(selected);

        if (selected == null) {
            metadataPanel.setVisible(false);
            currentlySelectedVersion = null;
            return;
        }

        if (selected.equals(currentlySelectedVersion)) return;
        currentlySelectedVersion = selected;

        saveLastSelectedVersion(selected.id());

        loadMetadata(selected);
    }

    private void loadMetadata(MinecraftVersion version) {
        metadataPanel.setVisible(true);
        metadataProgress.setVisible(true);
        metadataErrorLabel.setVisible(false);
        clearMetadataLabels();

        Task<VersionMetadata> task = new Task<>() {
            @Override
            protected VersionMetadata call() throws Exception {
                return metadataService.fetchMetadata(version);
            }
        };
        task.setOnSucceeded(e -> onMetadataLoaded(task.getValue()));
        task.setOnFailed(e -> onMetadataFailed(task.getException()));
        var thread = new Thread(task, "metadata-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    private void onMetadataLoaded(VersionMetadata meta) {
        metadataProgress.setVisible(false);
        currentlySelectedMetadata = meta;

        metaMainClassLabel.setText("Main Class: " + meta.mainClass().orElse("—"));

        int requiredJava = meta.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(8);
        meta.javaVersion().ifPresentOrElse(
                jv -> metaJavaVersionLabel.setText("Java Version: " + jv.majorVersion()
                        + " (" + jv.componentOpt().orElse("—") + ")"),
                () -> metaJavaVersionLabel.setText("Java Version: 8 (not specified, default)"));

        resolveJava(meta, requiredJava);

        meta.clientDownload().ifPresentOrElse(
                dl -> {
                    metaClientJarLabel.setText("Client JAR: " + dl.url());
                    metaClientSizeLabel.setText("Client Size: " + formatSize(dl.size()));
                },
                () -> {
                    metaClientJarLabel.setText("Client JAR: —");
                    metaClientSizeLabel.setText("Client Size: —");
                });

        metaLibrariesLabel.setText("Libraries: " + meta.libraries().size());
        metaNativesLabel.setText("Natives: " + meta.nativeLibraries(OsDetector.mojangName()).size()
                + " (" + OsDetector.mojangName() + ")");

        meta.assetIndex().ifPresentOrElse(
                ai -> metaAssetsLabel.setText("Assets Index: " + ai.id()
                        + " (" + formatSize(ai.totalSize()) + ")"),
                () -> metaAssetsLabel.setText("Assets Index: " + meta.assets().orElse("—")));

        metaGameArgsLabel.setText("Game Arguments: " + meta.gameArguments().size()
                + (meta.gameArguments().isEmpty() ? "" : " tokens"));
        metaJvmArgsLabel.setText("JVM Arguments: " + meta.jvmArguments().size()
                + (meta.jvmArguments().isEmpty() ? "" : " tokens"));

        meta.legacyMinecraftArguments().ifPresentOrElse(
                args -> metaLegacyArgsLabel.setText("Legacy Arguments: " + args.length() + " chars"),
                () -> metaLegacyArgsLabel.setText("Legacy Arguments: none"));
    }

    private void onMetadataFailed(Throwable cause) {
        metadataProgress.setVisible(false);
        metadataErrorLabel.setText("Failed to load metadata:\n" + cause.getMessage());
        metadataErrorLabel.setVisible(true);
        clearMetadataLabels();
    }

    private void resolveJava(VersionMetadata meta, int requiredMajor) {
        Task<JavaResolutionResult> task = new Task<>() {
            @Override
            protected JavaResolutionResult call() throws Exception {
                return javaResolutionService.resolve(meta);
            }
        };
        task.setOnSucceeded(e -> {
            currentJavaResolution = task.getValue();
            updateJavaStatusLabel(currentJavaResolution);
        });
        task.setOnFailed(e -> {
            currentJavaResolution = JavaResolutionResult.notFound(
                    "Java detection failed: " + task.getException().getMessage());
            updateJavaStatusLabel(currentJavaResolution);
        });
        var thread = new Thread(task, "java-resolve");
        thread.setDaemon(true);
        thread.start();
    }

    private void browseForJava() {
        Stage stage = (Stage) root.getScene().getWindow();
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select Java Executable");
        fc.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Java Executable", "java.exe", "java"));
        fc.setInitialDirectory(new java.io.File("C:\\Program Files"));
        java.io.File chosen = fc.showOpenDialog(stage);
        if (chosen == null) return;

        Path javaExe = chosen.toPath();
        customJavaLabel.setText("Custom Java: " + javaExe);

        if (javaResolutionService instanceof DefaultJavaResolutionService svc) {
            svc.setCustomJavaPath(javaExe);
        }

        if (currentlySelectedMetadata != null) {
            int required = currentlySelectedMetadata.javaVersion()
                    .map(JavaVersion::majorVersion).orElse(8);
            resolveJava(currentlySelectedMetadata, required);
        }
    }

    private boolean needsJava8(VersionMetadata meta) {
        int required = meta.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(8);
        return required <= 8;
    }

    private void downloadAndInstallJava8(MinecraftVersion selected,
                                          GameDirectory gameDir,
                                          GameProfile profile) {
        Task<JavaRuntime> installTask = new Task<>() {
            @Override
            protected JavaRuntime call() throws Exception {
                Path targetDir = gameDir.javaRuntimeDir("jre-legacy");
                return javaRuntimeInstaller.install(8, targetDir);
            }
        };
        installTask.setOnSucceeded(e -> {
            JavaRuntime rt = installTask.getValue();
            statusLabel.setText("Java 8 installed. Launching " + selected.id() + "...");

            if (javaResolutionService instanceof DefaultJavaResolutionService svc) {
                svc.setCustomJavaPath(rt.javaExecutable());
            }
            doLaunch(selected, gameDir, profile, true);
        });
        installTask.setOnFailed(e -> {
            String msg = "Java 8 download failed: "
                    + installTask.getException().getMessage();
            statusLabel.setText(msg);
            playButton.setDisable(false);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage, "Java Download Failed",
                    installTask.getException().getClass().getSimpleName() + ": "
                            + installTask.getException().getMessage()
                            + "\n\nPlease install Java 8 manually from adoptium.net"
                            + " and use 'Browse Java...' to select it.");
        });
        var thread = new Thread(installTask, "java-install");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateJavaStatusLabel(JavaResolutionResult result) {
        metaJavaStatusLabel.getStyleClass().removeAll("java-ok", "java-warn", "java-error");
        switch (result.status()) {
            case FOUND -> {
                JavaRuntime rt = result.runtime().orElseThrow();
                metaJavaStatusLabel.setText("Java Status: OK (Java " + rt.majorVersion()
                        + " at " + rt.javaExecutable().getParent() + ")");
                metaJavaStatusLabel.getStyleClass().add("java-ok");
            }
            case INCOMPATIBLE -> {
                metaJavaStatusLabel.setText("Java Status: " + result.reason().orElse("Incompatible"));
                metaJavaStatusLabel.getStyleClass().add("java-warn");
            }
            case NOT_FOUND -> {
                metaJavaStatusLabel.setText("Java Status: " + result.reason().orElse("Not found"));
                metaJavaStatusLabel.getStyleClass().add("java-error");
            }
        }
    }

    private void clearMetadataLabels() {
        metaMainClassLabel.setText("Main Class: …");
        metaJavaVersionLabel.setText("Java Version: …");
        metaJavaStatusLabel.setText("Java Status: …");
        metaJavaStatusLabel.getStyleClass().removeAll("java-ok", "java-warn", "java-error");
        metaClientJarLabel.setText("Client JAR: …");
        metaClientSizeLabel.setText("Client Size: …");
        metaLibrariesLabel.setText("Libraries: …");
        metaNativesLabel.setText("Natives: …");
        metaAssetsLabel.setText("Assets Index: …");
        metaGameArgsLabel.setText("Game Arguments: …");
        metaJvmArgsLabel.setText("JVM Arguments: …");
        metaLegacyArgsLabel.setText("Legacy Arguments: …");
    }

    // ------------------------------------------------------------------
    //  Sidebar updates
    // ------------------------------------------------------------------

    private void updateTypeCounts() {
        Map<VersionType, Integer> counts = new HashMap<>();
        for (VersionType t : typeRegistry.all()) {
            counts.put(t, 0);
        }
        for (MinecraftVersion v : allVersions) {
            counts.merge(v.type(), 1, Integer::sum);
        }
        for (var entry : typeCountLabels.entrySet()) {
            Label label = entry.getValue();
            int c = counts.getOrDefault(entry.getKey(), 0);
            label.setText(String.valueOf(c));
        }
    }

    private void updateQuickSelectButtons() {
        if (currentManifest == null) return;

        currentManifest.latestReleaseId().ifPresentOrElse(
                id -> {
                    latestReleaseButton.setText(id);
                    latestReleaseButton.setDisable(false);
                },
                () -> {
                    latestReleaseButton.setText("—");
                    latestReleaseButton.setDisable(true);
                }
        );

        currentManifest.latestSnapshotId().ifPresentOrElse(
                id -> {
                    latestSnapshotButton.setText(id);
                    latestSnapshotButton.setDisable(false);
                },
                () -> {
                    latestSnapshotButton.setText("—");
                    latestSnapshotButton.setDisable(true);
                }
        );
    }

    // ------------------------------------------------------------------
    //  Filtering
    // ------------------------------------------------------------------

    private VersionType getSelectedFilterType() {
        ToggleButton selected = (ToggleButton) typeFilterGroup.getSelectedToggle();
        if (selected == null || selected == allTypesButton) {
            return null;
        }
        for (var entry : typeFilterButtons.entrySet()) {
            if (entry.getValue() == selected) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void applyFilter() {
        if (table == null) return;
        if (allVersions.isEmpty()) {
            table.setItems(FXCollections.emptyObservableList());
            return;
        }

        VersionType selectedType = getSelectedFilterType();
        String query = (searchField.getText() != null) ? searchField.getText().trim().toLowerCase() : "";

        List<MinecraftVersion> filtered = new ArrayList<>();
        for (MinecraftVersion v : allVersions) {
            if (selectedType != null && !v.type().equals(selectedType)) {
                continue;
            }
            if (!query.isEmpty() && !v.id().toLowerCase().contains(query)) {
                continue;
            }
            filtered.add(v);
        }

        filtered.sort(Comparator.comparing(
                (MinecraftVersion v) -> v.releaseTime().orElse(null),
                Comparator.nullsLast(Comparator.reverseOrder())));

        table.setItems(FXCollections.observableArrayList(filtered));
    }

    // ------------------------------------------------------------------
    //  Quick select actions
    // ------------------------------------------------------------------

    private void selectLatestRelease() {
        if (currentManifest == null) return;
        currentManifest.latestReleaseId().ifPresent(this::selectVersionById);
    }

    private void selectLatestSnapshot() {
        if (currentManifest == null) return;
        currentManifest.latestSnapshotId().ifPresent(this::selectVersionById);
    }

    private void selectVersionById(String id) {
        for (MinecraftVersion v : table.getItems()) {
            if (v.id().equals(id)) {
                table.getSelectionModel().select(v);
                table.scrollTo(v);
                return;
            }
        }
    }

    private void restoreLastSelectedVersion() {
        if (preferences == null) return;
        try {
            preferences.getLastSelectedVersion().ifPresent(id -> {
                for (MinecraftVersion v : allVersions) {
                    if (v.id().equals(id)) {
                        if (getSelectedFilterType() != null
                                && !v.type().equals(getSelectedFilterType())) {
                            allTypesButton.setSelected(true);
                            applyFilter();
                        }
                        selectVersionById(id);
                        break;
                    }
                }
            });
        } catch (java.io.IOException e) {
            // Non-fatal — just don't restore
        }
    }

    private void saveLastSelectedVersion(String versionId) {
        if (preferences == null) return;
        try {
            preferences.setLastSelectedVersion(versionId);
        } catch (java.io.IOException e) {
            // Non-fatal — preference just won't persist
        }
    }

    private void saveLastSelectedAccount(String accountName) {
        if (preferences == null) return;
        try {
            preferences.setLastSelectedAccount(accountName);
        } catch (java.io.IOException e) {
            // Non-fatal
        }
    }

    private void updateAvatar(GameProfile profile) {
        if (profile == null || profile.skinUrl().isEmpty()) {
            avatarView.setVisible(false);
            return;
        }
        skinService.loadAvatarAsync(profile, 32)
                .thenAccept(optImg -> javafx.application.Platform.runLater(() -> {
                    if (optImg.isPresent()) {
                        avatarView.setImage(optImg.get());
                        avatarView.setVisible(true);
                    } else {
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
            accountCombo.getItems().setAll(profiles);

            String savedName = null;
            if (preferences != null) {
                savedName = preferences.getLastSelectedAccount().orElse(null);
            }

            if (savedName != null) {
                for (GameProfile p : profiles) {
                    if (p.name().equals(savedName)) {
                        accountCombo.getSelectionModel().select(p);
                        selectedProfile = p;
                        return;
                    }
                }
            }
            if (selectedProfile != null) {
                for (GameProfile p : profiles) {
                    if (p.name().equals(selectedProfile.name())) {
                        accountCombo.getSelectionModel().select(p);
                        return;
                    }
                }
            }
            if (!profiles.isEmpty()) {
                accountCombo.getSelectionModel().select(0);
                selectedProfile = profiles.get(0);
            }
        } catch (java.io.IOException e) {
            // Non-fatal
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
                profiles.removeIf(p -> p.name().equals(profile.name()));
                profiles.add(profile);
                profileService.saveProfiles(profiles);
            } catch (java.io.IOException ex) {
                // Non-fatal
            }
            refreshAccounts();
            for (GameProfile p : accountCombo.getItems()) {
                if (p.name().equals(profile.name())) {
                    accountCombo.getSelectionModel().select(p);
                    selectedProfile = p;
                    saveLastSelectedAccount(profile.name());
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

    private void updateDetailsPanel(MinecraftVersion selected) {
        if (selected == null) {
            detailsPanel.setVisible(false);
            playButton.setDisable(true);
            return;
        }

        detailsPanel.setVisible(true);
        playButton.setDisable(false);

        detailsVersionLabel.setText(selected.id());

        detailsTypeLabel.setText(selected.type().displayName());
        detailsTypeLabel.getStyleClass().removeAll(STABLE_BADGE, UNSTABLE_BADGE);
        detailsTypeLabel.getStyleClass().add(
                selected.type().isStable() ? STABLE_BADGE : UNSTABLE_BADGE);

        detailsDateLabel.setText(selected.formattedReleaseTime());

        String url = selected.metadataUrl();
        detailsUrlLabel.setText(url != null ? url : "—");
    }

    // ------------------------------------------------------------------
    //  Play action
    // ------------------------------------------------------------------

    private void onPlay() {
        MinecraftVersion selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (currentlySelectedMetadata == null) {
            statusLabel.setText("Metadata not loaded yet, please wait...");
            return;
        }

        GameProfile profile = getOrCreateProfile();
        GameDirectory gameDir = GameDirectory.defaultDirectory();

        playButton.setDisable(true);
        javaDownloadAttempted = false;
        statusLabel.setText("Launching " + selected.id() + "...");

        if (profile.isElyBy()) {
            refreshAndLaunch(selected, gameDir, profile);
        } else {
            doLaunch(selected, gameDir, profile, false);
        }
    }

    private void refreshAndLaunch(MinecraftVersion selected, GameDirectory gameDir,
                                   GameProfile profile) {
        statusLabel.setText("Refreshing Ely.by profile...");
        Task<GameProfile> refreshTask = new Task<>() {
            @Override
            protected GameProfile call() throws Exception {
                return elyAuthService.refreshProfile(profile);
            }
        };
        refreshTask.setOnSucceeded(e -> {
            GameProfile refreshed = refreshTask.getValue();
            selectedProfile = refreshed;

            try {
                var profiles = new java.util.ArrayList<>(profileService.loadProfiles());
                profiles.removeIf(p -> p.uuid().equals(refreshed.uuid())
                        || p.name().equals(refreshed.name()));
                profiles.add(refreshed);
                profileService.saveProfiles(profiles);
            } catch (java.io.IOException ex) {
                // Non-fatal
            }

            updateAvatar(refreshed);
            statusLabel.setText("Launching " + selected.id() + "...");
            doLaunch(selected, gameDir, refreshed, false);
        });
        refreshTask.setOnFailed(e -> {
            statusLabel.setText("Launching " + selected.id() + " (profile refresh failed)...");
            doLaunch(selected, gameDir, profile, false);
        });
        var thread = new Thread(refreshTask, "ely-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void doLaunch(MinecraftVersion selected, GameDirectory gameDir,
                          GameProfile profile, boolean isRetry) {
        Task<LaunchResult> launchTask = new Task<>() {
            @Override
            protected LaunchResult call() throws Exception {
                return launchService.launch(currentlySelectedMetadata, gameDir, profile);
            }
        };
        launchTask.setOnSucceeded(e -> {
            LaunchResult result = launchTask.getValue();
            if (result.isSuccess()) {
                statusLabel.setText("Minecraft " + selected.id() + " is running");
                Stage launcherStage = (Stage) root.getScene().getWindow();
                launcherStage.hide();
                monitorProcess(result.process().orElseThrow(), selected.id(), launcherStage);
            } else if (result.status() == LaunchResult.Status.FILE_CHECK_FAILED && !isRetry) {
                statusLabel.setText("Files missing, installing " + selected.id() + "...");
                startInstallation(selected, gameDir, profile);
            } else if (result.status() == LaunchResult.Status.JAVA_NOT_FOUND
                    && !javaDownloadAttempted && needsJava8(currentlySelectedMetadata)) {
                javaDownloadAttempted = true;
                statusLabel.setText("Java 8 required. Downloading JRE 8...");
                downloadAndInstallJava8(selected, gameDir, profile);
            } else {
                String msg = "Launch failed: " + result.message();
                statusLabel.setText(msg);
                playButton.setDisable(false);
                Stage launcherStage = (Stage) root.getScene().getWindow();
                ErrorDialog.show(launcherStage, "Launch Failed",
                        result.status() + ": " + result.message());
            }
        });
        launchTask.setOnFailed(e -> {
            String msg = "Launch error: " + launchTask.getException().getMessage();
            statusLabel.setText(msg);
            playButton.setDisable(false);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage, "Launch Error",
                    launchTask.getException().getClass().getSimpleName() + ": "
                            + launchTask.getException().getMessage());
        });
        var thread = new Thread(launchTask, "launch");
        thread.setDaemon(true);
        thread.start();
    }

    private void startInstallation(MinecraftVersion selected, GameDirectory gameDir,
                                   GameProfile profile) {
        statusLabel.setText("Installing " + selected.id() + "...");

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.show();

        Task<InstallationResult> installTask = new Task<>() {
            @Override
            protected InstallationResult call() throws Exception {
                return installationService.install(
                        selected, currentlySelectedMetadata, gameDir, progressDialog);
            }
        };
        installTask.setOnSucceeded(e -> {
            InstallationResult result = installTask.getValue();
            if (result.hasFailures()) {
                statusLabel.setText("Installation completed with "
                        + result.failed() + " failures");
                playButton.setDisable(false);
                return;
            }
            statusLabel.setText("Installation complete. Launching " + selected.id() + "...");
            doLaunch(selected, gameDir, profile, true);
        });
        installTask.setOnFailed(e -> {
            statusLabel.setText("Installation failed: " + installTask.getException().getMessage());
            playButton.setDisable(false);
        });
        var thread = new Thread(installTask, "install");
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
                    playButton.setDisable(false);
                });
            } catch (InterruptedException e) {
                javafx.application.Platform.runLater(() -> {
                    launcherStage.show();
                    statusLabel.setText("Process monitoring interrupted");
                    playButton.setDisable(false);
                });
            }
        }, "mc-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    // ------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
