package org.example.launcher.presentation;

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
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import org.example.launcher.infrastructure.server.BuildSummary;
import org.example.launcher.infrastructure.server.DistributionSources;
import org.example.launcher.infrastructure.server.ServerAuthService;
import org.example.launcher.infrastructure.server.ServerSession;
import org.example.launcher.infrastructure.server.api.LauncherServerApi;
import org.example.launcher.infrastructure.server.api.OfflineLauncherServerApi;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.application.build.ModpackImporter;
import org.example.launcher.application.build.ServerBuildInstaller;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.infrastructure.http.UrlFetcher;
import org.example.launcher.infrastructure.java.DefaultJavaFinder;
import org.example.launcher.infrastructure.elyby.ElyAuthService;
import org.example.launcher.infrastructure.java.JavaFinder;
import org.example.launcher.domain.model.LauncherPreferences;
import org.example.launcher.infrastructure.settings.FileSettingsRepository;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.ProfileService;
import org.example.launcher.infrastructure.skins.SkinService;
import org.example.launcher.infrastructure.mojang.VersionService;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.application.build.Build;
import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.build.BuildRequest;
import org.example.launcher.application.account.AccountManager;
import org.example.launcher.application.build.ModScanner;
import org.example.launcher.application.java.JavaEnvironmentManager;
import org.example.launcher.application.launch.BuildLaunchListener;
import org.example.launcher.application.launch.BuildLaunchManager;
import org.example.launcher.application.launch.CrashReport;
import org.example.launcher.application.launch.LaunchStage;
import org.example.launcher.core.LauncherContext;
import org.example.launcher.infrastructure.loaders.ModdedVersionService;
import org.example.launcher.infrastructure.updater.AppVersion;
import org.example.launcher.infrastructure.updater.LauncherUpdate;
import org.example.launcher.infrastructure.updater.UpdateService;
import org.example.launcher.i18n.Lang;
import org.example.launcher.domain.model.VersionType;

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
    private final JavaFinder javaFinder;
    private final JavaEnvironmentManager javaEnvironmentManager;
    private final ProfileService profileService;
    private final FileSettingsRepository preferences;
    private final ElyAuthService elyAuthService;
    private final SkinService skinService;
    private final ModLoaderRegistry modLoaderRegistry;
    private final FileSystemBuildRepository buildRepository;
    private final CreateBuildUseCase createBuildUseCase;
    private final AccountManager accountManager;
    private final BuildLaunchManager buildLaunchManager;
    private final ModpackImporter modpackImporter;

    private BorderPane root;
    private Label statusLabel;
    private boolean versionsLoadFailed = false;

    // Header-навигация + виды
    private enum View {
        INSTANCES, PROFILES, SERVER, IMPORT, CREATE, SETTINGS
    }

    private View currentView = View.INSTANCES;
    /** Табы Header: текстовые + иконочные (профили, настройки). */
    private final List<Button> headerTabs = new ArrayList<>();
    private StackPane viewStack;
    private TextField instanceSearchField;

    // Виды «Импорт» и «Создать»
    private VBox importView;
    private VBox createView;
    private Label importStatusLabel;
    private HBox importDropzone;

    // Вид инстансов (сетка карточек)
    private FlowPane instanceCards;
    private ScrollPane instancesScroll;
    private VBox instanceFilterChips;
    private Label instanceCountLabel;
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

    // Вкладка «Сборки от Админа»: текущий бэкенд + установщик серверных сборок
    private LauncherServerApi buildsBackend;
    private ServerBuildInstaller serverBuildInstaller;
    private VBox serverView;
    private VBox serverBuildsList;
    private Label serverModeLabel;
    private List<BuildSummary> serverBuilds = List.of();
    private boolean serverBuildsLoading = false;

    // Магазин сборок (по референсу): счётчик, поиск, фильтры-чипы, сортировка
    private Label serverCountLabel;
    private TextField serverSearchField;
    private String serverSearchText = "";
    private ComboBox<String> serverSortCombo;
    private HBox serverMcChips;
    private ToggleGroup serverMcChipGroup;
    private String serverMcFilter;
    private VBox serverLoaderBox;
    private final List<CheckBox> serverLoaderChecks = new ArrayList<>();
    private FlowPane serverCardsFlow;

    // Плашка Crash Analyzer: последний краш запуска (живёт до закрытия)
    private VBox serverCrashBar;
    private Label serverCrashTextLabel;
    private CrashReport serverLastCrash;
    private ModdedProfile serverLastCrashProfile;

    // Состояние запуска инстанса

    // Аккаунт
    private ComboBox<GameProfile> accountCombo;
    private Button addAccountButton;
    private ImageView avatarView;
    private GameProfile selectedProfile;
    private boolean suppressSelectionListener = false;
    private Timer elyRefreshTimer;

    /** Версии манифеста Mojang — питают диалог создания. */
    private List<MinecraftVersion> manifestVersions = List.of();

    public MainView(LauncherContext context) {
        this.versionService = context.versionService();
        this.javaFinder = context.javaFinder();
        this.javaEnvironmentManager = context.javaEnvironmentManager();
        this.profileService = context.profileService();
        this.preferences = context.preferences();
        this.elyAuthService = context.elyAuthService();
        this.skinService = context.skinService();
        this.modLoaderRegistry = context.modLoaderRegistry();
        this.buildRepository = context.buildRepository();
        this.createBuildUseCase = context.createBuildUseCase();
        this.accountManager = context.accountManager();
        this.buildLaunchManager = context.buildLaunchManager();
        this.modpackImporter = new ModpackImporter(createBuildUseCase,
                modLoaderRegistry, buildRepository, new UrlFetcher());
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
            LauncherPreferences saved = preferences.loadPreferences();
            mode = saved.buildsSourceMode() != null ? saved.buildsSourceMode() : "YANDEX";
            gitUrl = saved.buildsGitUrl();
            String savedLink = saved.yandexDiskLink();
            if (savedLink != null && !savedLink.isBlank()) {
                yandexLink = savedLink;
            }
            token = saved.buildsToken() != null ? saved.buildsToken() : "";
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
        buildsBackend = backend;
        serverBuildInstaller = new ServerBuildInstaller(backend,
                modLoaderRegistry, createBuildUseCase, buildRepository);
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
        serverView = buildServerView();
        importView = buildImportView();
        createView = buildCreateView();
        viewStack = new StackPane(instancesView, profilesView, serverView,
                importView, createView, settingsView);

        root.setTop(buildHeader());
        root.setCenter(viewStack);
        root.setBottom(buildFooter());
        showView(View.INSTANCES);
    }

    // --- Верхний Header: бренд, вкладки, профиль игрока ---

    /** Собирает Header: бренд, глобальные вкладки, профиль игрока. */
    private HBox buildHeader() {
        // Бренд-блок: фиолетовая иконка + название лаунчера
        Label brandIcon = new Label("\u25C6");
        brandIcon.getStyleClass().add("brand-icon");
        Label brandName = new Label("PACKCRAFT");
        brandName.getStyleClass().add("brand-name");
        Label brandSub = new Label("LAUNCHER");
        brandSub.getStyleClass().add("brand-sub");
        VBox brandText = new VBox(1, brandName, brandSub);
        HBox brandRow = new HBox(9, brandIcon, brandText);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");

        // Глобальные вкладки навигации
        HBox tabs = new HBox(8);
        tabs.getStyleClass().add("header-tabs");
        addHeaderTab(tabs, Lang.tr("nav.library"), View.INSTANCES);
        addHeaderTab(tabs, Lang.tr("nav.catalog"), View.SERVER);
        addHeaderTab(tabs, Lang.tr("nav.import"), View.IMPORT);
        addHeaderTab(tabs, Lang.tr("nav.create"), View.CREATE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Виджет аккаунта (быстрый переключатель; полное управление — в Профилях)
        avatarView = new ImageView();
        avatarView.setFitWidth(38);
        avatarView.setFitHeight(38);
        avatarView.setPreserveRatio(true);
        avatarView.setClip(new Circle(19, 19, 19));
        avatarView.setVisible(false);

        // Выпадающая стрелка аккаунт-капсулы (декоративная)
        Label accountArrow = new Label("\u25BE");
        accountArrow.getStyleClass().add("account-pill-arrow");

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

        HBox accountBox = new HBox(8, avatarView, accountCombo,
                addAccountButton, accountArrow);
        accountBox.setAlignment(Pos.CENTER);
        accountBox.getStyleClass().add("account-pill");

        // Системные кнопки Header: профили и настройки
        Button profilesButton = headerIconButton("\uD83D\uDC64",
                "nav.profiles", View.PROFILES);
        Button settingsButton = headerIconButton("\u2699",
                "nav.settings", View.SETTINGS);

        HBox header = new HBox(16, brandRow, tabs, spacer, accountBox,
                profilesButton, settingsButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.getStyleClass().add("topbar");
        return header;
    }

    /** Текстовый таб Header; активное состояние подсвечивается в {@code showView}. */
    private void addHeaderTab(HBox tabs, String text, View view) {
        Button tab = new Button(text);
        tab.getStyleClass().add("header-tab");
        tab.setUserData(view);
        tab.setOnAction(e -> showView(view));
        headerTabs.add(tab);
        tabs.getChildren().add(tab);
    }

    /** Иконочная кнопка Header (профили/настройки) с подсказкой. */
    private Button headerIconButton(String glyph, String tipKey, View view) {
        Button button = new Button(glyph);
        button.getStyleClass().add("header-icon-button");
        button.setUserData(view);
        button.setTooltip(new Tooltip(Lang.tr(tipKey)));
        button.setOnAction(e -> showView(view));
        headerTabs.add(button);
        return button;
    }

    // --- Вид «Импорт»: .mrpack / .zip в новый изолированный инстанс ---

    /** Hero-вид импорта: drop-zone, выбор файла, статус и подсказки. */
    private VBox buildImportView() {
        Label dropIcon = new Label("\uD83D\uDCE5");
        dropIcon.getStyleClass().add("import-drop-icon");

        Label dropTitle = new Label(Lang.tr("import.title"));
        dropTitle.getStyleClass().add("import-title");

        Label dropText = new Label(Lang.tr("import.drop.text"));
        dropText.getStyleClass().add("import-text");
        dropText.setWrapText(true);
        dropText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button choose = new Button(Lang.tr("import.drop.button"));
        choose.getStyleClass().add("pack-install");
        choose.setMaxWidth(Region.USE_PREF_SIZE);
        choose.setOnAction(e -> chooseImportFile());

        VBox dropContent = new VBox(10, dropIcon, dropTitle, dropText,
                choose);
        dropContent.setAlignment(Pos.CENTER);

        importStatusLabel = new Label();
        importStatusLabel.getStyleClass().add("alert-text");
        importStatusLabel.setWrapText(true);
        importStatusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Карточка перетаскивания: вся ширина рабочей зоны, центрировано
        importDropzone = new HBox(dropContent);
        importDropzone.setAlignment(Pos.CENTER);
        importDropzone.getStyleClass().add("import-dropzone");
        importDropzone.setMaxWidth(Double.MAX_VALUE);
        importDropzone.setMinHeight(230);

        // Drag & drop: файлы из проводника можно тянуть прямо в лаунчер
        importDropzone.setOnDragOver(this::onImportDragOver);
        importDropzone.setOnDragDropped(this::onImportDragDropped);
        importDropzone.setOnDragExited(e -> importDropzone
                .getStyleClass().remove("dropzone-active"));

        // --- Инфо-панель: как экспортировать и импортировать сборки ---
        Label guideTitle = new Label(Lang.tr("import.guide.title"));
        guideTitle.getStyleClass().add("import-guide-title");
        Label step1 = new Label(Lang.tr("import.guide.step1"));
        Label step2 = new Label(Lang.tr("import.guide.step2"));
        Label step3 = new Label(Lang.tr("import.guide.step3"));
        Label safety = new Label(Lang.tr("import.guide.safety"));
        step1.getStyleClass().add("import-guide-step");
        step2.getStyleClass().add("import-guide-step");
        step3.getStyleClass().add("import-guide-step");
        safety.getStyleClass().add("import-guide-note");
        safety.setWrapText(true);
        VBox guideCard = new VBox(8, guideTitle, step1, step2, step3,
                safety);
        guideCard.getStyleClass().add("import-guide-card");

        Label tipsTitle = new Label(Lang.tr("import.tips.title"));
        tipsTitle.getStyleClass().add("import-guide-title");
        Label formats = new Label(Lang.tr("import.tips.formats"));
        Label paths = new Label(Lang.tr("import.tips.paths"));
        Label custom = new Label(Lang.tr("import.tips.custom"));
        formats.getStyleClass().add("import-guide-step");
        paths.getStyleClass().add("import-guide-step");
        custom.getStyleClass().add("import-guide-step");
        VBox tipsCard = new VBox(8, tipsTitle, formats, paths, custom);
        tipsCard.getStyleClass().add("import-guide-card");

        // Две колонки 1:1 на всю ширину — страница не смотрится пустой
        HBox info = new HBox(16, guideCard, tipsCard);
        for (var child : info.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }
        info.setMaxWidth(Double.MAX_VALUE);

        VBox view = new VBox(20, importDropzone, importStatusLabel, info);
        view.setPadding(new Insets(32));
        return view;
    }

    private void onImportDragOver(DragEvent event) {
        Dragboard board = event.getDragboard();
        if (board.hasFiles()) {
            importDropzone.getStyleClass()
                    .add("dropzone-active");
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void onImportDragDropped(DragEvent event) {
        Dragboard board = event.getDragboard();
        importDropzone.getStyleClass().remove("dropzone-active");
        boolean handled = false;
        if (board.hasFiles()) {
            for (java.io.File file : board.getFiles()) {
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".mrpack") || name.endsWith(".zip")) {
                    startImport(file.toPath());
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                importStatusLabel.setText(Lang.tr("import.wrong.type"));
            }
        }
        event.setDropCompleted(handled);
        event.consume();
    }

    /** Диалог выбора архива модпака (.mrpack / .zip). */
    private void chooseImportFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.tr("import.chooser.title"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        Lang.tr("import.chooser.filter"),
                        "*.mrpack", "*.zip"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        Stage owner = (Stage) root.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(owner);
        if (file != null) {
            startImport(file.toPath());
        }
    }

    /**
     * Запускает фоновый импорт: версии Mojang должны быть загружены
     * (манифест нужен для подбора версии игры из .mrpack).
     */
    private void startImport(Path pack) {
        if (manifestVersions.isEmpty()) {
            importStatusLabel.setText(
                    versionsLoadFailed ? Lang.tr("versions.retry")
                            : Lang.tr("versions.wait"));
            if (versionsLoadFailed) {
                loadVersions();
            }
            return;
        }
        importStatusLabel.setText(Lang.tr("import.running"));
        LOG.info(() -> "Import started: " + pack);
        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("import.progress.title"));

        Task<ModpackImporter.Result> task = new Task<>() {
            @Override
            protected ModpackImporter.Result call() throws Exception {
                return modpackImporter.importPack(pack, manifestVersions,
                        progressDialog);
            }
        };
        task.setOnSucceeded(e -> {
            ModpackImporter.Result result = task.getValue();
            importStatusLabel.setText(Lang.tr("import.done",
                    result.packName(), result.downloaded(), result.failed()));
            statusLabel.setText(Lang.tr("import.done.status",
                    result.packName()));
            jumpToInstance(result.profile());
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            LOG.log(Level.WARNING, "Modpack import failed", cause);
            importStatusLabel.setText(Lang.tr("import.failed",
                    cause == null ? "" : cause.getMessage()));
            statusLabel.setText(Lang.tr("import.failed.short"));
        });
        FxTasks.run("import-pack", task);
    }

    // --- Вид «Создать»: hero-панель мастера + умные бандлы (быстрый старт) ---

    /** Hero-вид создания сборки: мастер + превью умных бандлов. */
    private VBox buildCreateView() {
        Label heroTitle = new Label(Lang.tr("create.hero.title"));
        heroTitle.getStyleClass().add("create-hero-title");
        heroTitle.setWrapText(true);

        Label heroText = new Label(Lang.tr("create.hero.text"));
        heroText.getStyleClass().add("create-hero-text");
        heroText.setWrapText(true);

        Button openWizard = new Button(Lang.tr("instances.create"));
        openWizard.getStyleClass().addAll("pack-install",
                "create-hero-cta");
        openWizard.setTooltip(new Tooltip(Lang.tr("instances.create.tip")));
        openWizard.setMaxWidth(Region.USE_PREF_SIZE);
        openWizard.setOnAction(e -> onNewInstance());

        VBox hero = new VBox(12, heroTitle, heroText, openWizard);
        hero.getStyleClass().add("create-hero");
        hero.setMaxWidth(Double.MAX_VALUE);

        Label bundlesTitle = new Label(Lang.tr("create.bundles.title"));
        bundlesTitle.getStyleClass().add("create-section-title");

        // Ровная сетка «3 колонки» на всю ширину: каждый бандл
        // получает свою треть свободного места (эквивалент 1fr)
        HBox bundles = new HBox(16,
                buildBundleCard("create.bundle.fps.name",
                        "create.bundle.fps.desc", "\u26A1",
                        ModLoaderType.FABRIC),
                buildBundleCard("create.bundle.shader.name",
                        "create.bundle.shader.desc", "\uD83C\uDF0C",
                        ModLoaderType.FABRIC),
                buildBundleCard("create.bundle.network.name",
                        "create.bundle.network.desc", "\uD83D\uDC9C",
                        ModLoaderType.FORGE));
        for (var child : bundles.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }
        bundles.setMaxWidth(Double.MAX_VALUE);

        VBox view = new VBox(24, hero, bundlesTitle, bundles);
        VBox.setMargin(bundlesTitle, new Insets(8, 0, 0, 0));
        view.setPadding(new Insets(32));
        return view;
    }

    /**
     * Карточка умного бандла: рабочий ярлык мастера создания сборки —
     * клик открывает окно создания с предвыбранным загрузчиком, бейдж
     * показывает целевое семейство (Fabric / Forge).
     */
    private VBox buildBundleCard(String nameKey, String descKey,
                                 String icon, ModLoaderType loader) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("bundle-icon");

        Label name = new Label(Lang.tr(nameKey));
        name.getStyleClass().add("bundle-name");
        Label loaderBadge = new Label(loader.displayName());
        loaderBadge.getStyleClass().add("bundle-badge");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        // Шапка карточки: иконка + название + бейдж загрузчика справа
        HBox header = new HBox(10, iconLabel, name, headerSpacer,
                loaderBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(Lang.tr(descKey));
        description.getStyleClass().add("pack-desc");
        description.setWrapText(true);

        VBox card = new VBox(10, header, description);
        card.getStyleClass().add("bundle-card");
        card.setPrefWidth(300);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> onNewInstance(loader));
        return card;
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

    // --- Вид инстансов: двухколоночный (фильтры слева + сетка справа) ---

    private VBox buildInstancesView() {
        // Поиск инстансов: лупа слева поверх поля (StackPane-оверлей)
        instanceSearchField = new TextField();
        instanceSearchField.setPromptText(Lang.tr("search.instances"));
        instanceSearchField.getStyleClass().add("search-field");
        instanceSearchField.setPadding(new Insets(8, 12, 8, 34));
        instanceSearchField.setMaxWidth(Double.MAX_VALUE);
        instanceSearchField.textProperty().addListener((obs, old, val) -> {
            instanceSearchText = val == null ? "" : val.trim().toLowerCase();
            refreshInstanceCards();
        });

        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.getStyleClass().add("search-icon");
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 11));
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane searchWrap = new StackPane(instanceSearchField, searchIcon);
        searchWrap.getStyleClass().add("search-wrap");
        searchWrap.setPrefWidth(320);
        searchWrap.setMinWidth(220);

        instanceFilterChips = new VBox(6);
        instanceFilterChips.getStyleClass().add("filter-row");
        instanceFilterChips.setAlignment(Pos.TOP_LEFT);
        instanceFilterGroup = new ToggleGroup();
        addInstanceFilterChip(Lang.tr("filter.all"), null);
        for (ModLoaderType loader : ModLoaderType.values()) {
            addInstanceFilterChip(loader.displayName(), loader);
        }

        Label filtersTitle = new Label(Lang.tr("filter.title"));
        filtersTitle.getStyleClass().add("filter-title");

        VBox filters = new VBox(10, filtersTitle, instanceFilterChips);
        filters.getStyleClass().add("store-filters");
        filters.setPrefWidth(220);
        filters.setPadding(new Insets(14));

        Label libraryTitle = new Label(Lang.tr("nav.library"));
        libraryTitle.getStyleClass().add("title-label");
        instanceCountLabel = new Label();
        instanceCountLabel.getStyleClass().add("store-count");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        Button createBuildButton = new Button(Lang.tr("instances.create"));
        createBuildButton.getStyleClass().add("pack-install");
        createBuildButton.setTooltip(new Tooltip(Lang.tr("instances.create.tip")));
        createBuildButton.setOnAction(e -> onNewInstance());

        HBox topRow = new HBox(10, libraryTitle, instanceCountLabel, topSpacer,
                searchWrap, createBuildButton);;
        topRow.setAlignment(Pos.CENTER_LEFT);

        instanceCards = new FlowPane();
        instanceCards.setHgap(16);
        instanceCards.setVgap(16);
        instanceCards.setPadding(new Insets(2, 0, 24, 0));
        instanceCards.getStyleClass().add("cards-flow");

        instancesScroll = new ScrollPane(instanceCards);
        instancesScroll.setFitToWidth(true);
        instancesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        instancesScroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(instancesScroll, Priority.ALWAYS);

        VBox content = new VBox(16, topRow, instancesScroll);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox layout = new HBox(18, filters, content);
        HBox.setHgrow(content, Priority.ALWAYS);

        VBox view = new VBox(0, layout);
        view.setPadding(new Insets(18, 24, 18, 24));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    /** Чип фильтра в левом сайдбаре: иконка загрузчика + счётчик инстансов. */
    private ToggleButton makeInstanceFilterChip(String text,
                                                  ModLoaderType loader) {
        long count = loader == null ? moddedProfiles.size()
                : moddedProfiles.stream().filter(
                        p -> p.loaderType() == loader).count();
        String label = text + "  ·  " + count;
        ToggleButton chip = new ToggleButton(label);
        if (loader != null) {
            ImageView icon = loaderImage(loader);
            if (icon != null) {
                icon.setFitWidth(16);
                icon.setFitHeight(16);
                chip.setGraphic(icon);
            }
        }
        chip.getStyleClass().add("filter-button");
        chip.setToggleGroup(instanceFilterGroup);
        chip.setUserData(loader);
        chip.setSelected(loader == null);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setAlignment(Pos.CENTER_LEFT);
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

    /**
     * Перестраивает чипы фильтров с иконками загрузчиков и счётчиками
     * инстансов (иконка — буквы буквы, счётчик — число инстансов).
     */
    private void rebuildInstanceFilterChips() {
        if (instanceFilterChips == null) return;
        ModLoaderType keep = instanceLoaderFilter;
        instanceFilterGroup = new ToggleGroup();
        List<ToggleButton> chips = new ArrayList<>();
        chips.add(makeInstanceFilterChip(Lang.tr("filter.all"), null));
        for (ModLoaderType loader : ModLoaderType.values()) {
            chips.add(makeInstanceFilterChip(loader.displayName(), loader));
        }
        instanceFilterChips.getChildren().setAll(chips);
        instanceLoaderFilter = keep;
        syncInstanceFilterChips();
    }

    /**
     * Переключает центральный вид: header-табы подсвечиваются,
     * статичные виды (Импорт/Создать) просто показываются.
     */
    private void showView(View view) {
        currentView = view;
        instancesView.setVisible(view == View.INSTANCES);
        instancesView.setManaged(view == View.INSTANCES);
        profilesView.setVisible(view == View.PROFILES);
        profilesView.setManaged(view == View.PROFILES);
        serverView.setVisible(view == View.SERVER);
        serverView.setManaged(view == View.SERVER);
        importView.setVisible(view == View.IMPORT);
        importView.setManaged(view == View.IMPORT);
        createView.setVisible(view == View.CREATE);
        createView.setManaged(view == View.CREATE);
        settingsView.setVisible(view == View.SETTINGS);
        settingsView.setManaged(view == View.SETTINGS);
        for (Button tab : headerTabs) {
            boolean active = tab.getUserData() == view;
            if (active) {
                if (!tab.getStyleClass().contains("header-tab-active")) {
                    tab.getStyleClass().add("header-tab-active");
                }
            } else {
                tab.getStyleClass().remove("header-tab-active");
            }
        }
        if (view == View.INSTANCES) {
            refreshInstanceCards();
        } else if (view == View.PROFILES) {
            refreshProfilesView();
        } else if (view == View.SERVER) {
            refreshServerBuildsView();
        } else if (view == View.SETTINGS) {
            refreshSettingsView();
        }
        // IMPORT/CREATE — статичные виды без фоновых обновлений
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
        if (instanceCountLabel != null) {
            instanceCountLabel.setText(moddedProfiles.isEmpty() ? ""
                    : Lang.tr("instances.count", moddedProfiles.size()));
        }
        rebuildInstanceFilterChips();
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
        VBox card = new VBox(10);
        card.setPrefWidth(248);
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

        // Обложка: кастомная картинка 16:9 или тёмный градиент
        // с логотипом загрузчика; в углу — папка и настройки (hover)
        card.getChildren().add(buildInstanceBanner(profile));

        // Название: одна строка, троеточие при переполнении,
        // полный текст — в Tooltip при наведении
        Label name = new Label(profile.name());
        name.getStyleClass().add("instance-card-name");
        name.setWrapText(false);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(profile.name()));

        // Бейдж версии и модов: [Fabric 1.21.4 • 12 модов]
        Label badge = new Label(cardSubLine(profile));
        badge.getStyleClass().add("instance-badge");

        // Дата последнего запуска — мелкий серый текст
        Label played = new Label(cardPlayedLine(profile));
        played.getStyleClass().add("instance-card-sub");

        VBox titles = new VBox(5, name, badge, played);
        card.getChildren().add(titles);

        Button play = new Button(
                launching ? launchingStatusText
                        : Lang.tr("button.play").toUpperCase());
        play.getStyleClass().add("card-play");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setDisable(versionPlayInFlight || launching);
        play.setOnAction(e -> {
            selectInstance(profile.id());
            startInstanceLaunch(profile);
        });
        if (launching) {
            startLaunchDots(play);
        }
        card.getChildren().add(play);

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

    /**
     * Обложка карточки (16:9): кастомная картинка инстанса на весь баннер,
     * иначе тёмный градиент с логотипом загрузчика по центру. В правом
     * верхнем углу — два иконочных действия, видимые при наведении:
     * открыть папку игры и меню настроек профиля.
     */
    private javafx.scene.Node buildInstanceBanner(ModdedProfile profile) {
        StackPane banner = new StackPane();
        banner.setPrefHeight(126);
        banner.setMaxHeight(126);
        banner.getStyleClass().addAll("instance-banner",
                accentClass(profile.loaderType()));

        // Логотип загрузчика по центру: официальный PNG (Prism Launcher),
        // при недоступности ресурса — текстовый фолбэк (V / Fb / Fo / N / Q)
        javafx.scene.Node logo;
        ImageView loaderIcon = loaderImage(profile.loaderType());
        if (loaderIcon != null) {
            logo = loaderIcon;
            StackPane.setAlignment(loaderIcon, Pos.CENTER);
        } else {
            Label fallback = new Label(iconText(profile.loaderType()));
            fallback.getStyleClass().add("instance-banner-logo");
            StackPane.setAlignment(fallback, Pos.CENTER);
            logo = fallback;
        }

        // Логотип по центру оверлеем: показывает и под картинкой
        // (поверх затемнения), скрывается при отсутствии картинки
        Path custom = customInstanceIcon(profile);
        if (custom != null) {
            try {
                Image image = new Image(custom.toUri().toString());
                if (!image.isError()) {
                    banner.setBackground(new Background(new BackgroundImage(
                            image,
                            BackgroundRepeat.NO_REPEAT,
                            BackgroundRepeat.NO_REPEAT,
                            BackgroundPosition.CENTER,
                            new BackgroundSize(100, 100, true, true,
                                    false, true))));
                } else {
                    banner.getChildren().add(logo);
                }
            } catch (Exception ignored) {
                banner.getChildren().add(logo);
            }
        } else {
            banner.getChildren().add(logo);
        }

        // Быстрые действия в углу обложки: 📁 папка игры + ⚙ настройки
        HBox quick = new HBox(4);
        quick.getStyleClass().add("banner-actions");

        Button folder = new Button("\u2750");
        folder.getStyleClass().add("banner-action");
        folder.setTooltip(new Tooltip(Lang.tr("button.folder")));
        folder.setOnAction(e -> onOpenProfileFolder(profile));
        folder.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        Button settings = new Button("\u2699");
        settings.getStyleClass().add("banner-action");
        settings.setTooltip(new Tooltip(Lang.tr("instance.menu.tip")));
        settings.setOnAction(e -> onEditProfile(profile));
        settings.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);

        quick.getChildren().addAll(folder, settings);
        StackPane.setAlignment(quick, Pos.TOP_RIGHT);
        StackPane.setMargin(quick, new Insets(6));
        banner.getChildren().add(quick);

        return banner;
    }

    /** Путь к пользовательской иконке инстанса, если она есть. */
    private Path customInstanceIcon(ModdedProfile profile) {
        try {
            Path dir = buildRepository.resolveGameDir(profile);
            for (String name : List.of("icon.png", "icon.jpg", "pack.png")) {
                Path candidate = dir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            // недоступный каталог — считаем иконки нет
        }
        return null;
    }

    /**
     * Меню карточки → «Сменить иконку»: копирует выбранное изображение
     * в папку инстанса как icon.png и перерисовывает сетку.
     */
    private void changeInstanceIcon(ModdedProfile profile) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.tr("instance.icon.chooser"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Lang.tr("instance.icon.filter"),
                "*.png", "*.jpg", "*.jpeg"));
        Stage owner = (Stage) root.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        try {
            Path target = buildRepository.resolveGameDir(profile)
                    .resolve("icon.png");
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            statusLabel.setText(Lang.tr("instance.icon.done", profile.name()));
            refreshInstanceCards();
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("instance.icon.failed", e.getMessage()));
        }
    }

    /**
     * Официальная иконка загрузчика (PNG из ресурсов, источник —
     * Prism Launcher) для баннера карточки; null, если ресурса нет.
     */
    private static ImageView loaderImage(ModLoaderType type) {
        String name = switch (type) {
            case VANILLA -> "vanilla";
            case FABRIC -> "fabric";
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case QUILT -> "quilt";
        };
        var url = MainView.class.getResource(
                "/icons/loader/" + name + ".png");
        if (url == null) {
            return null;
        }
        ImageView view = new ImageView(new Image(url.toExternalForm()));
        view.setFitWidth(64);
        view.setFitHeight(64);
        view.setPreserveRatio(true);
        return view;
    }

    /**
     * Анимация статуса запуска на кнопке «Играть»: текст статуса
     * + бегущие точки (…). Останавливается, когда карточка уходит
     * из сцены (перестройка сетки или закрытие вида).
     */
    private void startLaunchDots(Button play) {
        String base = launchingStatusText;
        final String stem = base.replaceAll("\\.+$", "");
        final int[] tick = {0};
        Timeline dots = new Timeline(new KeyFrame(
                javafx.util.Duration.millis(400), e -> {
            tick[0] = (tick[0] + 1) % 4;
            play.setText(stem + ".".repeat(tick[0] == 0 ? 0 : tick[0]));
        }));
        dots.setCycleCount(javafx.animation.Animation.INDEFINITE);
        dots.playFromStart();
        play.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) {
                dots.stop();
            }
        });
    }

    private VBox buildAddInstanceCard() {
        Label plus = new Label("+");
        plus.getStyleClass().add("add-instance-plus");
        Label text = new Label(Lang.tr("instances.add"));
        text.getStyleClass().add("add-instance-text");
        VBox card = new VBox(6, plus, text);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(248);
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

    /** Считает jar-моды через {@link ModScanner}. */
    private int modsCount(ModdedProfile profile) {
        return ModScanner.countMods(
                buildRepository.resolveGameDir(profile), profile.isVanilla());
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
                    accountManager.delete(account.name());
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
                LauncherPreferences saved = preferences.loadPreferences();
                String mode = saved.buildsSourceMode() != null
                        ? saved.buildsSourceMode() : "YANDEX";
                DistributionSources.BuildsSource selected;
                try {
                    selected = DistributionSources.BuildsSource
                            .valueOf(mode);
                } catch (IllegalArgumentException e) {
                    selected = DistributionSources.BuildsSource.YANDEX;
                }
                buildsModeCombo.getSelectionModel().select(selected);
                String gitUrl = saved.buildsGitUrl();
                if (buildsGitField != null && gitUrl != null) {
                    buildsGitField.setText(gitUrl);
                }
                String yandexLink = saved.yandexDiskLink() != null
                        ? saved.yandexDiskLink()
                        : DistributionSources.YANDEX_BUILDS_LINK_DEFAULT;
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
        for (Button tab : headerTabs) {
            Object data = tab.getUserData();
            if (data == View.PROFILES || data == View.SETTINGS) {
                tab.setTooltip(new Tooltip(Lang.tr(data == View.PROFILES
                        ? "nav.profiles" : "nav.settings")));
            } else {
                tab.setText(switch ((View) data) {
                    case INSTANCES -> Lang.tr("nav.library");
                    case SERVER -> Lang.tr("nav.catalog");
                    case IMPORT -> Lang.tr("nav.import");
                    case CREATE -> Lang.tr("nav.create");
                    case PROFILES, SETTINGS -> tab.getText();
                });
            }
        }
        instanceSearchField.setPromptText(Lang.tr("search.instances"));
        accountCombo.setPromptText(Lang.tr("account.none"));
        addAccountButton.setText(Lang.tr("account.add"));
        addAccountButton.setTooltip(new Tooltip(Lang.tr("account.add.tip")));
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
        if (javaFinder instanceof DefaultJavaFinder svc) {
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
        FxTasks.run("update-check-manual", () -> {
            UpdateService.CheckResult result = checkForUpdates();
            Platform.runLater(() -> {
                updateStatusLabel.setText(result.detail());
                if (result.status() == UpdateService.Status.AVAILABLE
                        || result.status() == UpdateService.Status.STAGED) {
                    UpdateService service = updateService();
                    if (service != null) {
                        LauncherUpdateWindow.show(
                                (Stage) root.getScene().getWindow(), service,
                                result);
                    }
                }
                refreshSettingsView();
            });
        });
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
            LauncherPreferences saved = preferences.loadPreferences();
            preferences.savePreferences(new LauncherPreferences(
                    saved.lastSelectedVersion(),
                    saved.lastSelectedAccount(),
                    gitUrl, mode.name(), yandexLink, token,
                    saved.language()));
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

    // ------------------------------------------------------------------
    //  Сборки от Админа: список с сервера + установка одним кликом
    // ------------------------------------------------------------------

    /** Вид «Сборки от Админа»: фильтры слева, карточки, поиск и краш-плашка. */
    private VBox buildServerView() {
        serverModeLabel = new Label();
        serverModeLabel.getStyleClass().add("settings-value");

        // Заголовочная строка: источник, счётчик, поиск, кнопка обновления
        serverCountLabel = new Label();
        serverCountLabel.getStyleClass().add("store-count");

        serverSearchField = new TextField();
        serverSearchField.setPromptText(Lang.tr("server.builds.search.prompt"));
        serverSearchField.getStyleClass().add("search-field");
        serverSearchField.setPrefWidth(280);
        serverSearchField.textProperty().addListener((obs, old, val) -> {
            serverSearchText = val == null ? "" : val.trim().toLowerCase();
            renderServerBuilds();
        });

        Button refresh = new Button(Lang.tr("server.builds.refresh"));
        refresh.getStyleClass().add("quick-select-button");
        refresh.setTooltip(new Tooltip(Lang.tr("server.builds.refresh.tip")));
        refresh.setOnAction(e -> {
            serverMcFilter = null;
            refreshServerBuildsView();
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(12, serverModeLabel, serverCountLabel,
                headerSpacer, serverSearchField, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        // --- Левая колонка фильтров ---
        serverSortCombo = new ComboBox<>();
        serverSortCombo.getItems().addAll(
                Lang.tr("server.builds.sort.recommended"),
                Lang.tr("server.builds.sort.newest"),
                Lang.tr("server.builds.sort.name"));
        serverSortCombo.getSelectionModel().select(0);
        serverSortCombo.setMaxWidth(Double.MAX_VALUE);
        serverSortCombo.valueProperty().addListener(
                (obs, old, val) -> renderServerBuilds());

        Label mcTitle = filterTitle("server.builds.filters.mc");
        serverMcChips = new HBox(6);
        serverMcChips.getStyleClass().add("filter-row");
        serverMcChipGroup = new ToggleGroup();

        Label loaderTitle = filterTitle("server.builds.filters.loader");
        serverLoaderBox = new VBox(6);

        VBox filters = new VBox(10, filterTitle("server.builds.filters.sort"),
                serverSortCombo, mcTitle, serverMcChips, loaderTitle,
                serverLoaderBox);
        filters.getStyleClass().add("store-filters");
        filters.setPrefWidth(232);
        filters.setPadding(new Insets(4, 12, 12, 4));
        VBox.setVgrow(filters, Priority.NEVER);

        // --- Сетка карточек ---
        serverBuildsList = new VBox(0);
        serverCardsFlow = new FlowPane();
        serverCardsFlow.setHgap(14);
        serverCardsFlow.setVgap(14);
        serverCardsFlow.setPadding(new Insets(4));
        serverCardsFlow.getStyleClass().add("cards-flow");
        serverCardsFlow.getChildren().add(serverBuildsList);

        ScrollPane scroll = new ScrollPane(serverCardsFlow);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // --- Плашка Crash Analyzer ---
        serverCrashBar = buildServerCrashBar();
        serverCrashBar.setVisible(false);
        serverCrashBar.setManaged(false);

        HBox content = new HBox(14, filters, scroll);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        VBox view = new VBox(10, header, content, serverCrashBar);
        view.setPadding(new Insets(16));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    private Label filterTitle(String key) {
        Label title = new Label(Lang.tr(key));
        title.getStyleClass().add("filter-title");
        return title;
    }

    private void updateServerModeLabel() {
        if (serverModeLabel == null || buildsModeCombo == null) return;
        DistributionSources.BuildsSource mode = buildsModeCombo
                .getSelectionModel().getSelectedItem();
        String source = switch (mode == null ? DistributionSources.BuildsSource.LOCAL
                : mode) {
            case LOCAL -> Lang.tr("settings.builds.local");
            case GITHUB -> Lang.tr("settings.builds.github");
            case YANDEX -> Lang.tr("settings.builds.yandex");
        };
        serverModeLabel.setText(Lang.tr("server.builds.source", source));
    }

    /** Загружает список сборок администратора в фоне и рисует карточки. */
    private void refreshServerBuildsView() {
        updateServerModeLabel();
        if (serverBuildsLoading) {
            return;
        }
        if (buildsBackend == null
                || buildsBackend instanceof OfflineLauncherServerApi) {
            serverBuilds = List.of();
            renderServerBuilds();
            return;
        }
        serverBuildsLoading = true;
        LOG.info(() -> "Server builds refresh via "
                + buildsBackend.getClass().getSimpleName()
                + ", session present: " + (serverSession != null));
        showServerBuildsPlaceholder(
                serverModeLabel == null ? "" : Lang.tr("server.builds.loading"),
                null);
        Task<List<BuildSummary>> task = new Task<>() {
            @Override
            protected List<BuildSummary> call() throws Exception {
                return buildsBackend.listBuilds(serverSession);
            }
        };
        task.setOnSucceeded(e -> {
            serverBuildsLoading = false;
            serverBuilds = task.getValue() == null ? List.of() : task.getValue();
            LOG.info(() -> "Server builds loaded: " + serverBuilds.size());
            refreshServerLoaderChecks();
            renderServerBuilds();
        });
        task.setOnFailed(e -> {
            serverBuildsLoading = false;
            serverBuilds = List.of();
            refreshServerLoaderChecks();
            Throwable cause = task.getException();
            showServerBuildsPlaceholder(
                    Lang.tr("server.builds.load.failed",
                            cause == null ? "" : cause.getMessage()),
                    cause);
        });
        FxTasks.run("server-builds-list", task);
    }

    private void showServerBuildsPlaceholder(String text, Throwable cause) {
        if (cause != null) {
            LOG.log(Level.WARNING, "Server builds list failed", cause);
        }
        Label label = new Label(text);
        label.getStyleClass().add("server-builds-placeholder");
        label.setWrapText(true);
        serverCardsFlow.getChildren().setAll(label);
    }

    /**
     * Чекбоксы загрузчиков с количеством сборок (Forge/Fabric/NeoForge/Quilt).
     * Снимается, когда ни один не выбран — показываются все.
     */
    private void refreshServerLoaderChecks() {
        if (serverLoaderBox == null) return;
        for (CheckBox cb : serverLoaderChecks) {
            ModLoaderType type = (ModLoaderType) cb.getUserData();
            long count = serverBuilds.stream().filter(
                    b -> b.loaderType() == type).count();
            cb.setText(Lang.tr("server.builds.filters.with.count",
                    type.displayName(), count));
        }
        if (serverLoaderChecks.isEmpty()) {
            for (ModLoaderType type : ModLoaderType.values()) {
                if (type == ModLoaderType.VANILLA) continue;
                CheckBox cb = new CheckBox(type.displayName());
                cb.getStyleClass().add("store-loader-check");
                cb.setUserData(type);
                cb.selectedProperty().addListener(
                        (obs, was, sel) -> renderServerBuilds());
                serverLoaderChecks.add(cb);
                serverLoaderBox.getChildren().add(cb);
            }
            refreshServerLoaderChecks();
        }
    }

    /** Активные фильтры загрузчиков; пустое множество = все. */
    private Set<ModLoaderType> activeServerLoaderFilter() {
        Set<ModLoaderType> loaders = new java.util.HashSet<>();
        for (CheckBox cb : serverLoaderChecks) {
            if (cb.isSelected()) {
                loaders.add((ModLoaderType) cb.getUserData());
            }
        }
        return loaders;
    }

    /** Перезаполняет чипы версий Minecraft по тому, что отдают сборки. */
    private void rebuildServerMcChips() {
        if (serverMcChips == null) {
            return;
        }
        List<String> versions = serverBuilds.stream()
                .map(BuildSummary::minecraftVersion)
                .distinct()
                .sorted(new MinecraftVersionComparator())
                .toList();
        if (serverMcFilter != null && !versions.contains(serverMcFilter)) {
            serverMcFilter = null;
        }
        serverMcChipGroup = new ToggleGroup();
        List<ToggleButton> chips = new ArrayList<>();
        chips.add(makeServerMcChip(Lang.tr("server.builds.mc.all"), null));
        for (String version : versions) {
            chips.add(makeServerMcChip(version, version));
        }
        serverMcChips.getChildren().setAll(chips);
        if (serverMcFilter == null) {
            chips.get(0).setSelected(true);
        } else {
            for (ToggleButton chip : chips) {
                if (serverMcFilter.equals(chip.getUserData())) {
                    chip.setSelected(true);
                    break;
                }
            }
        }
    }

    /** Один чип версии; {@code version == null} означает «все». */
    private ToggleButton makeServerMcChip(String text, String version) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().addAll("filter-button", "filter-mini");
        chip.setToggleGroup(serverMcChipGroup);
        chip.setUserData(version);
        chip.setOnAction(e -> {
            if (!chip.isSelected()) {
                chip.setSelected(true);
                return;
            }
            serverMcFilter = version;
            renderServerBuilds();
        });
        return chip;
    }

    /** Сортировка версий по убыванию (наибольшая первой). */
    private static final class MinecraftVersionComparator
            implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            List<Integer> orderA = versionOrder(a);
            List<Integer> orderB = versionOrder(b);
            int size = Math.min(orderA.size(), orderB.size());
            for (int i = 0; i < size; i++) {
                int diff = orderB.get(i) - orderA.get(i);
                if (diff != 0) {
                    return diff;
                }
            }
            return Integer.compare(orderB.size(), orderA.size());
        }

        private List<Integer> versionOrder(String version) {
            List<Integer> parts = new ArrayList<>();
            for (String part : version.split("[+._-]")) {
                String digits = part.replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    parts.add(Integer.parseInt(digits));
                }
            }
            return parts;
        }
    }

    /**
     * Фильтрует и сортирует сборки по текущим настройкам и рисует карточки.
     * Фильтры: поиск по имени/описанию, чип версии Minecraft, чекбоксы
     * загрузчиков; сортировка: рекомендованные/новые/по имени.
     */
    private void renderServerBuilds() {
        if (serverCountLabel != null) {
            serverCountLabel.setText(!serverBuilds.isEmpty()
                    ? Lang.tr("server.builds.count", serverBuilds.size())
                    : "");
        }
        rebuildServerMcChips();
        List<BuildSummary> visible = filteredServerBuilds();
        if (visible.isEmpty()) {
            showServerBuildsPlaceholder(serverBuilds.isEmpty()
                    ? Lang.tr("server.builds.empty")
                    : Lang.tr("server.builds.no.matches"), null);
            return;
        }
        List<VBox> cards = new ArrayList<>();
        for (BuildSummary summary : visible) {
            cards.add(buildServerBuildCard(summary));
        }
        serverCardsFlow.getChildren().setAll(cards);
    }

    private List<BuildSummary> filteredServerBuilds() {
        Set<ModLoaderType> loaders = activeServerLoaderFilter();
        List<BuildSummary> result = new ArrayList<>();
        for (BuildSummary summary : serverBuilds) {
            if (serverMcFilter != null
                    && !summary.minecraftVersion().equals(serverMcFilter)) {
                continue;
            }
            if (!loaders.isEmpty() && !loaders.contains(summary.loaderType())) {
                continue;
            }
            if (!serverSearchText.isEmpty()) {
                String haystack = (summary.displayName() + " "
                        + (summary.description() == null ? "" : summary.description()))
                        .toLowerCase();
                if (!haystack.contains(serverSearchText)) {
                    continue;
                }
            }
            result.add(summary);
        }
        int sortMode = serverSortCombo == null || serverSortCombo.getValue() == null
                ? 0 : serverSortCombo.getSelectionModel().getSelectedIndex();
        if (sortMode == 1) {
            // MinecraftVersionComparator уже сортирует по убыванию
            result.sort(java.util.Comparator.comparing(BuildSummary::version,
                    new MinecraftVersionComparator()));
        } else if (sortMode == 2) {
            result.sort(java.util.Comparator.comparing(
                    summary -> summary.displayName().toLowerCase()));
        }
        return result;
    }

    /**
     * Карточка сборки в стиле магазина: цветной баннер, бейдж версии,
     * чипы загрузчика и Minecraft, описание и кнопка «Установить».
     */
    private VBox buildServerBuildCard(BuildSummary summary) {
        VBox card = new VBox(8);
        card.getStyleClass().add("pack-card");
        card.setPrefWidth(300);

        // Баннер: пер-загрузочный градиент + крупное имя + бейдж версии
        Label bannerTitle = new Label(summary.displayName());
        bannerTitle.getStyleClass().add("pack-banner-title");
        bannerTitle.setWrapText(true);
        Label versionBadge = new Label("v" + summary.version());
        versionBadge.getStyleClass().add("pack-version-badge");
        StackPane banner = new StackPane(bannerTitle, versionBadge);
        banner.getStyleClass().addAll("pack-banner", "accent-"
                + summary.loaderType().name().toLowerCase(
                        java.util.Locale.ROOT));
        StackPane.setAlignment(bannerTitle, Pos.CENTER);
        StackPane.setAlignment(versionBadge, Pos.TOP_RIGHT);
        banner.setPrefHeight(110);

        // Чипы: загрузчик + версия Minecraft
        HBox chips = new HBox(6);
        Label loaderChip = new Label(summary.loaderType().displayName());
        loaderChip.getStyleClass().add("loader-badge-"
                + summary.loaderType().name().toLowerCase(java.util.Locale.ROOT));
        Label mcChip = new Label(summary.minecraftVersion());
        mcChip.getStyleClass().add("type-badge-snapshot");
        chips.getChildren().addAll(loaderChip, mcChip);

        Label name = new Label(summary.displayName());
        name.getStyleClass().add("pack-name");

        VBox body = new VBox(6, chips, name);
        if (summary.description() != null && !summary.description().isBlank()) {
            Label description = new Label(summary.description());
            description.getStyleClass().add("pack-desc");
            description.setWrapText(true);
            body.getChildren().add(description);
        }
        if (summary.loaderType() != ModLoaderType.VANILLA) {
            Label loaderVersion = new Label(Lang.tr(
                    "server.builds.loader.line", summary.loaderVersion()));
            loaderVersion.getStyleClass().add("pack-desc");
            body.getChildren().add(loaderVersion);
        }

        Button install = new Button(Lang.tr("server.builds.install"));
        install.getStyleClass().add("pack-install");
        install.setMaxWidth(Double.MAX_VALUE);
        install.setOnAction(e -> installServerBuild(summary));

        card.getChildren().addAll(banner, body, install);
        return card;
    }

    /**
     * Плашка Crash Analyzer: читаемая причина последнего краша,
     * кнопка «Подробнее» и крестик закрытия.
     */
    private VBox buildServerCrashBar() {
        Label icon = new Label("\u26A0");
        icon.getStyleClass().add("alert-icon");

        Label title = new Label(Lang.tr("server.builds.crash.title"));
        title.getStyleClass().add("alert-title");
        serverCrashTextLabel = new Label();
        serverCrashTextLabel.getStyleClass().add("alert-text");
        serverCrashTextLabel.setWrapText(true);
        VBox text = new VBox(2, title, serverCrashTextLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button details = new Button(Lang.tr("server.builds.crash.details"));
        details.getStyleClass().add("alert-details-button");
        details.setOnAction(e -> {
            if (serverLastCrash != null) {
                CrashWindow.show((Stage) root.getScene().getWindow(),
                        serverLastCrash);
            }
        });

        Button close = new Button("\u2715");
        close.getStyleClass().add("alert-close");
        close.setOnAction(e -> hideServerCrashBar());

        HBox bar = new HBox(12, icon, text, details, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("alert-bar");

        VBox wrap = new VBox(bar);
        return wrap;
    }

    /** Показывает плашку после краша; вызывается из слушателей запуска. */
    private void showServerCrashBar(ModdedProfile profile, CrashReport report) {
        if (serverCrashTextLabel == null) {
            return;
        }
        serverLastCrash = report;
        serverLastCrashProfile = profile;
        String suggestion = report.suggestion().orElse("");
        serverCrashTextLabel.setText(report.reason()
                + (suggestion.isBlank() ? "" : "\n" + suggestion));
        serverCrashBar.setVisible(true);
        serverCrashBar.setManaged(true);
    }

    private void hideServerCrashBar() {
        serverLastCrash = null;
        serverLastCrashProfile = null;
        if (serverCrashBar != null) {
            serverCrashBar.setVisible(false);
            serverCrashBar.setManaged(false);
        }
    }

    /**
     * Установка серверной сборки одним кликом: панель прогресса переиспользуется,
     * пайплайн целиком — {@link ServerBuildInstaller#provision}. После успеха
     * открывается созданный инстанс на вкладке «Мои инстансы».
     */
    private void installServerBuild(BuildSummary summary) {
        if (serverBuildInstaller == null) {
            statusLabel.setText(Lang.tr("server.builds.no.backend"));
            return;
        }
        // Версия Minecraft берётся из уже загруженного манифеста Mojang
        MinecraftVersion mcVersion = null;
        for (MinecraftVersion candidate : manifestVersions) {
            if (candidate.id().equals(summary.minecraftVersion())) {
                mcVersion = candidate;
                break;
            }
        }
        if (mcVersion == null) {
            statusLabel.setText(manifestVersions.isEmpty()
                    ? Lang.tr("versions.wait")
                    : Lang.tr("server.builds.no.mc",
                            summary.minecraftVersion()));
            return;
        }

        statusLabel.setText(Lang.tr("server.builds.installing",
                summary.summaryLine()));
        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("server.builds.install.title"));
        progressDialog.show();

        MinecraftVersion finalMcVersion = mcVersion;
        Task<ServerBuildInstaller.Outcome> task = new Task<>() {
            @Override
            protected ServerBuildInstaller.Outcome call() throws Exception {
                return serverBuildInstaller.provision(serverSession, summary,
                        summary.displayName(), finalMcVersion,
                        progressDialog);
            }
        };
        task.setOnSucceeded(e -> {
            ServerBuildInstaller.Outcome outcome = task.getValue();
            progressDialog.onComplete(new InstallationResult(
                    0, 0, 0, 0, 0, List.of()));
            if (outcome.failed() > 0) {
                statusLabel.setText(Lang.tr("server.builds.partial",
                        outcome.failed()));
            } else {
                statusLabel.setText(Lang.tr("server.builds.ready",
                        outcome.profile().name()));
            }
            jumpToInstance(outcome.profile());
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            progressDialog.onComplete(new InstallationResult(
                    0, 0, 0, 1, 0, List.of()));
            UiErrors.fail(statusLabel, owner,
                    Lang.tr("server.builds.install.failed",
                            cause == null ? "" : cause.getMessage()),
                    Lang.tr("server.builds.install.failed.title"), cause);
            if (currentView == View.SERVER) {
                refreshServerBuildsView();
            }
        });
        FxTasks.run("server-build-install", task);
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

        Task<List<MinecraftVersion>> task = new Task<>() {
            @Override
            protected List<MinecraftVersion> call() throws Exception {
                return versionService.fetchVersions();
            }
        };
        task.setOnSucceeded(e -> onVersionsLoaded(task.getValue()));
        task.setOnFailed(e -> onLoadFailed(task.getException()));
        FxTasks.run("version-fetch", task);

        checkForUpdatesOnStartup();
    }

    /**
     * При каждом запуске (с интернетом): спрашивает главную ветку о новой
     * версии лаунчера. Молчит, если актуально или офлайн; диалог
     * открывается, когда обновление доступно или подготовлено.
     */
    private void checkForUpdatesOnStartup() {
        FxTasks.run("update-check", () -> {
            try {
                UpdateService.CheckResult result = checkForUpdates();
                Platform.runLater(() -> {
                    switch (result.status()) {
                        case AVAILABLE, STAGED -> {
                            statusLabel.setText(result.detail());
                            UpdateService service = updateService();
                            if (service != null) {
                                LauncherUpdateWindow.show((Stage) root.getScene()
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
        });
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

    private void onVersionsLoaded(List<MinecraftVersion> versions) {
        manifestVersions = versions;
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
                    VersionType type = VersionType.fromMojangId(typeStr);
                    cached.add(MinecraftVersion.of(id, type, null, null));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return cached;
    }

    private void saveLastSelectedAccount(String accountName) {
        if (preferences == null) return;
        try {
            LauncherPreferences saved = preferences.loadPreferences();
            preferences.savePreferences(new LauncherPreferences(
                    saved.lastSelectedVersion(), accountName,
                    saved.buildsGitUrl(), saved.buildsSourceMode(),
                    saved.yandexDiskLink(), saved.buildsToken(),
                    saved.language()));
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
        skinService.loadAvatarAsync(profile, 64)
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
            var profiles = accountManager.load();
            suppressSelectionListener = true;
            try {
                accountCombo.getItems().setAll(profiles);

                String selectedUuid = selectedProfile != null
                        ? selectedProfile.uuid().orElse(null) : null;
                String savedName = null;
                if (preferences != null && selectedUuid == null) {
                    savedName = preferences.loadPreferences()
                            .lastSelectedAccount();
                }

                var toSelect = accountManager
                        .resolveSelection(profiles, selectedUuid, savedName)
                        .orElse(null);

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
            accountManager.createOffline(name);
            refreshAccounts();
            refreshProfilesView();
            for (GameProfile p : accountCombo.getItems()) {
                if (p.name().equals(name.trim())) {
                    accountCombo.getSelectionModel().select(p);
                    selectedProfile = p;
                    saveLastSelectedAccount(p.name());
                    break;
                }
            }
            statusLabel.setText(Lang.tr("account.created", name.trim()));
        } catch (IllegalArgumentException e) {
            ErrorDialog.show(launcherStage, Lang.tr("account.error.title"),
                    e.getMessage());
        } catch (IOException e) {
            ErrorDialog.show(launcherStage, Lang.tr("account.error.title"),
                    Lang.tr("account.create.failed", e.getMessage()));
        }
    }

    private void authenticateElyBy(String user, String pass) {
        Stage launcherStage = (Stage) root.getScene().getWindow();
        statusLabel.setText(Lang.tr("ely.signing"));
        LOG.info(() -> "Ely.by authentication requested for " + user);
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
                accountManager.saveAuthenticated(profile);
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
            if (addAccountButton != null) addAccountButton.setDisable(false);
            UiErrors.fail(statusLabel, launcherStage,
                    Lang.tr("ely.failed", msg),
                    Lang.tr("ely.failed.title"), msg);
        });
        FxTasks.run("ely-auth", authTask);
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
        onNewInstance(null);
    }

    /**
     * Открывает мастер создания инстанса; источник вызова может
     * предвыбрать загрузчик (клик по «умному бандлу»).
     */
    private void onNewInstance(ModLoaderType initialLoader) {
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
                modLoaderRegistry, manifestVersions, initialLoader);
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
        Build build = new Build(
                result.name(),
                result.mcVersion().id(),
                result.type(),
                result.type() == ModLoaderType.VANILLA ? "" : result.loader().loaderVersion());
        BuildRequest request = new BuildRequest(
                build,
                result.mcVersion(),
                result.type() == ModLoaderType.VANILLA ? null : result.loader(),
                List.of());
        String versionId = request.versionId();

        try {
            if (!createBuildUseCase.isBuildAvailable(versionId, request.effectiveName())) {
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
        LOG.info(() -> "Creating instance: " + request.effectiveName()
                + " (" + request.versionId() + ")");

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("install.create.title"));
        progressDialog.show();

        Task<ModdedProfile> task = new Task<>() {
            @Override
            protected ModdedProfile call() throws Exception {
                // Весь пайплайн — в CreateBuildUseCase:
                // isVersionDownloaded -> downloadVersion + повторная проверка
                // -> createBuildDirectory + populateBuildDirectories
                return createBuildUseCase.createBuild(request, progressDialog);
            }
        };
        task.setOnSucceeded(e -> {
            ModdedProfile profile = task.getValue();
            progressDialog.onComplete(new InstallationResult(0, 0, 0, 0, 0,
                    List.of()));
            LOG.info(() -> "Instance created: " + profile.name() + " ("
                    + profile.versionId() + ")");
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
            UiErrors.fail(statusLabel, owner,
                    Lang.tr("instance.create.failed", cause.getMessage()),
                    Lang.tr("instance.create.failed.title"), cause);
        });
        FxTasks.run("instance-create", task);
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
            moddedProfiles = buildRepository.loadProfiles();
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
            Path dir = buildRepository.resolveGameDir(profile);
            FileSystemBuildRepository.ensureProfileFolders(dir);
            java.awt.Desktop.getDesktop().open(dir.toFile());
            statusLabel.setText(Lang.tr("folder.opened", dir));
        } catch (Exception ex) {
            UiErrors.fail(statusLabel,
                    (Stage) root.getScene().getWindow(),
                    Lang.tr("folder.open.failed", ex.getMessage()),
                    Lang.tr("error.folder.title"), ex);
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

        Path gameDir = buildRepository.resolveGameDir(profile);

        // Модальная панель с вкладками; удаление — через onDeleteProfile
        // (его Alert-подтверждение, как раньше из карточки)
        InstanceSettingsDialog.Result result = InstanceSettingsDialog.show(
                (Stage) root.getScene().getWindow(), profile, gameDir,
                this::onDeleteProfile,
                () -> changeInstanceIcon(profile));
        if (result == null) return;

        try {
            // Переименование также переименовывает папку (моды/сохранения/сборки
            // переезжают вместе), поэтому далее выбираем по новому id
            Optional<ModdedProfile> updated = buildRepository.updateProfile(
                    profile.id(), result.jvmArgs(), result.name(),
                    result.memoryMb());
            if (updated.isPresent()) {
                refreshModdedProfiles(updated.get().id());
                statusLabel.setText(Lang.tr("instance.updated",
                        updated.get().name(),
                        FileSystemBuildRepository.formatMemory(
                                updated.get().memoryMb()),
                        ""));
            }
        } catch (IOException e) {
            UiErrors.fail(statusLabel,
                    (Stage) root.getScene().getWindow(),
                    Lang.tr("instance.update.failed", e.getMessage()),
                    Lang.tr("error.update.title"), e);
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
                    var keptDir = buildRepository.delete(profile.id());
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

    // --- Запуск сборки одним вызовом: BuildLaunchManager владеет
    // проверкой, починкой, Java, командой, процессом и разбором выхода.
    // Здесь только поток, статусы и диалоги.

    /**
     * Общая точка запуска для всех кнопок «Играть» на карточках.
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
        LOG.info(() -> "Launch requested: " + profile.name() + " ("
                + profile.versionId() + ", account="
                + account.name() + (account.isOnline() ? ", online)" : ", offline)"));

        // Попытка запуска сразу блокирует все кнопки «Играть»
        // (до обновления по терминальному колбэку)
        versionPlayInFlight = true;
        setLaunchStatus(profile.id(), Lang.tr("launch.verifying"));
        statusLabel.setText(Lang.tr("launch.verify.status", profile.name()));
        if (account.isElyBy()) {
            statusLabel.setText(Lang.tr("ely.refreshing"));
        }

        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog repairDialog = new InstallProgressDialog(owner);
        repairDialog.setTitle(Lang.tr("install.repair.title", profile.name()));

        Task<Void> launchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                buildLaunchManager.launch(profile, account, storage,
                        repairDialog, new BuildLaunchListener() {
                            @Override
                            public void onStage(LaunchStage stage) {
                                Platform.runLater(() -> onLaunchStage(
                                        profile, repairDialog, stage));
                            }

                            @Override
                            public void onFinished() {
                                Platform.runLater(() -> {
                                    refreshLaunchButtons();
                                    statusLabel.setText(Lang.tr(
                                            "launch.exited",
                                            profile.versionId()));
                                });
                            }

                            @Override
                            public void onCrashed(CrashReport report) {
                                Platform.runLater(() -> {
                                    refreshLaunchButtons();
                                    showServerCrashBar(profile, report);
                                    CrashWindow.show(owner, report);
                                });
                            }

                            @Override
                            public void onFailed(String error) {
                                Platform.runLater(() -> {
                                    refreshLaunchButtons();
                                    UiErrors.fail(statusLabel, owner, error,
                                            Lang.tr("launch.launch.failed.title"),
                                            error);
                                });
                            }
                        });
                return null;
            }
        };
        launchTask.setOnFailed(e -> {
            refreshLaunchButtons();
            Throwable cause = launchTask.getException();
            UiErrors.fail(statusLabel, owner,
                    Lang.tr("launch.launch.error", cause.getMessage()),
                    Lang.tr("launch.launch.error.title"), cause);
        });
        FxTasks.run("build-launch", launchTask);
    }

    /** Строка статуса и диалог починки по стадии запуска. */
    private void onLaunchStage(ModdedProfile profile,
                               InstallProgressDialog repairDialog,
                               LaunchStage stage) {
        switch (stage) {
            case CHECKING -> {
                setLaunchStatus(profile.id(), Lang.tr("launch.verifying"));
                statusLabel.setText(
                        Lang.tr("launch.verify.status", profile.name()));
            }
            case REPAIRING -> {
                setLaunchStatus(profile.id(), Lang.tr("launch.repairing"));
                statusLabel.setText(
                        Lang.tr("launch.repair.status", profile.name()));
                repairDialog.show();
            }
            case INSTALLING_JAVA -> {
                setLaunchStatus(profile.id(), Lang.tr("launch.installing"));
                statusLabel.setText(
                        Lang.tr("launch.java.needed", profile.name()));
            }
            case STARTING -> {
                setLaunchStatus(profile.id(), Lang.tr("launch.launching"));
                statusLabel.setText(
                        Lang.tr("launch.starting", profile.name()));
            }
            case RUNNING ->
                statusLabel.setText(Lang.tr("launch.running",
                        profile.versionId(), profile.name()));
        }
    }


    private GameProfile getOrCreateProfile() {
        try {
            selectedProfile = accountManager.getOrCreateDefault(selectedProfile);
        } catch (IOException e) {
            // Проваливаемся к профилю в памяти
            selectedProfile = GameProfile.offline("Player");
        }
        return selectedProfile;
    }

    // ------------------------------------------------------------------
    //  Утилиты
    // ------------------------------------------------------------------
}
