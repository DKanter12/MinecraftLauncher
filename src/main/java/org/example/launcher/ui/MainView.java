package org.example.launcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
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

import org.example.launcher.distribution.DistributionSources;
import org.example.launcher.distribution.ServerAuthService;
import org.example.launcher.distribution.ServerSession;
import org.example.launcher.distribution.api.LauncherServerApi;
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
import org.example.launcher.net.UrlFetcher;
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
import org.example.launcher.build.BuildCreator;
import org.example.launcher.build.BuildRequest;
import org.example.launcher.service.modloader.ModdedProfileVerificationService;
import org.example.launcher.service.modloader.ModdedVersionService;
import org.example.launcher.update.AppVersion;
import org.example.launcher.update.LauncherUpdate;
import org.example.launcher.update.UpdateService;
import org.example.launcher.i18n.Lang;
import org.example.launcher.version.StandardVersionType;
import org.example.launcher.version.VersionType;
import org.example.launcher.version.VersionTypeRegistry;

/**
 * Строит главное окно лаунчера и управляет им.
 * <p>
 * Раскладка (BorderPane):
 * <pre>
 * +--------+--------------------------------------------------+
 * | Навиг. | Верхняя панель: заголовок + поиск + пилюля ак-та |
 * | панель +--------------------------------------------------+
 * | Инст.  | Виды (центральный стек):                          |
 * | Проф.  |  Мои инстансы — чипы фильтров + карточки          |
 * | Настр. |  Профили — управление аккаунтами                  |
 * |        |  Настройки — папки, Java, сервер, о программе     |
 * |        +--------------------------------------------------+
 * |        | Подвал: статус + информация о лаунчере            |
 * +--------+--------------------------------------------------+
 * </pre>
 * <p>
 * Всё — это инстанс (ванильный или модовый), у каждого свой
 * игровой каталог. Новые инстансы создаются через один простой
 * диалог (имя → чипы загрузчиков → список версий → список версий
 * загрузчика); манифест Mojang подгружается лишь в фоне,
 * чтобы питать этот диалог.
 */
public class MainView {

    private static final Logger LOG = Logger.getLogger(MainView.class.getName());

    private static final String APP_VERSION = AppVersion.BUILT_IN;

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
    private final BuildCreator buildCreator;

    private BorderPane root;
    private Label statusLabel;
    private boolean versionsLoadFailed = false;

    // Навигационная панель + виды
    private enum View {
        INSTANCES, PROFILES, SETTINGS
    }

    private View currentView = View.INSTANCES;
    private final List<Button> navButtons = new ArrayList<>();
    private StackPane viewStack;
    private Label viewTitleLabel;
    private TextField instanceSearchField;

    // Вид инстансов (сетка карточек)
    private FlowPane instanceCards;
    private ScrollPane instancesScroll;
    private HBox instanceFilterChips;
    private ToggleGroup instanceFilterGroup;
    private String selectedInstanceId;
    private String instanceSearchText = "";
    private ModLoaderType instanceLoaderFilter;

    // Остальные виды
    private VBox instancesView;
    private VBox profilesView;
    private VBox profilesBox;
    private VBox settingsView;
    private ComboBox<Lang.Language> languageCombo;
    private TextField javaPathField;
    private Label serverSessionLabel;
    private Label footerVersionLabel;
    private Label currentVersionLabel;
    private Label updateStatusLabel;
    private TextField buildsGitField;
    private Label buildsStatusLabel;
    private ComboBox<DistributionSources.BuildsSource> buildsModeCombo;
    private TextField yandexLinkField;
    private TextField buildsTokenField;
    private VBox buildsGitBox;
    private VBox buildsYandexBox;

    // Защита от повторного запуска: попытка запуска блокирует все кнопки «Играть»
    private boolean versionPlayInFlight = false;
    /** Инстанс, проходящий запуск, если есть (одиночный запуск). */
    private String launchingProfileId;
    private String launchingStatusText = Lang.tr("launch.launching");

    // Инстансы (сетка карточек)
    private List<ModdedProfile> moddedProfiles = List.of();

    // Бэкенд распространения сборок для входа на сервер. Локальный
    // UI сборок удалён; библиотека distribution под ним остаётся для
    // переиспользования (см. org.example.launcher.distribution).
    private ServerAuthService serverAuthService;
    private ServerSession serverSession;
    private Button serverButton;

    // Состояние запуска инстанса
    private boolean javaDownloadAttempted = false;

    // Аккаунт
    private ComboBox<GameProfile> accountCombo;
    private Button addAccountButton;
    private ImageView avatarView;
    private GameProfile selectedProfile;
    private boolean suppressSelectionListener = false;
    private Timer elyRefreshTimer;

    /** Версии манифеста Mojang — питают диалог создания. */
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
                    ModdedProfileVerificationService profileVerificationService,
                    BuildCreator buildCreator) {
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
        this.buildCreator = buildCreator;
        serverSession = null;
        applyDistributionSettings();
        serverSession = serverAuthService.restoreSession().orElse(null);
        buildView();
    }

    public BorderPane getView() {
        return root;
    }

    /**
     * (Пере)привязывает бэкенд сборок из настроек: git-бэкенд, когда
     * задана ссылка на репозиторий сборок, иначе локальный режим.
     * Смена бэкенда сбрасывает прошлую серверную сессию — её
     * токен принадлежит другому бэкенду.
     */
    private void applyDistributionSettings() {
        String mode = "YANDEX";
        String gitUrl = null;
        String yandexLink = DistributionSources.YANDEX_BUILDS_LINK_DEFAULT;
        String token = "";
        try {
            mode = preferences.getBuildsSourceMode().orElse("YANDEX");
            gitUrl = preferences.getBuildsGitUrl().orElse(null);
            String savedLink = preferences.getYandexDiskLink().orElse(null);
            if (savedLink != null && !savedLink.isBlank()) {
                yandexLink = savedLink;
            }
            token = preferences.getBuildsToken().orElse("");
        } catch (IOException ignored) {
            // оставляем умолчания: yandex со встроенной ссылкой
        }
        LauncherServerApi backend;
        if ("GITHUB".equalsIgnoreCase(mode)) {
            backend = DistributionSources.createBuildsBackend(gitUrl);
        } else if ("LOCAL".equalsIgnoreCase(mode)) {
            backend = new OfflineLauncherServerApi();
        } else {
            backend = DistributionSources.createYandexBackend(token, yandexLink);
        }
        serverAuthService = new ServerAuthService(backend,
                GameDirectory.defaultDirectory().root()
                        .resolve(ServerAuthService.SESSION_FILE_NAME));
        if (serverSession != null) {
            try {
                serverAuthService.logout();
            } catch (IOException ignored) {
                // файл токена уже удалён — нормально
            }
            serverSession = null;
            updateServerButtonText();
        }
    }

    // ------------------------------------------------------------------
    //  Построение UI
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

    // --- Верхняя панель ---

    private HBox buildTopBar() {
        viewTitleLabel = new Label(Lang.tr("nav.instances"));
        viewTitleLabel.getStyleClass().add("view-title");

        instanceSearchField = new TextField();
        instanceSearchField.setPromptText(Lang.tr("search.instances"));
        instanceSearchField.getStyleClass().add("search-field");
        instanceSearchField.setPrefWidth(260);
        instanceSearchField.textProperty().addListener((obs, old, val) -> {
            instanceSearchText = val == null ? "" : val.trim().toLowerCase();
            refreshInstanceCards();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Виджет аккаунта (быстрый переключатель; полное управление — в Профилях)
        avatarView = new ImageView();
        avatarView.setFitWidth(28);
        avatarView.setFitHeight(28);
        avatarView.setPreserveRatio(true);
        avatarView.setVisible(false);

        accountCombo = new ComboBox<>();
        accountCombo.setPrefWidth(180);
        accountCombo.setPromptText(Lang.tr("account.none"));
        accountCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(GameProfile profile) {
                if (profile == null) return "";
                String type = profile.isElyBy() ? Lang.tr("account.type.ely")
                        : Lang.tr("account.type.offline");
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
        accountCombo.setTooltip(new Tooltip(Lang.tr("account.tip")));
        // Строки списка: правый клик показывает «Использовать / Удалить», как в большинстве лаунчеров
        accountCombo.setCellFactory(list -> {
            ListCell<GameProfile> cell = new ListCell<>() {
                private ContextMenu menu;

                @Override
                protected void updateItem(GameProfile item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                        setOnContextMenuRequested(null);
                        menu = null;
                    } else {
                        String type = item.isElyBy() ? "Ely.by"
                                : Lang.tr("account.offline");
                        setText(item.name() + "  [" + type + "]");
                        ContextMenu m = new ContextMenu();
                        MenuItem useItem = new MenuItem(Lang.tr("account.menu.use"));
                        useItem.setOnAction(e -> {
                            accountCombo.getSelectionModel().select(item);
                            onUseAccount(item);
                        });
                        MenuItem deleteItem = new MenuItem(
                                Lang.tr("account.menu.delete"));
                        deleteItem.getStyleClass().add("menu-item-danger");
                        deleteItem.setOnAction(e -> onDeleteAccount(item));
                        m.getItems().addAll(useItem, deleteItem);
                        menu = m;
                        setContextMenu(m);
                        setOnContextMenuRequested(ev -> {
                            m.show(this, ev.getScreenX(), ev.getScreenY());
                            ev.consume();
                        });
                    }
                }
            };
            return cell;
        });
        // Выбранное значение (кнопка-ячейка): правый клик удаляет без открытия списка
        accountCombo.setButtonCell(new ListCell<>() {
            private ContextMenu menu;

            @Override
            protected void updateItem(GameProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                    setOnContextMenuRequested(null);
                    menu = null;
                } else {
                    String type = item.isElyBy() ? "Ely.by"
                            : Lang.tr("account.offline");
                    setText(item.name() + "  [" + type + "]");
                    ContextMenu m = new ContextMenu();
                    MenuItem deleteItem = new MenuItem(
                            Lang.tr("account.menu.delete.name", item.name()));
                    deleteItem.getStyleClass().add("menu-item-danger");
                    deleteItem.setOnAction(e -> onDeleteAccount(item));
                    m.getItems().add(deleteItem);
                    menu = m;
                    setContextMenu(m);
                    setOnContextMenuRequested(ev -> {
                        m.show(this, ev.getScreenX(), ev.getScreenY());
                        ev.consume();
                    });
                }
            }
        });

        addAccountButton = new Button(Lang.tr("account.add"));
        addAccountButton.getStyleClass().add("quick-select-button");
        addAccountButton.setStyle("-fx-font-size: 12px;");
        addAccountButton.setOnAction(e -> onAddAccount());
        addAccountButton.setTooltip(new Tooltip(Lang.tr("account.add.tip")));

        serverButton = new Button(Lang.tr("server.button"));
        serverButton.getStyleClass().add("browse-java-button");
        serverButton.setStyle("-fx-font-size: 11px;");
        serverButton.setOnAction(e -> onServerLogin());
        updateServerButtonText();

        HBox accountBox = new HBox(8, avatarView, accountCombo,
                addAccountButton, serverButton);
        accountBox.setAlignment(Pos.CENTER);
        accountBox.getStyleClass().add("account-pill");

        HBox topBar = new HBox(16, viewTitleLabel, instanceSearchField,
                spacer, accountBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.getStyleClass().add("topbar");
        return topBar;
    }

    // --- Навигационная панель ---

    private VBox buildNavRail() {
        VBox rail = new VBox(6);
        rail.setPrefWidth(190);
        rail.setPadding(new Insets(16, 10, 16, 10));
        rail.getStyleClass().add("nav-rail");
        addNavButton(rail, Lang.tr("nav.instances"), View.INSTANCES);
        addNavButton(rail, Lang.tr("nav.profiles"), View.PROFILES);
        addNavButton(rail, Lang.tr("nav.settings"), View.SETTINGS);
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

    // --- Подвал (статус слева, информация о лаунчере справа) ---

    private HBox buildFooter() {
        statusLabel = new Label(Lang.tr("status.loading"));
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        footerVersionLabel = new Label(Lang.tr("footer.version", APP_VERSION));
        footerVersionLabel.getStyleClass().add("footer-version");

        HBox footer = new HBox(12, statusLabel, footerVersionLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 20, 8, 20));
        footer.getStyleClass().add("footer");
        return footer;
    }

    // --- Вид инстансов: чипы фильтров + сетка карточек ---

    private VBox buildInstancesView() {
        instanceFilterChips = new HBox(8);
        instanceFilterChips.getStyleClass().add("filter-row");
        instanceFilterGroup = new ToggleGroup();
        addInstanceFilterChip(Lang.tr("filter.all"), null);
        for (ModLoaderType loader : ModLoaderType.values()) {
            addInstanceFilterChip(loader.displayName(), loader);
        }
        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        Button createBuildButton = new Button(Lang.tr("instances.create"));
        createBuildButton.getStyleClass().add("quick-select-button");
        createBuildButton.setTooltip(new Tooltip(Lang.tr("instances.create.tip")));
        createBuildButton.setOnAction(e -> onNewInstance());
        instanceFilterChips.getChildren().addAll(filterSpacer,
                createBuildButton);

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

    private ToggleButton makeInstanceFilterChip(String text,
                                                  ModLoaderType loader) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("filter-button");
        chip.setToggleGroup(instanceFilterGroup);
        chip.setUserData(loader);
        chip.setSelected(loader == null);
        chip.setOnAction(e -> {
            // Фильтр с одиночным выбором не бывает пустым: повторный клик
            // по активному чипу сохраняет его
            if (!chip.isSelected()) {
                chip.setSelected(true);
                return;
            }
            instanceLoaderFilter = (ModLoaderType) chip.getUserData();
            refreshInstanceCards();
        });
        return chip;
    }

    private void addInstanceFilterChip(String text, ModLoaderType loader) {
        instanceFilterChips.getChildren().add(
                makeInstanceFilterChip(text, loader));
    }

    /** Перестраивает чипы фильтров на текущем языке. */
    private void rebuildInstanceFilterChips() {
        ModLoaderType keep = instanceLoaderFilter;
        instanceFilterGroup = new ToggleGroup();
        List<ToggleButton> chips = new ArrayList<>();
        chips.add(makeInstanceFilterChip(Lang.tr("filter.all"), null));
        for (ModLoaderType loader : ModLoaderType.values()) {
            chips.add(makeInstanceFilterChip(loader.displayName(), loader));
        }
        instanceFilterChips.getChildren()
                .removeIf(node -> node instanceof ToggleButton);
        instanceFilterChips.getChildren().addAll(0, chips);
        instanceLoaderFilter = keep;
        syncInstanceFilterChips();
    }

    /** Переключает центральный вид и обновляет заголовок, поиск и навигацию. */
    private void showView(View view) {
        currentView = view;
        instancesView.setVisible(view == View.INSTANCES);
        instancesView.setManaged(view == View.INSTANCES);
        profilesView.setVisible(view == View.PROFILES);
        profilesView.setManaged(view == View.PROFILES);
        settingsView.setVisible(view == View.SETTINGS);
        settingsView.setManaged(view == View.SETTINGS);
        viewTitleLabel.setText(switch (view) {
            case INSTANCES -> Lang.tr("nav.instances");
            case PROFILES -> Lang.tr("nav.profiles");
            case SETTINGS -> Lang.tr("nav.settings");
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

    /** Выбранный инстанс (сетка карточек) или null. */
    private ModdedProfile selectedInstance() {
        if (selectedInstanceId == null) return null;
        for (ModdedProfile p : moddedProfiles) {
            if (p.id().equals(selectedInstanceId)) return p;
        }
        return null;
    }

    /**
     * Переходит к свежесозданному инстансу: перезагружает список, двигает
     * фильтр загрузчиков к его семейству (чтобы карточка была видна, даже
     * если было отфильтровано другое семейство), очищает поиск, открывает
     * вид инстансов и раскрывает карточку.
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

    /** Отражает {@link #instanceLoaderFilter} в чипах фильтров. */
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

    /**
     * Выбирает карточку на месте (без перестройки, чтобы дабл-клик для игры
     * продолжал работать); полные перестройки — после изменения данных.
     */
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
     * Вновь включает все кнопки «Играть» после выхода попытки запуска в
     * терминальное состояние (запущено, ошибка, починка или выход из игры).
     */
    private void refreshLaunchButtons() {
        versionPlayInFlight = false;
        launchingProfileId = null;
        refreshInstanceCards();
    }

    /**
     * Показывает стадию запуска на карточке инстанса (все вызовы —
     * в потоке FX).
     */
    private void setLaunchStatus(String profileId, String text) {
        launchingProfileId = profileId;
        launchingStatusText = text;
        refreshInstanceCards();
    }

    /** Перестраивает сетку карточек по текущим фильтру + поиску. */
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

        Button play = new Button(
                launching ? launchingStatusText : Lang.tr("button.play"));
        play.getStyleClass().add("card-play");
        play.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(play, Priority.ALWAYS);
        play.setDisable(versionPlayInFlight || launching);
        play.setOnAction(e -> {
            selectInstance(profile.id());
            startInstanceLaunch(profile);
        });

        Button folder = new Button(Lang.tr("button.folder"));
        folder.getStyleClass().add("card-ghost-button");
        folder.setOnAction(e -> onOpenProfileFolder(profile));

        Button edit = new Button(Lang.tr("button.edit"));
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

    /** Раскрытый блок выбранной карточки: факты + сборки + удаление. */
    private VBox buildInstanceDetails(ModdedProfile profile) {
        VBox details = new VBox(3);
        details.getStyleClass().add("instance-card-details");
        details.getChildren().add(detailLine(profile.isVanilla()
                ? Lang.tr("card.version.vanilla", profile.minecraftVersion())
                : "MC " + profile.minecraftVersion() + "  ·  "
                        + profile.loaderType().displayName() + " "
                        + profile.loaderVersion()));
        Path dir = moddedProfileService.resolveGameDir(profile);
        details.getChildren().add(detailLine(Lang.tr("card.gamedir",
                shortGameDir(dir))));
        details.getChildren().add(detailLine(Lang.tr("card.memory",
                ModdedProfileService.formatMemory(profile.memoryMb()))));
        details.getChildren().add(detailLine(Lang.tr("card.jvm",
                profile.extraJvmArgs().isEmpty() ? Lang.tr("card.none")
                        : String.join(" ", profile.extraJvmArgs()))));
        details.getChildren().add(detailLine(Lang.tr("card.created",
                profile.createdTime().map(MainView::formatDate).orElse("—"))));

        Button delete = new Button(Lang.tr("button.delete.instance"));
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
        Label text = new Label(Lang.tr("instances.add"));
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

    /** Акцентный стиль по семейству загрузчиков, как в эталонном макете. */
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
     * Буквы иконок: первая буква, две там, где коллизия
     * (у Fabric/Forge общая «F»).
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
        if (mods < 0) {
            return profile.loaderType().displayName() + " "
                    + profile.minecraftVersion();
        }
        return Lang.tr("card.mods", profile.loaderType().displayName(),
                profile.minecraftVersion(), mods);
    }

    private String cardPlayedLine(ModdedProfile profile) {
        if (profile.lastPlayedTime().isPresent()) {
            return Lang.tr("card.played",
                    relativeTime(profile.lastPlayedTime().get()));
        }
        return profile.createdTime()
                .map(t -> Lang.tr("card.created", relativeTime(t)))
                .orElseGet(() -> Lang.tr("card.never"));
    }

    /** Считает jar-моды; отрицательное — когда не читается. */
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
        if (minutes < 1) return Lang.tr("time.now");
        if (minutes < 60) {
            return Lang.tr("time.minutes", minutes);
        }
        long hours = age.toHours();
        if (hours < 24) {
            return Lang.tr("time.hours", hours);
        }
        long days = age.toDays();
        if (days == 1) return Lang.tr("time.yesterday");
        if (days < 7) return Lang.tr("time.days", days);
        long weeks = days / 7;
        if (weeks < 5) {
            return Lang.tr("time.weeks", weeks);
        }
        return formatDate(time);
    }

    private static String formatDate(OffsetDateTime time) {
        return DATE_FORMATTER.format(time);
    }

    // ------------------------------------------------------------------
    //  Вид профилей: управление аккаунтами
    // ------------------------------------------------------------------

    private VBox buildProfilesView() {
        Button addAccount = new Button(Lang.tr("account.add"));
        addAccount.getStyleClass().add("quick-select-button");
        addAccount.setTooltip(new Tooltip(Lang.tr("account.add.tip")));
        addAccount.setOnAction(e -> onAddAccount());
        Button server = new Button(Lang.tr("server.button"));
        server.getStyleClass().add("quick-select-button");
        server.setTooltip(new Tooltip(Lang.tr("profiles.server.tip")));
        server.setOnAction(e -> {
            onServerLogin();
            refreshProfilesView();
        });
        Label manageHint = new Label(Lang.tr("profiles.hint.top"));
        manageHint.getStyleClass().add("quick-select-label");
        HBox buttons = new HBox(8, addAccount, server, manageHint);
        buttons.setAlignment(Pos.CENTER_LEFT);

        profilesBox = new VBox(10);
        VBox.setVgrow(profilesBox, Priority.ALWAYS);

        Label hint = new Label(Lang.tr("profiles.hint"));
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
            statusLabel.setText(Lang.tr("profiles.load.failed",
                    e.getMessage()));
            return;
        }

        String serverText = serverSession == null
                ? Lang.tr("profiles.server.offline")
                : Lang.tr("profiles.server.on", serverSession.accountName()
                        + (serverSession.isAdmin()
                                ? " (" + Lang.tr("server.role.admin") + ")"
                                : " (" + Lang.tr("server.role.user") + ")"));
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
                    + (active ? Lang.tr("profiles.active") : ""));
            name.getStyleClass().add("account-row-name");
            String sub = account.isElyBy() ? "Ely.by"
                    : Lang.tr("account.offline");
            if (account.uuid().isPresent()) {
                String uuid = account.uuid().get();
                sub += "  ·  " + uuid.substring(0, Math.min(8, uuid.length()));
            }
            Label subLabel = new Label(sub);
            subLabel.getStyleClass().add("account-row-sub");
            VBox texts = new VBox(2, name, subLabel);
            HBox.setHgrow(texts, Priority.ALWAYS);
            Button use = new Button(
                    active ? Lang.tr("profiles.is.active")
                            : Lang.tr("account.menu.use"));
            use.getStyleClass().add("card-ghost-button");
            use.setDisable(active);
            use.setOnAction(e -> onUseAccount(account));
            Button delete = new Button(Lang.tr("button.delete"));
            delete.getStyleClass().add("card-danger-button");
            delete.setOnAction(e -> onDeleteAccount(account));
            HBox row = new HBox(10, tile, texts, use, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("account-row");
            if (active) {
                row.getStyleClass().add("account-row-active");
            }
            // Строка с правым кликом — контекстное меню, как в большинстве лаунчеров
            ContextMenu rowMenu = new ContextMenu();
            MenuItem rowUse = new MenuItem(Lang.tr("profiles.menu.use"));
            rowUse.setDisable(active);
            rowUse.setOnAction(e -> onUseAccount(account));
            MenuItem rowDelete = new MenuItem(
                    Lang.tr("account.menu.delete"));
            rowDelete.getStyleClass().add("menu-item-danger");
            rowDelete.setOnAction(e -> onDeleteAccount(account));
            rowMenu.getItems().addAll(rowUse, rowDelete);
            row.setOnContextMenuRequested(e -> {
                rowMenu.show(row, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !active) onUseAccount(account);
            });
            profilesBox.getChildren().add(row);
        }
    }

    private void onUseAccount(GameProfile account) {
        selectedProfile = account;
        saveLastSelectedAccount(account.name());
        updateAvatar(account);
        refreshAccounts();
        refreshProfilesView();
        statusLabel.setText(Lang.tr("profiles.status.active", account.name()));
    }

    private void onDeleteAccount(GameProfile account) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(Lang.tr("profiles.delete.title"));
        alert.setHeaderText(
                Lang.tr("profiles.delete.header", account.name()));
        alert.setContentText(Lang.tr("profiles.delete.text"));
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
                    statusLabel.setText(Lang.tr("profiles.delete.done",
                            account.name()));
                } catch (IOException e) {
                    statusLabel.setText(Lang.tr("profiles.delete.failed",
                            e.getMessage()));
                }
            }
        });
    }

    // ------------------------------------------------------------------
    //  Вид настроек: папки, переопределение Java, сессия сервера
    // ------------------------------------------------------------------

    private VBox buildSettingsView() {
        Label languageTitle = new Label(Lang.tr("settings.language"));
        languageTitle.getStyleClass().add("section-title");
        languageCombo = new ComboBox<>();
        languageCombo.getItems().setAll(Lang.Language.values());
        languageCombo.getSelectionModel().select(Lang.getLanguage());
        languageCombo.setMaxWidth(Double.MAX_VALUE);
        languageCombo.setOnAction(e -> onApplyLanguage());
        Label languageHint = new Label(Lang.tr("settings.language.hint"));
        languageHint.getStyleClass().add("quick-select-label");
        languageHint.setWrapText(true);
        VBox languageBox = new VBox(6, languageTitle, languageCombo,
                languageHint);
        languageBox.getStyleClass().add("settings-group");

        Label dirTitle = new Label(Lang.tr("settings.dir"));
        dirTitle.getStyleClass().add("section-title");
        Label dirPath = new Label(
                GameDirectory.defaultDirectory().root().toString());
        dirPath.getStyleClass().add("settings-value");
        dirPath.setWrapText(true);
        Button openDir = new Button(Lang.tr("button.openfolder"));
        openDir.getStyleClass().add("quick-select-button");
        openDir.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(
                        GameDirectory.defaultDirectory().root().toFile());
            } catch (Exception ex) {
                statusLabel.setText(Lang.tr("settings.open.failed",
                        ex.getMessage()));
            }
        });
        VBox dirBox = new VBox(6, dirTitle, dirPath, openDir);
        dirBox.getStyleClass().add("settings-group");

        Label javaTitle = new Label(Lang.tr("settings.java"));
        javaTitle.getStyleClass().add("section-title");
        javaPathField = new TextField();
        javaPathField.setPromptText(Lang.tr("settings.java.auto"));
        javaPathField.getStyleClass().add("search-field");
        javaPathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(javaPathField, Priority.ALWAYS);
        Button browse = new Button(Lang.tr("button.browse"));
        browse.getStyleClass().add("quick-select-button");
        browse.setOnAction(e -> onBrowseJava());
        Button apply = new Button(Lang.tr("button.apply"));
        apply.getStyleClass().add("quick-select-button");
        apply.setOnAction(e -> onApplyJava());
        Button clear = new Button(Lang.tr("button.clear"));
        clear.getStyleClass().add("quick-select-button");
        clear.setOnAction(e -> {
            javaPathField.clear();
            onApplyJava();
        });
        HBox javaRow = new HBox(8, javaPathField, browse, apply, clear);
        javaRow.setAlignment(Pos.CENTER_LEFT);
        Label javaHint = new Label(Lang.tr("settings.java.hint"));
        javaHint.getStyleClass().add("quick-select-label");
        javaHint.setWrapText(true);
        VBox javaBox = new VBox(6, javaTitle, javaRow, javaHint);
        javaBox.getStyleClass().add("settings-group");

        Label serverTitle = new Label(Lang.tr("server.title"));
        serverTitle.getStyleClass().add("section-title");
        serverSessionLabel = new Label();
        serverSessionLabel.getStyleClass().add("settings-value");
        serverSessionLabel.setWrapText(true);
        Button serverButton = new Button(Lang.tr("settings.server.sign"));
        serverButton.getStyleClass().add("quick-select-button");
        serverButton.setOnAction(e -> {
            onServerLogin();
            refreshSettingsView();
        });
        VBox serverBox = new VBox(6, serverTitle, serverSessionLabel,
                serverButton);
        serverBox.getStyleClass().add("settings-group");

        Label updatesTitle = new Label(Lang.tr("settings.updates"));
        updatesTitle.getStyleClass().add("section-title");
        currentVersionLabel = new Label();
        currentVersionLabel.getStyleClass().add("settings-value");
        updateStatusLabel = new Label(Lang.tr("settings.updates.hint"));
        updateStatusLabel.getStyleClass().add("quick-select-label");
        updateStatusLabel.setWrapText(true);
        Button checkNow = new Button(Lang.tr("button.checknow"));
        checkNow.getStyleClass().add("quick-select-button");
        checkNow.setOnAction(e -> onCheckUpdatesNow());
        VBox updatesBox = new VBox(6, updatesTitle, currentVersionLabel,
                updateStatusLabel, checkNow);
        updatesBox.getStyleClass().add("settings-group");

        Label buildsTitle = new Label(Lang.tr("settings.builds"));
        buildsTitle.getStyleClass().add("section-title");
        buildsModeCombo = new ComboBox<>();
        buildsModeCombo.getItems().setAll(
                DistributionSources.BuildsSource.values());
        buildsModeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(DistributionSources.BuildsSource mode) {
                if (mode == null) return "";
                return switch (mode) {
                    case LOCAL -> Lang.tr("settings.builds.local");
                    case GITHUB -> Lang.tr("settings.builds.github");
                    case YANDEX -> Lang.tr("settings.builds.yandex");
                };
            }

            @Override
            public DistributionSources.BuildsSource fromString(String string) {
                return null;
            }
        });
        buildsModeCombo.setMaxWidth(Double.MAX_VALUE);
        buildsStatusLabel = new Label();
        buildsStatusLabel.getStyleClass().add("settings-value");
        buildsStatusLabel.setWrapText(true);
        buildsGitField = new TextField();
        buildsGitField.setPromptText(Lang.tr("settings.git.prompt"));
        buildsGitField.getStyleClass().add("search-field");
        buildsGitField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(buildsGitField, Priority.ALWAYS);
        yandexLinkField = new TextField();
        yandexLinkField.setPromptText(Lang.tr("settings.yandex.prompt"));
        yandexLinkField.getStyleClass().add("search-field");
        yandexLinkField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(yandexLinkField, Priority.ALWAYS);
        buildsTokenField = new PasswordField();
        buildsTokenField.setPromptText(Lang.tr("settings.token.prompt"));
        buildsTokenField.getStyleClass().add("search-field");
        buildsTokenField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(buildsTokenField, Priority.ALWAYS);
        Button buildsApply = new Button(Lang.tr("button.apply"));
        buildsApply.getStyleClass().add("quick-select-button");
        buildsApply.setOnAction(e -> onApplyBuildsSource());
        HBox buildsRow = new HBox(8, buildsGitField, buildsApply);
        buildsRow.setAlignment(Pos.CENTER_LEFT);
        HBox yandexRow = new HBox(8, yandexLinkField, buildsTokenField);
        yandexRow.setAlignment(Pos.CENTER_LEFT);
        buildsGitBox = new VBox(6, buildsRow);
        buildsYandexBox = new VBox(6, yandexRow);
        VBox buildsBox = new VBox(6, buildsTitle, buildsModeCombo,
                buildsStatusLabel, buildsGitBox, buildsYandexBox);
        buildsBox.getStyleClass().add("settings-group");
        buildsModeCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> updateBuildsRows());

        Label aboutTitle = new Label(Lang.tr("settings.about"));
        aboutTitle.getStyleClass().add("section-title");
        Label about = new Label(Lang.tr("settings.about.text", APP_VERSION));
        about.getStyleClass().add("settings-value");
        about.setWrapText(true);
        VBox aboutBox = new VBox(6, aboutTitle, about);
        aboutBox.getStyleClass().add("settings-group");

        VBox view = new VBox(12, languageBox, dirBox, javaBox, serverBox,
                updatesBox, buildsBox, aboutBox);
        view.setPadding(new Insets(16));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private void refreshSettingsView() {
        if (serverSessionLabel == null) return;
        if (languageCombo != null
                && languageCombo.getSelectionModel().getSelectedItem()
                        != Lang.getLanguage()) {
            languageCombo.getSelectionModel().select(Lang.getLanguage());
        }
        serverSessionLabel.setText(serverSession == null
                ? Lang.tr("settings.server.offline")
                : Lang.tr("settings.server.on", serverSession.accountName(),
                        serverSession.isAdmin()
                                ? Lang.tr("server.role.admin")
                                : Lang.tr("server.role.user")));
        if (currentVersionLabel != null) {
            currentVersionLabel.setText(
                    Lang.tr("settings.current", AppVersion.current()));
        }
        if (buildsStatusLabel != null) {
            try {
                String mode = preferences.getBuildsSourceMode()
                        .orElse("YANDEX");
                DistributionSources.BuildsSource selected;
                try {
                    selected = DistributionSources.BuildsSource
                            .valueOf(mode);
                } catch (IllegalArgumentException e) {
                    selected = DistributionSources.BuildsSource.YANDEX;
                }
                buildsModeCombo.getSelectionModel().select(selected);
                String gitUrl =
                        preferences.getBuildsGitUrl().orElse(null);
                if (buildsGitField != null && gitUrl != null) {
                    buildsGitField.setText(gitUrl);
                }
                String yandexLink = preferences.getYandexDiskLink().orElse(
                        DistributionSources.YANDEX_BUILDS_LINK_DEFAULT);
                if (yandexLinkField != null
                        && yandexLinkField.getText().isBlank()) {
                    yandexLinkField.setText(yandexLink);
                }
                updateBuildsRows();
                updateBuildsStatus();
            } catch (IOException e) {
                buildsStatusLabel.setText(
                        Lang.tr("settings.builds.local.full"));
            }
        }
    }

    private void updateBuildsRows() {
        if (buildsModeCombo == null) return;
        DistributionSources.BuildsSource mode = buildsModeCombo
                .getSelectionModel().getSelectedItem();
        boolean git = mode == DistributionSources.BuildsSource.GITHUB;
        boolean yandex = mode == DistributionSources.BuildsSource.YANDEX;
        buildsGitBox.setVisible(git);
        buildsGitBox.setManaged(git);
        buildsYandexBox.setVisible(yandex);
        buildsYandexBox.setManaged(yandex);
    }

    private void updateBuildsStatus() {
        if (buildsStatusLabel == null || buildsModeCombo == null) return;
        DistributionSources.BuildsSource mode = buildsModeCombo
                .getSelectionModel().getSelectedItem();
        if (mode == null) {
            buildsStatusLabel.setText(Lang.tr("settings.builds.local.full"));
        } else {
            switch (mode) {
                case LOCAL -> buildsStatusLabel
                        .setText(Lang.tr("settings.builds.local.full"));
                case GITHUB -> {
                    String url = buildsGitField.getText();
                    buildsStatusLabel.setText(url == null || url.isBlank()
                            ? Lang.tr("settings.builds.git.empty")
                            : Lang.tr("settings.builds.git.on", url.trim()));
                }
                case YANDEX -> {
                    String link = yandexLinkField.getText();
                    buildsStatusLabel.setText(link == null || link.isBlank()
                            ? Lang.tr("settings.builds.yandex.empty")
                            : Lang.tr("settings.builds.yandex.on",
                                    link.trim()));
                }
            }
        }
    }

    /**
     * Перерисовывает все статические тексты на текущем языке (вызывается
     * сразу после переключения в настройках — перезапуск не нужен).
     * Диалоги читают строки при создании, поэтому подхватят язык
     * автоматически при следующем открытии.
     */
    private void applyLanguage() {
        for (Button nav : navButtons) {
            if (nav.getUserData() == View.INSTANCES) {
                nav.setText(Lang.tr("nav.instances"));
            } else if (nav.getUserData() == View.PROFILES) {
                nav.setText(Lang.tr("nav.profiles"));
            } else if (nav.getUserData() == View.SETTINGS) {
                nav.setText(Lang.tr("nav.settings"));
            }
        }
        instanceSearchField.setPromptText(Lang.tr("search.instances"));
        accountCombo.setPromptText(Lang.tr("account.none"));
        addAccountButton.setText(Lang.tr("account.add"));
        addAccountButton.setTooltip(new Tooltip(Lang.tr("account.add.tip")));
        updateServerButtonText();
        rebuildInstanceFilterChips();
        updateFooterVersion();
        refreshAccounts();
        showView(currentView);
        refreshSettingsView();
    }

    private void onApplyLanguage() {
        Lang.Language selected = languageCombo.getSelectionModel()
                .getSelectedItem();
        if (selected == null) {
            return;
        }
        Lang.setLanguage(selected);
        try {
            Lang.save(preferences);
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("settings.save.failed", e.getMessage()));
            return;
        }
        applyLanguage();
        statusLabel.setText(
                Lang.tr("settings.language.done", selected.displayName()));
    }

    private void onBrowseJava() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.tr("settings.browse.title"));
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
                    ? Lang.tr("settings.java.saved.auto")
                    : Lang.tr("settings.java.saved.override", path));
        } else {
            statusLabel.setText(Lang.tr("settings.java.unsupported"));
        }
    }

    /** Ручная проверка обновлений из настроек. */
    private void onCheckUpdatesNow() {
        updateStatusLabel.setText(Lang.tr("settings.checking"));
        Thread thread = new Thread(() -> {
            UpdateService.CheckResult result = checkForUpdates();
            Platform.runLater(() -> {
                updateStatusLabel.setText(result.detail());
                if (result.status() == UpdateService.Status.AVAILABLE
                        || result.status() == UpdateService.Status.STAGED) {
                    UpdateService service = updateService();
                    if (service != null) {
                        UpdateDialog.show(
                                (Stage) root.getScene().getWindow(), service,
                                result);
                    }
                }
                refreshSettingsView();
            });
        }, "update-check-manual");
        thread.setDaemon(true);
        thread.start();
    }

    /** Сохраняет ссылку на репозиторий сборок (приходит отдельно). */
    private void onApplyBuildsSource() {
        DistributionSources.BuildsSource mode = buildsModeCombo
                .getSelectionModel().getSelectedItem();
        if (mode == null) {
            mode = DistributionSources.BuildsSource.LOCAL;
        }
        String gitUrl = buildsGitField.getText() == null ? ""
                : buildsGitField.getText().trim();
        String yandexLink = yandexLinkField.getText() == null ? ""
                : yandexLinkField.getText().trim();
        String token = buildsTokenField.getText() == null ? ""
                : buildsTokenField.getText();
        try {
            preferences.setBuildsSourceMode(mode.name());
            preferences.setBuildsGitUrl(gitUrl);
            preferences.setYandexDiskLink(yandexLink);
            preferences.setBuildsToken(token);
            applyDistributionSettings();
            updateBuildsStatus();
            statusLabel.setText(Lang.tr("settings.builds.relogin",
                    buildsStatusLabel.getText()));
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("settings.save.failed", e.getMessage()));
        }
    }

    private UpdateService updateService() {
        try {
            return new UpdateService(new UrlFetcher(),
                    DistributionSources.UPDATE_MANIFEST_URL,
                    GameDirectory.defaultDirectory().root());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private UpdateService.CheckResult checkForUpdates() {
        UpdateService service = updateService();
        if (service == null) {
            return new UpdateService.CheckResult(
                    UpdateService.Status.FAILED, null,
                    Lang.tr("update.noservice"));
        }
        return service.check();
    }

    /**
     * Вход на сервер лаунчера (или выход). Диалог выполняет
     * весь процесс; после хранится только токен — принесённая им роль
     * решает, станут ли доступны административные функции (публикация
     * сборок).
     */
    private void onServerLogin() {
        ServerSession result = ServerLoginDialog.show(
                (Stage) root.getScene().getWindow(), serverAuthService, serverSession);
        boolean changed = result != serverSession;
        serverSession = result;
        updateServerButtonText();
        if (changed) {
            statusLabel.setText(serverSession == null
                    ? Lang.tr("server.signedout.status")
                    : Lang.tr("server.signedin.status",
                            serverSession.accountName(),
                            serverSession.isAdmin()
                                    ? " (" + Lang.tr("server.role.admin")
                                            + ")"
                                    : ""));
        }
        refreshProfilesView();
        refreshSettingsView();
    }

    private void updateServerButtonText() {
        if (serverButton == null) {
            return;
        }
        serverButton.setText(serverSession == null
                ? Lang.tr("server.button")
                : serverSession.isAdmin()
                        ? Lang.tr("server.button.admin",
                                serverSession.accountName())
                        : Lang.tr("server.button.session",
                                serverSession.accountName()));
    }

    // ------------------------------------------------------------------
    //  Асинхронно: загрузка списка версий (питает диалог «Новый инстанс»)
    // ------------------------------------------------------------------

    public void loadVersions() {
        refreshAccounts();
        // Мгновенная офлайн-поддержка: показываем уже созданные инстансы/сборки
        // сразу, чтобы они были видны, даже если загрузка манифеста позже
        // упрётся в таймаут (15–30 с) без интернета.
        refreshModdedProfiles(null);
        try {
            manifestVersions = loadCachedManifestVersions();
            if (!manifestVersions.isEmpty()) {
                versionsLoadFailed = false;
            }
        } catch (Exception ignored) {
        }
        startElyByRefreshTimer();
        statusLabel.setText(manifestVersions.isEmpty()
                ? Lang.tr("status.loading")
                : Lang.tr("versions.cached.loading",
                        manifestVersions.size()));

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

        checkForUpdatesOnStartup();
    }

    /**
     * При каждом запуске (с интернетом): спрашивает главную ветку о новой
     * версии лаунчера. Молчит, если актуально или офлайн; диалог
     * открывается, когда обновление доступно или подготовлено.
     */
    private void checkForUpdatesOnStartup() {
        Thread thread = new Thread(() -> {
            try {
                UpdateService.CheckResult result = checkForUpdates();
                Platform.runLater(() -> {
                    switch (result.status()) {
                        case AVAILABLE, STAGED -> {
                            statusLabel.setText(result.detail());
                            UpdateService service = updateService();
                            if (service != null) {
                                UpdateDialog.show((Stage) root.getScene()
                                        .getWindow(), service, result);
                            }
                        }
                        case FAILED -> statusLabel.setText(Lang.tr(
                                "update.check.failed", result.detail()));
                        default -> {
                            // актуально или офлайн — молчим
                        }
                    }
                });
            } catch (RuntimeException ignored) {
                // проверки обновлений не должны ломать старт
            }
        }, "update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void startElyByRefreshTimer() {
        if (elyRefreshTimer != null) {
            elyRefreshTimer.cancel();
        }
        elyRefreshTimer = new Timer("ely-refresh-timer", true);
        elyRefreshTimer.scheduleAtFixedRate(new TimerTask() {
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
            var updated = new ArrayList<GameProfile>();

            for (GameProfile p : profiles) {
                if (p.isElyBy() && p.uuid().isPresent()) {
                    GameProfile refreshed = elyAuthService.refreshProfile(p);
                    boolean nameChanged = !refreshed.name().equals(p.name());
                    if (nameChanged) {
                        LOG.fine("Ely.by nick changed: '" + p.name() + "' -> '" + refreshed.name() + "'");
                        nameChange = new String[]{p.name(), refreshed.name()};
                    }
                    if (nameChanged || !Objects.equals(
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
                Platform.runLater(() -> {
                    var items = accountCombo.getItems();
                    String selectedUuid = selectedProfile != null
                            ? selectedProfile.uuid().orElse(null) : null;

                    for (int i = 0; i < items.size(); i++) {
                        GameProfile old = items.get(i);
                        for (GameProfile up : updated) {
                            if (old.uuid().isPresent() && up.uuid().isPresent()
                                    && old.uuid().get().equals(up.uuid().get())
                                    && !old.name().equals(up.name())) {
                                LOG.fine("Ely.by combo item " + i + ": "
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
                                LOG.fine("Ely.by re-selected: " + p.name());
                                break;
                            }
                        }
                    }

                    if (selectedProfile != null) {
                        updateAvatar(selectedProfile);
                    }
                    if (finalNameChange != null) {
                        statusLabel.setText(Lang.tr("ely.nick",
                                finalNameChange[0], finalNameChange[1]));
                    }
                });
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Ely.by background refresh failed", e);
        }
    }

    private void onVersionsLoaded(VersionManifest manifest) {
        manifestVersions = manifest.versions();
        versionsLoadFailed = false;
        footerVersionsCount = manifestVersions.size();
        footerOffline = false;
        statusLabel.setText(
                Lang.tr("versions.loaded", manifestVersions.size()));
        updateFooterVersion();
        refreshModdedProfiles(null);
    }

    private void onLoadFailed(Throwable cause) {
        versionsLoadFailed = true;
        statusLabel.setText(Lang.tr("versions.offline", cause.getMessage()));
        // Офлайн-запасной вариант: показываем уже созданные инстансы и кэшированные
        // установленные версии (например, Forge), чтобы их можно было запускать
        // без интернета. Создание новых инстансов ограничится
        // локально кэшированными версиями.
        try {
            manifestVersions = loadCachedManifestVersions();
        } catch (Exception ignored) {
            manifestVersions = List.of();
        }
        footerVersionsCount = manifestVersions.size();
        footerOffline = true;
        updateFooterVersion();
        refreshModdedProfiles(null);
    }

    /** Перестраивает строку подвала (также используется при смене языка). */
    private void updateFooterVersion() {
        if (footerVersionLabel == null) return;
        if (footerOffline) {
            footerVersionLabel.setText(footerVersionsCount > 0
                    ? Lang.tr("footer.offline.cached", APP_VERSION,
                            footerVersionsCount)
                    : Lang.tr("footer.offline", APP_VERSION));
        } else {
            footerVersionLabel.setText(footerVersionsCount >= 0
                    ? Lang.tr("footer.loaded", APP_VERSION,
                            footerVersionsCount)
                    : Lang.tr("footer.version", APP_VERSION));
        }
    }

    // Состояние подвала для перерисовок (включая смену языка)
    private int footerVersionsCount = -1;
    private boolean footerOffline = false;

    private List<MinecraftVersion> loadCachedManifestVersions() {
        List<MinecraftVersion> cached = new ArrayList<>();
        Path versionsDir = GameDirectory.defaultDirectory().versionsDir();
        if (!Files.isDirectory(versionsDir)) return cached;
        try (var stream = Files.list(versionsDir)) {
            for (Path dir : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(dir)) continue;
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) continue;
                try {
                    String text = Files.readString(json);
                    JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                    // Пропускать битые или неванильные? Включаем и модовые для офлайн-видимости
                    String typeStr = obj.has("type") && obj.get("type").isJsonPrimitive()
                            ? obj.get("type").getAsString() : "release";
                    VersionType type;
                    try {
                        type = new VersionTypeRegistry().resolve(typeStr);
                    } catch (Exception e) {
                        type = StandardVersionType.RELEASE;
                    }
                    cached.add(new MinecraftVersion(id, type, null, null));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return cached;
    }

    /** Требуют ли метаданные версии Java 8 или старше. */
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
        } catch (IOException e) {
            // Некритично
        }
    }

    private final AtomicLong avatarRequestId = new AtomicLong();

    private void updateAvatar(GameProfile profile) {
        long requestId = avatarRequestId.incrementAndGet();
        if (profile == null || profile.skinUrl().isEmpty()) {
            avatarView.setVisible(false);
            return;
        }
        skinService.loadAvatarAsync(profile, 32)
                .thenAccept(optImg -> Platform.runLater(() -> {
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
    //  Управление аккаунтами
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
        } catch (IOException e) {
            suppressSelectionListener = false;
        }
        if (selectedProfile != null) {
            updateAvatar(selectedProfile);
        }
    }

    /** Единая точка входа: одна кнопка открывает диалог, где пользователь выбирает Offline или Ely.by. */
    private void onAddAccount() {
        Stage launcherStage = (Stage) root.getScene().getWindow();
        AddAccountDialog.Result res = AddAccountDialog.showDialog(launcherStage);
        if (res == null) return;
        if (res.type() == AddAccountDialog.Type.OFFLINE) {
            createOfflineAccount(res.offlineName());
        } else {
            authenticateElyBy(res.elyUser(), res.elyPass());
        }
    }

    private void createOfflineAccount(String name) {
        if (name == null || name.isBlank()) return;
        Stage launcherStage = (Stage) root.getScene().getWindow();
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
            statusLabel.setText(Lang.tr("account.created", name));
        } catch (IOException e) {
            ErrorDialog.show(launcherStage, Lang.tr("account.error.title"),
                    Lang.tr("account.create.failed", e.getMessage()));
        }
    }

    private void authenticateElyBy(String user, String pass) {
        Stage launcherStage = (Stage) root.getScene().getWindow();
        statusLabel.setText(Lang.tr("ely.signing"));
        if (addAccountButton != null) addAccountButton.setDisable(true);

        Task<GameProfile> authTask = new Task<>() {
            @Override
            protected GameProfile call() throws Exception {
                return elyAuthService.authenticate(user, pass);
            }
        };
        authTask.setOnSucceeded(e -> {
            GameProfile profile = authTask.getValue();
            try {
                var profiles = new ArrayList<>(profileService.loadProfiles());
                profiles.removeIf(p -> (profile.uuid().isPresent() && p.uuid().isPresent()
                        && p.uuid().get().equals(profile.uuid().get()))
                        || p.name().equals(profile.name()));
                profiles.add(profile);
                profileService.saveProfiles(profiles);
            } catch (IOException ex) {
                // Некритично
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
                    ? Lang.tr("ely.skin", profile.skinModel().orElse("classic"))
                    : Lang.tr("ely.noskin");
            statusLabel.setText(Lang.tr("ely.ok", profile.name(), skinInfo));
            if (addAccountButton != null) addAccountButton.setDisable(false);
        });
        authTask.setOnFailed(e -> {
            String msg = authTask.getException().getMessage();
            statusLabel.setText(Lang.tr("ely.failed", msg));
            if (addAccountButton != null) addAccountButton.setDisable(false);
            ErrorDialog.show(launcherStage, Lang.tr("ely.failed.title"), msg);
        });
        var thread = new Thread(authTask, "ely-auth");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Панель деталей
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Создание инстанса
    // ------------------------------------------------------------------

    /**
     * Открывает диалог «Новый инстанс» — один простой экран с именем
     * инстанса, чипами загрузчиков, списком версий и списком версий
     * загрузчика — и создаёт инстанс при подтверждении.
     * Выбор версии живёт здесь, на главной вкладке.
     */
    private void onNewInstance() {
        if (manifestVersions.isEmpty()) {
            if (versionsLoadFailed) {
                statusLabel.setText(Lang.tr("versions.retry"));
                loadVersions();
            } else {
                statusLabel.setText(Lang.tr("versions.wait"));
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
     * Создаёт инстанс в одной фоновой задаче: устанавливает
     * версию игры/загрузчика, только если она ещё не установлена
     * (пропуск-если-цело: целые файлы не перекачиваются; для уже
     * имеющихся версий сеть вообще не используется) и регистрирует
     * инстанс с собственным игровым каталогом.
     */
    private void createInstance(NewInstanceDialog.Result result) {
        createInstance(result, false);
    }

    /**
     * @param playWhenReady запустить инстанс сразу после создания
     */
    private void createInstance(NewInstanceDialog.Result result,
                                boolean playWhenReady) {
        BuildRequest request = new BuildRequest(
                result.type(),
                result.mcVersion(),
                result.loader(),
                result.extraJvmArgs(),
                result.name());
        String versionId = request.versionId();

        try {
            if (!buildCreator.isBuildAvailable(versionId, request.effectiveName())) {
                statusLabel.setText(Lang.tr("instance.create.failed",
                        "Build already exists: " + request.effectiveName() + " " + versionId));
                ErrorDialog.show((Stage) root.getScene().getWindow(),
                        Lang.tr("instance.create.failed.title"),
                        "Build already exists: " + request.effectiveName()
                                + " (" + versionId + ")");
                return;
            }
        } catch (IOException e) {
            LOG.warning("Build availability check failed, continuing: " + e.getMessage());
        }

        // Несколько инстансов могут делить одну версию (у каждого свои
        // имя, игровой каталог, моды и сборки) — каждое создание
        // делает новый; имя каталога остаётся уникальным
        // автоматически
        statusLabel.setText(Lang.tr("instance.creating",
                request.type() == ModLoaderType.VANILLA ? "vanilla " + request.mcVersion().id()
                        : request.type().displayName() + " " + request.loader().loaderVersion()
                                + " for MC " + request.mcVersion().id()));

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("install.create.title"));
        progressDialog.show();

        Task<ModdedProfile> task = new Task<>() {
            @Override
            protected ModdedProfile call() throws Exception {
                // Весь пайплайн — в BuildCreator:
                // isVersionDownloaded -> downloadVersion + повторная проверка
                // -> createBuildDirectory + populateBuildDirectories
                return buildCreator.createBuild(request, progressDialog);
            }
        };
        task.setOnSucceeded(e -> {
            ModdedProfile profile = task.getValue();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 0, 0,
                    List.of()));
            jumpToInstance(profile);
            statusLabel.setText(Lang.tr("instance.ready", profile.name(),
                    profile.isVanilla() ? ""
                            : Lang.tr("instance.ready.mods")));
            if (playWhenReady) {
                startInstanceLaunch(profile);
            }
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 1, 0, List.of()));
            statusLabel.setText(
                    Lang.tr("instance.create.failed", cause.getMessage()));
            ErrorDialog.show(owner, Lang.tr("instance.create.failed.title"),
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        });
        var thread = new Thread(task, "instance-create");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Инстансы: список, запуск, папка, удаление
    // ------------------------------------------------------------------

    /**
     * Перезагружает список инстансов в боковую панель.
     *
     * @param selectId id инстанса для выбора после загрузки, либо null
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
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("instances.load.failed", e.getMessage()));
        }
    }

    /**
     * Открывает игровой каталог выбранного профиля в системном файловом
     * менеджере, чтобы пользователь мог добавить моды, ресурс-паки, шейдеры,
     * миры или вручную проверить логи.
     */
    private void onOpenProfileFolder(ModdedProfile profile) {
        if (profile == null) return;
        try {
            Path dir = moddedProfileService.resolveGameDir(profile);
            ModdedProfileService.ensureProfileFolders(dir);
            java.awt.Desktop.getDesktop().open(dir.toFile());
            statusLabel.setText(Lang.tr("folder.opened", dir));
        } catch (Exception ex) {
            statusLabel.setText(Lang.tr("folder.open.failed", ex.getMessage()));
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    Lang.tr("error.folder.title"),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Открывает диалог редактирования выбранного профиля (отображаемое имя,
     * лимит памяти и доп. JVM-аргументы) и сохраняет изменения.
     * Переименование также переименовывает игровой каталог (моды, сохранения
     * и сборки переезжают вместе); версии остаются фиксированными.
     */
    private void onEditProfile(ModdedProfile profile) {
        if (profile == null) return;

        EditProfileDialog.Result result = EditProfileDialog.show(
                (Stage) root.getScene().getWindow(), profile);
        if (result == null) return;

        try {
            // Переименование также переименовывает папку (моды/сохранения/сборки
            // переезжают вместе), поэтому далее выбираем по новому id
            Optional<ModdedProfile> updated = moddedProfileService.updateProfile(
                    profile.id(), result.extraJvmArgs(), result.name(),
                    result.memoryMb());
            if (updated.isPresent()) {
                refreshModdedProfiles(updated.get().id());
                statusLabel.setText(Lang.tr("instance.updated",
                        updated.get().name(),
                        ModdedProfileService.formatMemory(
                                updated.get().memoryMb()),
                        result.extraJvmArgs().isEmpty() ? ""
                                : Lang.tr("instance.updated.jvm",
                                        result.extraJvmArgs().size())));
            }
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("instance.update.failed", e.getMessage()));
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    Lang.tr("error.update.title"),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void onDeleteProfile(ModdedProfile profile) {
        if (profile == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(Lang.tr("instance.delete.title"));
        alert.setHeaderText(
                Lang.tr("instance.delete.header", profile.name()));
        alert.setContentText(
                Lang.tr("instance.delete.text",
                        GameDirectory.defaultDirectory().root()
                                .resolve(profile.gameDirPath())));
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    var keptDir = moddedProfileService.deleteProfile(profile.id());
                    refreshModdedProfiles(null);
                    statusLabel.setText(Lang.tr("instance.delete.done",
                            keptDir.map(d -> Lang.tr("instance.delete.kept", d))
                                    .orElse("")));
                } catch (IOException e) {
                    statusLabel.setText(Lang.tr("instance.delete.failed",
                            e.getMessage()));
                }
            }
        });
    }

    // --- Запуск инстанса (проверка → починка при нужде → запуск) ---

    /**
     * Общая точка запуска для всех кнопок «Играть» на карточках:
     * определение аккаунта, обновление Ely.by и далее цепочка
     * проверка → починка-при-необходимости → запуск.
     */
    private void startInstanceLaunch(ModdedProfile profile) {
        // Только одна игра за раз на окно лаунчера — повторная
        // попытка во время запуска игнорируется
        if (versionPlayInFlight) {
            statusLabel.setText(Lang.tr("launch.blocked",
                    launchingStatusText.toLowerCase()));
            return;
        }
        GameProfile account = getOrCreateProfile();
        GameDirectory storage = GameDirectory.defaultDirectory();

        // Попытка запуска сразу блокирует все кнопки «Играть»
        // (до обновления по терминальному колбэку)
        versionPlayInFlight = true;
        javaDownloadAttempted = false;
        setLaunchStatus(profile.id(), Lang.tr("launch.verifying"));
        statusLabel.setText(Lang.tr("launch.verify.status", profile.name()));

        if (account.isElyBy()) {
            statusLabel.setText(Lang.tr("ely.refreshing"));
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
     * Проверяет установку профиля; при проблемах пытается автоматически
     * починить (докачивая только отсутствующие или битые файлы)
     * и перепроверяет перед запуском. Непочиняемые проблемы
     * показываются с причинами.
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
            statusLabel.setText(Lang.tr("launch.verify.error",
                    verifyTask.getException().getMessage()));
            ErrorDialog.show((Stage) root.getScene().getWindow(),
                    Lang.tr("launch.verify.title"),
                    verifyTask.getException().getClass().getSimpleName() + ": "
                            + verifyTask.getException().getMessage());
        });
        var thread = new Thread(verifyTask, "instance-verify");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Направляет неуспешную проверку по нужному пути восстановления:
     * автоматическое скачивание Java 8 для legacy-версий, которым не хватает
     * только рантайма, однократная автопочинка (докачка отсутствующих или
     * битых файлов) либо диагностический отчёт с причинами.
     *
     * @param repairAttempted выполнялась ли уже починка для этой
     *                        попытки запуска (защита от циклов починки)
     */
    private void handleInstanceVerificationFailure(
            ModdedProfile profile,
            GameProfile account,
            GameDirectory storage,
            ModdedProfileVerificationService.VerificationReport report,
            boolean repairAttempted) {
        // Legacy-версии: всё хорошо, кроме Java-рантайма
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
        // Автопочинка: переустановка ваниллы/загрузчика — целые файлы
        // пропускаются, скачиваются только отсутствующие/битые — затем
        // повторная проверка
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
        statusLabel.setText(
                Lang.tr("launch.repair.status", profile.name()));
        setLaunchStatus(profile.id(), Lang.tr("launch.repairing"));

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("install.repair.title", profile.name()));
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
            statusLabel.setText(
                    Lang.tr("launch.repair.failed", cause.getMessage()));
            StringBuilder msg = new StringBuilder();
            msg.append(Lang.tr("launch.repair.failed.text",
                    cause.getClass().getSimpleName(), cause.getMessage()));
            for (String error : original.errors()) {
                msg.append(" - ").append(error).append('\n');
            }
            ErrorDialog.show(owner, Lang.tr("launch.repair.failed.title",
                    profile.name()), msg.toString());
        });
        var thread = new Thread(repairTask, "instance-repair");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Сообщает о проблемах проверки с причинами и вновь включает
     * управление инстансом.
     */
    private void showProfileVerificationErrors(
            ModdedProfile profile,
            ModdedProfileVerificationService.VerificationReport report) {
        refreshLaunchButtons();
        statusLabel.setText(
                Lang.tr("launch.notlaunchable", report.errors().get(0)));

        StringBuilder msg = new StringBuilder(
                Lang.tr("launch.cannot"));
        for (String error : report.errors()) {
            msg.append(" - ").append(error).append('\n');
        }
        if (!report.warnings().isEmpty()) {
            msg.append(Lang.tr("launch.warnings"));
            for (String warning : report.warnings()) {
                msg.append(" - ").append(warning).append('\n');
            }
        }
        ErrorDialog.show((Stage) root.getScene().getWindow(),
                Lang.tr("launch.verify.failed.title", profile.name()),
                msg.toString());
    }

    private void doLaunchProfile(ModdedProfile profile,
                                 GameProfile account,
                                 VersionMetadata metadata,
                                 GameDirectory storage) {
        statusLabel.setText(Lang.tr("launch.starting", profile.name()));
        setLaunchStatus(profile.id(), Lang.tr("launch.launching"));
        Path runtimeDir = storage.root().resolve(profile.gameDirPath());

        Task<LaunchResult> launchTask = new Task<>() {
            @Override
            protected LaunchResult call() throws Exception {
                // Игровой каталог профиля должен существовать до запуска
                // процесса внутри него
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
                } catch (IOException ignored) {
                    // Некритично
                }
                statusLabel.setText(Lang.tr("launch.running",
                        profile.versionId(), profile.name()));
                Stage launcherStage = (Stage) root.getScene().getWindow();
                launcherStage.hide();
                monitorProcess(result.process().orElseThrow(),
                        profile.versionId(), launcherStage);
            } else if (result.status() == LaunchResult.Status.FILE_CHECK_FAILED) {
                // Редкая гонка (файлы исчезли между проверкой и запуском)
                ModdedProfileVerificationService.VerificationReport report =
                        new ModdedProfileVerificationService.VerificationReport(
                                false, List.of(result.message()), List.of(),
                                Optional.of(metadata));
                repairProfileAndLaunch(profile, account, storage, report);
            } else {
                refreshLaunchButtons();
                String msg = Lang.tr("launch.launch.failed", result.message());
                statusLabel.setText(msg);
                Stage launcherStage = (Stage) root.getScene().getWindow();
                ErrorDialog.show(launcherStage,
                        Lang.tr("launch.launch.failed.title"),
                        result.status() + ": " + result.message());
            }
        });
        launchTask.setOnFailed(e -> {
            refreshLaunchButtons();
            String msg = Lang.tr("launch.launch.error",
                    launchTask.getException().getMessage());
            statusLabel.setText(msg);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage,
                    Lang.tr("launch.launch.error.title"),
                    launchTask.getException().getClass().getSimpleName() + ": "
                            + launchTask.getException().getMessage());
        });
        var thread = new Thread(launchTask, "profile-launch");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Автозагрузка Java 8 (legacy-инстансы)
    // ------------------------------------------------------------------

    /**
     * Скачивает и устанавливает управляемую JRE 8 для legacy-инстанса,
     * единственная проблема которого — отсутствующий рантайм, затем перепроверяет
     * и запускает.
     */
    private void downloadAndInstallJava8ForInstance(ModdedProfile profile,
                                                    GameProfile account,
                                                    GameDirectory storage,
                                                    VersionMetadata meta) {
        statusLabel.setText(
                Lang.tr("launch.java.needed", profile.name()));
        setLaunchStatus(profile.id(), Lang.tr("launch.installing"));

        Task<JavaRuntime> installTask = new Task<>() {
            @Override
            protected JavaRuntime call() throws Exception {
                Path targetDir = storage.javaRuntimeDir("jre-legacy");
                return javaRuntimeInstaller.install(8, targetDir);
            }
        };
        installTask.setOnSucceeded(e -> {
            JavaRuntime rt = installTask.getValue();
            statusLabel.setText(
                    Lang.tr("launch.java.installed", profile.name()));

            if (javaResolutionService instanceof DefaultJavaResolutionService svc) {
                svc.setCustomJavaPath(rt.javaExecutable());
            }
            verifyProfileAndLaunch(profile, account, storage);
        });
        installTask.setOnFailed(e -> {
            refreshLaunchButtons();
            String msg = Lang.tr("launch.java.failed",
                    installTask.getException().getMessage());
            statusLabel.setText(msg);
            Stage launcherStage = (Stage) root.getScene().getWindow();
            ErrorDialog.show(launcherStage, Lang.tr("launch.java.failed.title"),
                    installTask.getException().getClass().getSimpleName() + ": "
                            + installTask.getException().getMessage()
                            + Lang.tr("launch.java.manual"));
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
        } catch (IOException e) {
            // Проваливаемся к созданию умолчания
        }
        GameProfile defaultProfile = GameProfile.offline("Player");
        try {
            profileService.addOfflineProfile("Player");
        } catch (IOException e) {
            // Некритично — продолжаем с профилем в памяти
        }
        selectedProfile = defaultProfile;
        return defaultProfile;
    }

    private void monitorProcess(MinecraftProcess process, String versionId,
                                 Stage launcherStage) {
        Thread monitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                Platform.runLater(() -> {
                    launcherStage.show();
                    if (exitCode == 0) {
                        statusLabel.setText(
                                Lang.tr("launch.exited", versionId));
                    } else {
                        statusLabel.setText(Lang.tr("launch.crashed",
                                versionId, exitCode));
                        String err = process.stderr();
                        if (err.isBlank()) {
                            err = process.stdout();
                        }
                        if (!err.isBlank()) {
                            ErrorDialog.show(launcherStage,
                                    Lang.tr("launch.crash.title"),
                                    Lang.tr("launch.crash.code", exitCode)
                                            + err.lines().limit(30)
                                                    .reduce("", (a, b) -> a + b + "\n"));
                        }
                    }
                    refreshLaunchButtons();
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    launcherStage.show();
                    statusLabel.setText(Lang.tr("launch.monitor"));
                    refreshLaunchButtons();
                });
            }
        }, "mc-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    // ------------------------------------------------------------------
    //  Утилиты
    // ------------------------------------------------------------------
}
