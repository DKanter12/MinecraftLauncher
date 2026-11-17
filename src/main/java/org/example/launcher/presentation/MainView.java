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
import java.util.Map;
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
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ColorPicker;
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
import javafx.scene.shape.Rectangle;
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
import org.example.launcher.infrastructure.mojang.VersionManifestCache;
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
    /** Сортировка библиотеки: послед. запуск / имя / создание / версия. */
    private ComboBox<String> instanceSortCombo;
    /** Сборка, у которой последний запуск упал: красное состояние карточки. */
    private String lastFailedProfileId;

    // Остальные виды
    private VBox instancesView;
    private VBox profilesView;
    private VBox profilesBox;
    private ScrollPane settingsView;
    private ComboBox<Lang.Language> languageCombo;
    private ColorPicker themeBgPicker;
    private ColorPicker themeAccentPicker;
    private HBox themePreview;
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

    // Секция «Рекомендуем»: рисуется только когда источник даёт
    // рекомендованные сборки (метаданные рекомендаций — будущая функция)
    private VBox serverRecommendedBox;

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
    private Label avatarPlaceholder;
    private Label offlineAccountBadge;
    private StackPane avatarRing;
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
        showView(initialView());
    }

    /**
     * Стартовая вкладка: «Библиотека», либо вкладка из системного
     * свойства mdd.openView (для проверки интерфейса, напр.
     * -Dmdd.openView=SERVER). Некорректное значение игнорируется.
     */
    private static View initialView() {
        String flag = System.getProperty("mdd.openView");
        if (flag != null) {
            try {
                return View.valueOf(flag.trim().toUpperCase(
                        java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // неизвестная вкладка — открываем библиотеку
            }
        }
        return View.INSTANCES;
    }

    // --- Верхний Header: бренд, вкладки, профиль игрока ---

    /** Собирает Header: бренд, глобальные вкладки, профиль игрока. */
    private HBox buildHeader() {
        // Бренд-блок: зелёный градиентный брикет с литерой M + название
        Label brandIcon = new Label("M");
        brandIcon.getStyleClass().add("brand-icon");
        Label brandName = new Label("MDD");
        brandName.getStyleClass().add("brand-name");
        Label brandSub = new Label("LAUNCHER");
        brandSub.getStyleClass().add("brand-sub");
        VBox brandText = new VBox(1, brandName, brandSub);
        HBox brandRow = new HBox(9, brandIcon, brandText);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        brandRow.getStyleClass().add("brand-row");

        // Глобальные вкладки навигации: иконка + подпись
        HBox tabs = new HBox(8);
        tabs.getStyleClass().add("header-tabs");
        addHeaderTab(tabs, "\u2630  ", Lang.tr("nav.library"), View.INSTANCES);
        addHeaderTab(tabs, "\u2302  ", Lang.tr("nav.catalog"), View.SERVER);
        addHeaderTab(tabs, "\u2B07  ", Lang.tr("nav.import"), View.IMPORT);
        addHeaderTab(tabs, "\u271A  ", Lang.tr("nav.create"), View.CREATE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Виджет аккаунта (быстрый переключатель; полное управление — в Профилях)
        avatarView = new ImageView();
        avatarView.setFitWidth(38);
        avatarView.setFitHeight(38);
        avatarView.setPreserveRatio(true);
        // Аватар квадратный со скруглёнными углами: круглая маска
        // обрезала лица скинов Ely.by по краям
        javafx.scene.shape.Rectangle avatarMask = new javafx.scene.shape.Rectangle(38, 38);
        avatarMask.setArcWidth(12);
        avatarMask.setArcHeight(12);
        avatarView.setClip(avatarMask);
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
                    }
                    updateAvatar(val);
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

        addAccountButton = new Button("\uFF0B");
        addAccountButton.getStyleClass().add("header-icon-button");
        addAccountButton.setOnAction(e -> onAddAccount());
        addAccountButton.setTooltip(new Tooltip(Lang.tr("account.add.tip")));

        // Аватар в кольце-подложке (скругление и рамка — через CSS)
        // Под кольцом — силуэт-заглушка (когда у аккаунта нет скина),
        // в углу — значок офлайн-аккаунта
        avatarPlaceholder = new Label("\uD83D\uDC64");
        avatarPlaceholder.getStyleClass().add("avatar-placeholder");
        offlineAccountBadge = new Label("\uD83C\uDFE0");
        offlineAccountBadge.getStyleClass().add("avatar-offline-badge");
        offlineAccountBadge.setTooltip(new Tooltip(
                Lang.tr("account.offline.tip")));
        StackPane.setAlignment(offlineAccountBadge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(offlineAccountBadge, new Insets(0, -4, -4, 0));
        avatarRing = new StackPane(avatarPlaceholder, avatarView,
                offlineAccountBadge);
        avatarRing.getStyleClass().add("avatar-ring");
        offlineAccountBadge.setVisible(false);
        offlineAccountBadge.setManaged(false);

        HBox accountBox = new HBox(8, avatarRing, accountCombo,
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

    /** Текстовый таб Header (с иконкой-глифом); подсветка в {@code showView}. */
    private void addHeaderTab(HBox tabs, String icon, String text, View view) {
        Button tab = new Button(icon + text);
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
        // Прогресс-окно видно сразу: скачивание и распаковка
        // отображаются полосой, файлом и статистикой — не мелкой строкой
        progressDialog.show();
        task.setOnSucceeded(e -> {
            ModpackImporter.Result result = task.getValue();
            LOG.info(() -> "Import finished: " + result.packName()
                    + " — downloaded=" + result.downloaded()
                    + ", unpacked=" + result.unpacked()
                    + ", skipped=" + result.skipped()
                    + ", failed=" + result.failed());
            importStatusLabel.setText(Lang.tr("import.done",
                    result.packName(), result.downloaded(),
                    result.unpacked(), result.failed()));
            statusLabel.setText(Lang.tr("import.done.status",
                    result.packName()));
            jumpToInstance(result.profile());
        });
        task.setOnFailed(e -> {
            Throwable cause = task.getException();
            if (cause instanceof ModpackImporter.DuplicatePackException dup) {
                LOG.info(() -> "Import rejected: duplicate pack "
                        + dup.packName());
                importStatusLabel.setText(Lang.tr("import.duplicate",
                        dup.packName()));
                statusLabel.setText(Lang.tr("import.duplicate.short"));
            } else {
                LOG.log(Level.WARNING, "Modpack import failed", cause);
                importStatusLabel.setText(Lang.tr("import.failed",
                        cause == null ? "" : cause.getMessage()));
                statusLabel.setText(Lang.tr("import.failed.short"));
            }
            progressDialog.onComplete(new InstallationResult(
                    0, 0, 0, 1, 0, List.of()));
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

        // Бандл ещё в разработке: нейтральная метка «Скоро»,
        // карточка не кликается
        Label soonBadge = new Label(Lang.tr("create.bundle.soon"));
        soonBadge.getStyleClass().add("bundle-badge-soon");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        // Шапка карточки: иконка + название + бейдж загрузчика + «Скоро»
        HBox header = new HBox(10, iconLabel, name, headerSpacer,
                loaderBadge, soonBadge);
        header.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(Lang.tr(descKey));
        description.getStyleClass().add("pack-desc");
        description.setWrapText(true);

        VBox card = new VBox(10, header, description);
        card.getStyleClass().addAll("bundle-card", "bundle-card-soon");
        card.setPrefWidth(300);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
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
        // Поиск инстансов: лупа слева и кнопка очистки справа
        instanceSearchField = new TextField();
        instanceSearchField.setPromptText(Lang.tr("search.instances"));
        instanceSearchField.getStyleClass().add("search-field");
        instanceSearchField.setPadding(new Insets(8, 30, 8, 34));
        instanceSearchField.setMaxWidth(Double.MAX_VALUE);
        instanceSearchField.textProperty().addListener((obs, old, val) -> {
            instanceSearchText = val == null ? "" : val.trim().toLowerCase();
            refreshInstanceCards();
        });

        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.getStyleClass().add("search-icon");
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 11));
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        Button searchClear = new Button("\u2715");
        searchClear.getStyleClass().add("search-clear");
        searchClear.setTooltip(new Tooltip(Lang.tr("search.clear")));
        searchClear.setVisible(false);
        searchClear.setManaged(false);
        StackPane.setMargin(searchClear, new Insets(0, 8, 0, 0));
        StackPane.setAlignment(searchClear, Pos.CENTER_RIGHT);
        searchClear.setOnAction(e -> instanceSearchField.clear());
        instanceSearchField.textProperty().addListener((obs, old, val) -> {
            boolean has = val != null && !val.isEmpty();
            searchClear.setVisible(has);
            searchClear.setManaged(has);
        });
        StackPane searchWrap = new StackPane(instanceSearchField, searchIcon,
                searchClear);
        searchWrap.getStyleClass().add("search-wrap");
        searchWrap.setPrefWidth(320);
        searchWrap.setMinWidth(220);

        // --- Заголовок страницы: «Библиотека» + счётчик сборок ---
        Label libraryTitle = new Label(Lang.tr("nav.library"));
        libraryTitle.getStyleClass().add("title-label");
        instanceCountLabel = new Label();
        instanceCountLabel.getStyleClass().add("store-count");
        instanceCountLabel.setTooltip(new Tooltip(Lang.tr("instances.count.tip")));
        HBox titleRow = new HBox(12, libraryTitle, instanceCountLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Button createBuildButton = new Button(
                "\u271A  " + Lang.tr("instances.create"));
        createBuildButton.getStyleClass().add("pack-install");
        createBuildButton.setTooltip(new Tooltip(Lang.tr("instances.create.tip")));
        createBuildButton.setOnAction(e -> onNewInstance());

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topRow = new HBox(14, titleRow, topSpacer, searchWrap,
                createBuildButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // --- Боковая навигация: фильтры + сортировка ---
        Label filtersTitle = new Label(Lang.tr("filter.title").toUpperCase());
        filtersTitle.getStyleClass().add("side-nav-title");

        instanceFilterChips = new VBox(2);
        instanceFilterChips.getStyleClass().add("filter-row");
        instanceFilterChips.setAlignment(Pos.TOP_LEFT);
        instanceFilterGroup = new ToggleGroup();
        addInstanceFilterChip(Lang.tr("filter.all"), null);
        for (ModLoaderType loader : ModLoaderType.values()) {
            addInstanceFilterChip(loader.displayName(), loader);
        }

        Label sortTitle = new Label(Lang.tr("sort.title").toUpperCase());
        sortTitle.getStyleClass().add("side-nav-title");
        instanceSortCombo = new ComboBox<>();
        instanceSortCombo.getItems().addAll(
                Lang.tr("sort.lastplayed"),
                Lang.tr("sort.name"),
                Lang.tr("sort.created"),
                Lang.tr("sort.version"));
        instanceSortCombo.getSelectionModel().select(0);
        instanceSortCombo.setMaxWidth(Double.MAX_VALUE);
        instanceSortCombo.valueProperty().addListener(
                (obs, old, val) -> refreshInstanceCards());

        Region navSpacer = new Region();
        VBox.setVgrow(navSpacer, Priority.ALWAYS);
        VBox filters = new VBox(2, filtersTitle, instanceFilterChips,
                navSpacer, sortTitle, instanceSortCombo);
        filters.getStyleClass().add("side-nav");
        filters.setPrefWidth(216);
        filters.setMinWidth(216);
        filters.setPadding(new Insets(6, 4, 10, 4));

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

        HBox layout = new HBox(20, filters, content);
        HBox.setHgrow(content, Priority.ALWAYS);

        VBox view = new VBox(0, layout);
        view.setPadding(new Insets(18, 24, 18, 24));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
    }

    /**
     * Строка фильтра в боковой навигации: иконка загрузчика (или глиф
     * «все»), название и счётчик сборок на правом краю.
     */
    private ToggleButton makeInstanceFilterChip(String text,
                                                 ModLoaderType loader) {
        long count = loader == null ? moddedProfiles.size()
                : moddedProfiles.stream().filter(
                        p -> p.loaderType() == loader).count();

        Label icon = null;
        ImageView loaderIcon = null;
        if (loader == null) {
            icon = new Label("\u2261");
            icon.getStyleClass().add("nav-icon");
        } else {
            loaderIcon = loaderImage(loader);
            if (loaderIcon == null) {
                icon = new Label(iconText(loader));
                icon.getStyleClass().add("nav-icon");
            }
        }

        Label nameLabel = new Label(text);
        nameLabel.setMouseTransparent(true);

        Label countBadge = new Label(String.valueOf(count));
        countBadge.getStyleClass().add("count-badge");
        countBadge.setMouseTransparent(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(9);
        content.setAlignment(Pos.CENTER_LEFT);
        if (icon != null) {
            content.getChildren().add(icon);
        } else if (loaderIcon != null) {
            loaderIcon.setFitWidth(15);
            loaderIcon.setFitHeight(15);
            content.getChildren().add(loaderIcon);
        }
        content.getChildren().addAll(nameLabel, spacer, countBadge);

        ToggleButton chip = new ToggleButton();
        chip.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        chip.setGraphic(content);
        chip.getStyleClass().add("nav-item");
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
        // Плавное появление страницы (быстрый fade, не мешает работе)
        animateViewIn(switch (view) {
            case INSTANCES -> instancesView;
            case PROFILES -> profilesView;
            case SERVER -> serverView;
            case IMPORT -> importView;
            case CREATE -> createView;
            case SETTINGS -> settingsView;
        });
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

    /** Быстрое затухание вида: opacity 0 → 1 за 130 мс. */
    private void animateViewIn(javafx.scene.Node view) {
        view.setOpacity(0);
        FadeTransition fade = new FadeTransition(
                javafx.util.Duration.millis(130), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setCycleCount(1);
        fade.play();
    }

    /**
     * Плавный hover-lift: карточка мягко приподнимается при наведении
     * и опускается при уходе курсора (короткие Timelines, ~100 мс).
     */
    private static void hoverLift(javafx.scene.layout.Region node,
                                  double lift) {
        node.setOnMouseEntered(e -> {
            Timeline liftIn = new Timeline(new KeyFrame(
                    javafx.util.Duration.millis(90),
                    new KeyValue(node.translateYProperty(), lift)));
            liftIn.setCycleCount(1);
            liftIn.play();
        });
        node.setOnMouseExited(e -> {
            Timeline liftOut = new Timeline(new KeyFrame(
                    javafx.util.Duration.millis(120),
                    new KeyValue(node.translateYProperty(), 0)));
            liftOut.setCycleCount(1);
            liftOut.play();
        });
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
        // После установки новой сборки показываем «Все»: карточка
        // точно видна независимо от того, какие чипы фильтра стояли
        instanceLoaderFilter = null;
        syncInstanceFilterChips();
        if (!instanceSearchField.getText().isEmpty()) {
            instanceSearchField.clear();
        }
        showView(View.INSTANCES);
        refreshModdedProfiles(profile.id());
        scrollInstanceIntoView();
    }

    /**
     * Прокручивает список инстансов к выделенной карточке, чтобы после
     * установки/импорта новой сборки пользователь сразу видел её в
     * окне (карточка «прыгает» в центр видимой области).
     */
    private void scrollInstanceIntoView() {
        if (instancesScroll == null || selectedInstanceId == null) {
            return;
        }
        Platform.runLater(() -> {
            instanceCards.applyCss();
            instanceCards.layout();
            javafx.scene.Node target = null;
            for (javafx.scene.Node node : instanceCards.getChildren()) {
                if (selectedInstanceId.equals(node.getUserData())) {
                    target = node;
                    break;
                }
            }
            if (target == null) return;
            javafx.geometry.Bounds b = target.getBoundsInParent();
            double content = instanceCards.getHeight();
            double viewport = instancesScroll.getViewportBounds().getHeight();
            if (content <= viewport || (b.getMinY() > 0
                    && b.getMaxY() < viewport)) {
                return;
            }
            double fraction = (b.getMinY() + b.getHeight() / 2
                    - viewport / 2) / (content - viewport);
            instancesScroll.setVvalue(Math.max(0,
                    Math.min(1, fraction)));
            if (target instanceof javafx.scene.Parent parent
                    && !parent.getStyleClass()
                            .contains("instance-card-selected")) {
                parent.getStyleClass().add("instance-card-selected");
            }
        });
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
        sortShown(shown);
        for (ModdedProfile p : shown) {
            instanceCards.getChildren().add(buildInstanceCard(p));
        }
        instanceCards.getChildren().add(buildAddInstanceCard());
        if (scroll > 0 && instancesScroll != null) {
            double restore = scroll;
            Platform.runLater(() -> instancesScroll.setVvalue(restore));
        }
    }

    /**
     * Сортировка библиотеки по выбору пользователя: последний запуск
     * (умолчание), имя, дата создания, версия Minecraft.
     */
    private void sortShown(List<ModdedProfile> shown) {
        int mode = instanceSortCombo == null
                || instanceSortCombo.getValue() == null
                ? 0 : instanceSortCombo.getSelectionModel().getSelectedIndex();
        switch (mode) {
            case 1 -> shown.sort(Comparator.comparing(
                    p -> p.name().toLowerCase()));
            case 2 -> shown.sort(Comparator.comparing(
                    (ModdedProfile p) -> p.createdTime().orElse(null),
                    Comparator.nullsLast(Comparator.naturalOrder())));
            case 3 -> shown.sort(Comparator.comparing(
                    (ModdedProfile p) -> p.minecraftVersion().toLowerCase(),
                    new MinecraftVersionComparator()));
            default -> shown.sort(Comparator
                    .comparing((ModdedProfile p) -> p.lastPlayedTime()
                            .orElse(null),
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .reversed()
                    .thenComparing(p -> p.name().toLowerCase()));
        }
    }

    private VBox buildInstanceCard(ModdedProfile profile) {
        boolean selected = profile.id().equals(selectedInstanceId);
        boolean launching = profile.id().equals(launchingProfileId);
        boolean failed = profile.id().equals(lastFailedProfileId);
        boolean neverPlayed = profile.lastPlayedTime().isEmpty();

        VBox card = new VBox(10);
        card.setPrefWidth(252);
        card.setUserData(profile.id());
        card.getStyleClass().addAll("instance-card",
                accentClass(profile.loaderType()));
        if (selected) {
            card.getStyleClass().add("instance-card-selected");
        }
        if (launching) {
            card.getStyleClass().add("instance-card-launching");
        }
        if (failed && !launching) {
            card.getStyleClass().add("instance-card-error");
        }
        if (neverPlayed) {
            card.getStyleClass().add("instance-card-never");
        }
        hoverLift(card, -2);

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

        // Строка метаданных: версия Minecraft + загрузчик + моды
        Label badge = new Label(cardSubLine(profile));
        badge.getStyleClass().add("instance-badge");

        // Дата последнего запуска — мелкий серый текст
        Label played = new Label(cardPlayedLine(profile));
        played.getStyleClass().add("instance-card-played");
        played.setTextOverrun(OverrunStyle.ELLIPSIS);
        played.setMaxWidth(Double.MAX_VALUE);

        VBox titles = new VBox(6, name, badge, played);
        card.getChildren().add(titles);

        boolean retry = failed && !launching;
        String playText = launching ? launchingStatusText
                : retry ? Lang.tr("card.retry").toUpperCase()
                : "\u25B6  " + Lang.tr("button.play").toUpperCase();
        Button play = new Button(playText);
        play.getStyleClass().add("card-play");
        if (retry) {
            play.getStyleClass().add("card-play-retry");
        }
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
        // Контекстное меню правым кликом: Node не имеет setContextMenu
        // (свойство объявлено на Control), поэтому показываем вручную
        ContextMenu contextMenu = buildInstanceContextMenu(profile);
        card.setOnContextMenuRequested(e -> {
            contextMenu.show(card, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return card;
    }

    /**
     * Контекстное меню карточки сборки (правый клик): настройки,
     * папка игры, смена иконки и удаление.
     */
    private ContextMenu buildInstanceContextMenu(ModdedProfile profile) {
        MenuItem edit = new MenuItem("\u2699  " + Lang.tr("context.edit"));
        edit.setOnAction(e -> onEditProfile(profile));

        MenuItem folder = new MenuItem("\u2750  " + Lang.tr("button.folder"));
        folder.setOnAction(e -> onOpenProfileFolder(profile));

        MenuItem icon = new MenuItem("\uD83D\uDDBC  "
                + Lang.tr("instance.icon.change"));
        icon.setOnAction(e -> changeInstanceIcon(profile));

        MenuItem delete = new MenuItem("\u2716  " + Lang.tr("context.delete"));
        delete.getStyleClass().add("menu-item-danger");
        delete.setOnAction(e -> onDeleteProfile(profile));

        ContextMenu menu = new ContextMenu(edit, folder, icon,
                new SeparatorMenuItem(), delete);
        return menu;
    }

    /**
     * Обложка карточки (16:9): кастомная картинка инстанса на весь баннер,
     * иначе тёмный градиент с логотипом загрузчика по центру. В правом
     * верхнем углу — два иконочных действия, видимые при наведении:
     * открыть папку игры и меню настроек профиля.
     */
    private javafx.scene.Node buildInstanceBanner(ModdedProfile profile) {
        StackPane banner = new StackPane();
        banner.setPrefHeight(148);
        banner.setMaxHeight(148);
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
        card.setPrefWidth(252);
        card.setMinHeight(190);
        card.getStyleClass().add("add-instance-card");
        card.setCursor(javafx.scene.Cursor.HAND);
        hoverLift(card, -2);
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
        Label manageHint = new Label(Lang.tr("profiles.hint.top"));
        manageHint.getStyleClass().add("quick-select-label");
        HBox buttons = new HBox(8, addAccount, manageHint);
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

    private ScrollPane buildSettingsView() {
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

        Label themeTitle = new Label(Lang.tr("settings.theme"));
        themeTitle.getStyleClass().add("section-title");

        Label bgCaption = new Label(Lang.tr("settings.theme.bg"));
        bgCaption.getStyleClass().add("quick-select-label");
        themeBgPicker = new ColorPicker(javafx.scene.paint.Color
                .web(pickValue(ThemeManager.customBg(),
                        ThemeManager.DEFAULT_BG)));
        Label accentCaption = new Label(Lang.tr("settings.theme.accent"));
        accentCaption.getStyleClass().add("quick-select-label");
        themeAccentPicker = new ColorPicker(javafx.scene.paint.Color
                .web(pickValue(ThemeManager.customAccent(),
                        ThemeManager.DEFAULT_ACCENT)));

        VBox bgPickBox = new VBox(4, bgCaption, themeBgPicker);
        bgPickBox.setPrefWidth(220);
        VBox accentPickBox = new VBox(4, accentCaption, themeAccentPicker);
        accentPickBox.setPrefWidth(220);
        HBox pickersRow = new HBox(16, bgPickBox, accentPickBox);
        pickersRow.getStyleClass().add("theme-pickers");

        themePreview = new HBox(6);
        themePreview.getStyleClass().add("theme-preview");
        refreshThemePreview();

        Button applyTheme = new Button(Lang.tr("settings.theme.apply"));
        applyTheme.getStyleClass().add("quick-select-button");
        applyTheme.setOnAction(e -> onApplyCustomTheme());
        Button resetTheme = new Button(Lang.tr("settings.theme.standard"));
        resetTheme.getStyleClass().add("quick-select-button");
        resetTheme.setOnAction(e -> onResetTheme());
        HBox themeButtons = new HBox(8, applyTheme, resetTheme);

        Label themeHint = new Label(Lang.tr("settings.theme.hint"));
        themeHint.getStyleClass().add("quick-select-label");
        themeHint.setWrapText(true);
        VBox themeBox = new VBox(8, themeTitle, pickersRow, themePreview,
                themeButtons, themeHint);
        themeBox.getStyleClass().add("settings-group");

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

        VBox content = new VBox(12, languageBox, themeBox, dirBox, javaBox,
                serverBox, updatesBox, buildsBox, aboutBox);
        content.setPadding(new Insets(16));

        // Настройки не помещаются на низких окнах — даём прокрутку
        ScrollPane view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        view.getStyleClass().add("cards-scroll");
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
        if (themePreview != null) {
            refreshThemePreview();
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

    /** Красиво превращает Color в hex-строку #RRGGBB. */
    private static String toHexColor(javafx.scene.paint.Color c) {
        if (c == null) {
            return null;
        }
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private static String pickValue(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    /** Перестраивает полоску превью из текущих цветов пикеров. */
    private void refreshThemePreview() {
        if (themePreview == null || themeBgPicker == null
                || themeAccentPicker == null) {
            return;
        }
        themePreview.getChildren().clear();
        List<String> swatches = ThemeManager.previewSwatches(
                toHexColor(themeBgPicker.getValue()),
                toHexColor(themeAccentPicker.getValue()));
        for (int i = 0; i < swatches.size(); i++) {
            javafx.scene.layout.Region swatch =
                    new javafx.scene.layout.Region();
            swatch.getStyleClass().add("theme-swatch");
            swatch.setStyle("-fx-background-color: " + swatches.get(i)
                    + "; -fx-background-radius: 8;");
            if (i == swatches.size() - 1) {
                // Акцентный образец — чуть шире, это «главный» цвет
                swatch.getStyleClass().add("theme-swatch-accent");
            }
            themePreview.getChildren().add(swatch);
        }
    }

    /** Применяет тему из выбранных цветов и сохраняет её. */
    private void onApplyCustomTheme() {
        String bg = toHexColor(themeBgPicker.getValue());
        String accent = toHexColor(themeAccentPicker.getValue());
        ThemeManager.setCustom(bg, accent);
        refreshThemePreview();
        try {
            LauncherPreferences saved = preferences.loadPreferences();
            preferences.savePreferences(new LauncherPreferences(
                    saved.lastSelectedVersion(),
                    saved.lastSelectedAccount(),
                    saved.buildsGitUrl(), saved.buildsSourceMode(),
                    saved.yandexDiskLink(), saved.buildsToken(),
                    saved.language(), "CUSTOM", bg, accent));
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("settings.save.failed", e.getMessage()));
            return;
        }
        statusLabel.setText(Lang.tr("settings.theme.done"));
    }

    /** Возвращает стандартную тему и очищает пользовательские цвета. */
    private void onResetTheme() {
        ThemeManager.setStandard();
        themeBgPicker.setValue(javafx.scene.paint.Color
                .web(ThemeManager.DEFAULT_BG));
        themeAccentPicker.setValue(javafx.scene.paint.Color
                .web(ThemeManager.DEFAULT_ACCENT));
        refreshThemePreview();
        try {
            LauncherPreferences saved = preferences.loadPreferences();
            preferences.savePreferences(new LauncherPreferences(
                    saved.lastSelectedVersion(),
                    saved.lastSelectedAccount(),
                    saved.buildsGitUrl(), saved.buildsSourceMode(),
                    saved.yandexDiskLink(), saved.buildsToken(),
                    saved.language(), "STANDARD", null, null));
        } catch (IOException e) {
            statusLabel.setText(
                    Lang.tr("settings.save.failed", e.getMessage()));
            return;
        }
        statusLabel.setText(Lang.tr("settings.theme.standard.done"));
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
                    saved.language(), saved.theme(),
                    saved.customThemeBg(), saved.customThemeAccent()));
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
        serverModeLabel.getStyleClass().add("catalog-source-line");

        // --- Hero-заголовок: «Открой новые миры» + поиск + обновление ---
        Label title = new Label(Lang.tr("catalog.hero.title"));
        title.getStyleClass().add("catalog-hero-title");
        Label subtitle = new Label(Lang.tr("catalog.hero.subtitle"));
        subtitle.getStyleClass().add("catalog-hero-subtitle");
        subtitle.setWrapText(true);
        VBox heroText = new VBox(4, title, subtitle);
        HBox.setHgrow(heroText, Priority.ALWAYS);
        heroText.setMaxWidth(Double.MAX_VALUE);

        serverSearchField = new TextField();
        serverSearchField.setPromptText(Lang.tr("server.builds.search.prompt"));
        serverSearchField.getStyleClass().add("search-field");
        serverSearchField.setPrefWidth(300);
        serverSearchField.textProperty().addListener((obs, old, val) -> {
            serverSearchText = val == null ? "" : val.trim().toLowerCase();
            renderServerBuilds();
        });

        Button refresh = new Button("\u21BB  "
                + Lang.tr("server.builds.refresh"));
        refresh.getStyleClass().add("catalog-refresh-button");
        refresh.setTooltip(new Tooltip(Lang.tr("server.builds.refresh.tip")));
        refresh.setOnAction(e -> {
            serverMcFilter = null;
            refreshServerBuildsView();
        });

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);
        HBox heroRow = new HBox(16, heroText, serverSearchField, refresh);
        heroRow.setAlignment(Pos.CENTER_LEFT);

        // Мета-строка: количество сборок + источник (компактные пилюли)
        serverCountLabel = new Label();
        serverCountLabel.getStyleClass().add("store-count");
        HBox metaRow = new HBox(8, serverCountLabel, serverModeLabel);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(10, heroRow, metaRow);
        hero.getStyleClass().add("catalog-hero");

        // --- Боковая панель фильтров ---
        serverSortCombo = new ComboBox<>();
        serverSortCombo.getItems().addAll(
                Lang.tr("server.builds.sort.recommended"),
                Lang.tr("server.builds.sort.newest"),
                Lang.tr("server.builds.sort.name"));
        serverSortCombo.getSelectionModel().select(0);
        serverSortCombo.setMaxWidth(Double.MAX_VALUE);
        serverSortCombo.valueProperty().addListener(
                (obs, old, val) -> renderServerBuilds());

        Label sortTitle = new Label(
                Lang.tr("server.builds.filters.sort").toUpperCase());
        sortTitle.getStyleClass().add("side-nav-title");

        Label mcTitle = new Label(
                Lang.tr("server.builds.filters.mc").toUpperCase());
        mcTitle.getStyleClass().add("side-nav-title");
        serverMcChips = new HBox(6);
        serverMcChips.getStyleClass().add("filter-row");

        Label loaderTitle = new Label(
                Lang.tr("server.builds.filters.loader").toUpperCase());
        loaderTitle.getStyleClass().add("side-nav-title");
        serverLoaderBox = new VBox(4);

        Button resetFilters = new Button(Lang.tr("catalog.filters.reset"));
        resetFilters.getStyleClass().add("catalog-reset-button");
        resetFilters.setMaxWidth(Double.MAX_VALUE);
        resetFilters.setOnAction(e -> resetServerFilters());

        Region navSpacer = new Region();
        VBox.setVgrow(navSpacer, Priority.ALWAYS);
        VBox filters = new VBox(2, sortTitle, serverSortCombo, mcTitle,
                serverMcChips, loaderTitle, serverLoaderBox, navSpacer,
                resetFilters);
        filters.getStyleClass().add("side-nav");
        filters.setPrefWidth(224);
        filters.setMinWidth(224);
        filters.setPadding(new Insets(6, 6, 12, 6));
        VBox.setVgrow(filters, Priority.NEVER);

        // --- Секция «Рекомендуем» (появится, когда появятся данные) ---
        serverRecommendedBox = new VBox(12);
        serverRecommendedBox.getStyleClass().add("catalog-recommended");
        serverRecommendedBox.setVisible(false);
        serverRecommendedBox.setManaged(false);

        // --- Сетка карточек ---
        serverBuildsList = new VBox(0);
        serverCardsFlow = new FlowPane();
        serverCardsFlow.setHgap(14);
        serverCardsFlow.setVgap(14);
        serverCardsFlow.setPadding(new Insets(4));
        serverCardsFlow.getStyleClass().add("cards-flow");
        serverCardsFlow.getChildren().add(serverBuildsList);

        ScrollPane scroll = new ScrollPane(
                new VBox(12, serverRecommendedBox, serverCardsFlow));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // --- Плашка Crash Analyzer ---
        serverCrashBar = buildServerCrashBar();
        serverCrashBar.setVisible(false);
        serverCrashBar.setManaged(false);

        HBox content = new HBox(20, filters, scroll);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        VBox view = new VBox(14, hero, content, serverCrashBar);
        view.setPadding(new Insets(16, 24, 18, 24));
        VBox.setVgrow(view, Priority.ALWAYS);
        return view;
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

    /** Название активного источника сборок — для карточек каталога. */
    private String buildsSourceName() {
        if (buildsModeCombo == null) {
            return Lang.tr("settings.builds.local");
        }
        DistributionSources.BuildsSource mode = buildsModeCombo
                .getSelectionModel().getSelectedItem();
        return switch (mode == null
                ? DistributionSources.BuildsSource.LOCAL : mode) {
            case LOCAL -> Lang.tr("settings.builds.local");
            case GITHUB -> Lang.tr("settings.builds.github");
            case YANDEX -> Lang.tr("settings.builds.yandex");
        };
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
        showServerBuildsLoading();
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
            renderServerBuilds(true);
        });
        task.setOnFailed(e -> {
            serverBuildsLoading = false;
            serverBuilds = List.of();
            refreshServerLoaderChecks();
            Throwable cause = task.getException();
            LOG.log(Level.WARNING, "Server builds list failed", cause);
            showServerBuildsState("\u26A0",
                    Lang.tr("catalog.state.error.title"),
                    Lang.tr("server.builds.load.failed",
                            cause == null ? "" : cause.getMessage()),
                    Lang.tr("catalog.state.error.retry"),
                    this::refreshServerBuildsView);
        });
        FxTasks.run("server-builds-list", task);
    }

    /** Состояние загрузки каталога: мягкий спиннер по центру. */
    private void showServerBuildsLoading() {
        VBox pane = new VBox(12);
        pane.getStyleClass().add("state-pane");
        pane.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("state-spinner");
        Label text = new Label(Lang.tr("catalog.state.loading"));
        text.getStyleClass().add("state-title");
        pane.getChildren().addAll(spinner, text);
        StackPane state = new StackPane(pane);
        state.getStyleClass().add("state-wrap");
        animateViewIn(state);
        serverCardsFlow.getChildren().setAll(state);
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
            // Источник не сообщил версию игры — подпись «Не указана»,
            // без «unknown» в интерфейсе
            String text = "unknown".equals(version)
                    ? Lang.tr("catalog.mc.unknown") : version;
            chips.add(makeServerMcChip(text, version));
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

    // ------------------------------------------------------------------
    //  Каталог: сброс фильтров, состояния, рекомендации
    // ------------------------------------------------------------------

    /** Полный сброс фильтров каталога к состоянию «показать всё». */
    private void resetServerFilters() {
        serverSearchField.clear();
        serverSearchText = "";
        serverMcFilter = null;
        for (CheckBox cb : serverLoaderChecks) {
            cb.setSelected(false);
        }
        if (serverSortCombo != null) {
            serverSortCombo.getSelectionModel().select(0);
        }
        renderServerBuilds();
    }

    /**
     * Единая вёрстка состояний каталога: загрузка, ошибка, пусто,
     * нет совпадений. Кнопка действия — реальный обработчик.
     */
    private void showServerBuildsState(String icon, String title,
            String subtitle, String actionText, Runnable action) {
        VBox pane = new VBox(10);
        pane.getStyleClass().add("state-pane");
        pane.setAlignment(Pos.CENTER);

        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("state-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox text = new VBox(6, titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("state-subtitle");
            sub.setWrapText(true);
            sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            sub.setMaxWidth(460);
            text.getChildren().add(sub);
        }

        pane.getChildren().addAll(iconLabel, text);
        if (actionText != null && action != null) {
            Button actionButton = new Button(actionText);
            actionButton.getStyleClass().add("state-button");
            actionButton.setOnAction(e -> action.run());
            pane.getChildren().add(actionButton);
        }

        StackPane state = new StackPane(pane);
        state.getStyleClass().add("state-wrap");
        animateViewIn(state);
        serverCardsFlow.getChildren().setAll(state);
    }

    /**
     * Сборки для секции «Рекомендуем». Механизма рекомендаций в
     * источнике данных пока нет, поэтому список пуст и секция не
     * рисуется: никаких выдуманных сборок. Когда сервер начнёт
     * отдавать метаданные рекомендаций, верните сюда реальные
     * сборки — крупные карточки появятся автоматически.
     */
    private List<BuildSummary> recommendedServerBuilds() {
        return List.of();
    }

    /** Рисует секцию «Рекомендуем», если есть рекомендованные сборки. */
    private void renderRecommended() {
        if (serverRecommendedBox == null) {
            return;
        }
        List<BuildSummary> recommended = serverBuilds.isEmpty()
                ? List.of() : recommendedServerBuilds();
        if (recommended.isEmpty()) {
            serverRecommendedBox.setVisible(false);
            serverRecommendedBox.setManaged(false);
            serverRecommendedBox.getChildren().clear();
            return;
        }
        Label title = new Label(Lang.tr("catalog.recommended"));
        title.getStyleClass().add("section-title");
        HBox cards = new HBox(14);
        for (BuildSummary summary : recommended) {
            cards.getChildren().add(buildRecommendedCard(summary));
        }
        serverRecommendedBox.getChildren().setAll(title, cards);
        serverRecommendedBox.setVisible(true);
        serverRecommendedBox.setManaged(true);
    }

    /**
     * Крупная карточка рекомендации: обложка 16:9 с наложением,
     * название, описание, чипы и кнопка установки.
     */
    private VBox buildRecommendedCard(BuildSummary summary) {
        VBox card = new VBox(0);
        card.getStyleClass().add("pack-card");
        card.setPrefWidth(520);
        hoverLift(card, -2);

        StackPane cover = buildBuildCover(summary, true);

        VBox body = new VBox(8);
        body.getStyleClass().add("pack-card-body");
        Label name = new Label(summary.displayName());
        name.getStyleClass().add("pack-name-title");
        HBox chips = new HBox(6, loaderChip(summary));
        Label mcBadge = mcChip(summary);
        if (mcBadge != null) {
            chips.getChildren().add(mcBadge);
        }
        VBox text = new VBox(6, name, chips);
        if (summary.description() != null
                && !summary.description().isBlank()) {
            Label description = new Label(summary.description());
            description.getStyleClass().add("pack-desc");
            description.setWrapText(true);
            text.getChildren().add(description);
        }
        HBox.setHgrow(text, Priority.ALWAYS);

        Button install = new Button(Lang.tr("server.builds.install"));
        install.getStyleClass().add("pack-install");
        install.setOnAction(e -> installServerBuild(summary));
        VBox bottom = new VBox(install);
        bottom.getStyleClass().add("pack-card-footer");

        body.getChildren().addAll(text, bottom);
        card.getChildren().addAll(cover, body);
        return card;
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
     * загрузчиков, категории; сортировка: рекомендованные/новые/по имени.
     */
    private void renderServerBuilds() {
        renderServerBuilds(false);
    }

    private void renderServerBuilds(boolean animateCards) {
        if (serverCountLabel != null) {
            serverCountLabel.setText(!serverBuilds.isEmpty()
                    ? Lang.tr("server.builds.count", serverBuilds.size())
                    : "");
        }
        rebuildServerMcChips();
        renderRecommended();
        List<BuildSummary> visible = filteredServerBuilds();
        if (visible.isEmpty()) {
            if (serverBuilds.isEmpty()) {
                // Каталог пуст: источник не настроен или без сборок
                showServerBuildsState("\u2708",
                        Lang.tr("catalog.state.empty.title"),
                        Lang.tr("server.builds.empty"),
                        Lang.tr("server.builds.refresh"),
                        this::refreshServerBuildsView);
            } else {
                // Есть сборки, но фильтры/поиск ничего не дали
                showServerBuildsState("\u2315",
                        Lang.tr("catalog.state.noresults.title"),
                        Lang.tr("catalog.state.noresults.hint"),
                        Lang.tr("catalog.filters.reset"),
                        this::resetServerFilters);
            }
            return;
        }
        List<VBox> cards = new ArrayList<>();
        for (BuildSummary summary : visible) {
            cards.add(buildServerBuildCard(summary));
        }
        serverCardsFlow.getChildren().setAll(cards);
        if (animateCards) {
            staggerCardsIn(cards);
        }
    }

    /** Плавное каскадное появление карточек (лёгкое, без сдвигов). */
    private void staggerCardsIn(List<VBox> cards) {
        int maxStagger = 12;
        for (int i = 0; i < cards.size() && i < maxStagger; i++) {
            VBox card = cards.get(i);
            card.setOpacity(0);
            FadeTransition fade = new FadeTransition(
                    javafx.util.Duration.millis(140), card);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(javafx.util.Duration.millis(i * 35));
            fade.setCycleCount(1);
            fade.play();
        }
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
     * Карточка сборки: обложка (icon.png из источника, иначе
     * градиент с названием), чипы загрузчика и Minecraft, название,
     * описание, источник и версия сборки, кнопка «Установить».
     * Компактная вертикальная компоновка: все строки по одной,
     * длинный текст обрезается многоточием.
     */
    private VBox buildServerBuildCard(BuildSummary summary) {
        VBox card = new VBox(0);
        card.getStyleClass().add("pack-card");
        card.setPrefWidth(288);
        hoverLift(card, -3);

        StackPane cover = buildBuildCover(summary, false);

        VBox body = new VBox(6);
        body.getStyleClass().add("pack-card-body");

        HBox chips = new HBox(6, loaderChip(summary));
        Label mcBadge = mcChip(summary);
        if (mcBadge != null) {
            chips.getChildren().add(mcBadge);
        }

        // Название сборки в тексте карточки — независимо от того,
        // что на обложке (icon.png или название)
        Label name = new Label(summary.displayName());
        name.getStyleClass().add("pack-name-title");

        Label description = null;
        if (summary.description() != null
                && !summary.description().isBlank()) {
            description = new Label(summary.description());
            description.getStyleClass().add("pack-desc");
        }

        HBox meta = new HBox(6);
        Label source = new Label(Lang.tr("catalog.card.source",
                buildsSourceName()));
        source.getStyleClass().add("pack-card-source");
        Region metaSpacer = new Region();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);
        Label buildVersion = new Label("v" + shortVersion(summary.version()));
        buildVersion.getStyleClass().add("pack-card-version");
        meta.getChildren().addAll(source, metaSpacer, buildVersion);

        Button install = new Button(Lang.tr("server.builds.install"));
        install.getStyleClass().add("pack-install");
        install.setMaxWidth(Double.MAX_VALUE);
        install.setOnAction(e -> installServerBuild(summary));

        body.getChildren().addAll(chips, name);
        if (description != null) {
            body.getChildren().add(description);
        }
        body.getChildren().addAll(meta, install);
        card.getChildren().addAll(cover, body);
        return card;
    }

    /** Чип загрузчика модов (цвет по типу, как на карточках инстансов). */
    private Label loaderChip(BuildSummary summary) {
        Label chip = new Label(summary.loaderType().displayName());
        chip.getStyleClass().add("loader-badge-"
                + summary.loaderType().name().toLowerCase(
                        java.util.Locale.ROOT));
        return chip;
    }

    /**
     * Чип версии Minecraft. Для сборок, у которых источник не сообщил
     * версию игры, чип не рисуется — надписей «unknown» в каталоге нет.
     */
    private Label mcChip(BuildSummary summary) {
        if ("unknown".equals(summary.minecraftVersion())) {
            return null;
        }
        Label chip = new Label("MC " + summary.minecraftVersion());
        chip.getStyleClass().add("type-badge-snapshot");
        return chip;
    }

    /** Кэш обложек сборок: одна загрузка icon.png на ссылку. */
    private static final java.util.concurrent.ConcurrentHashMap<
            String, Image> COVER_ICONS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Обложка сборки. Если источник отдал ссылку на icon.png
     * ({@code BuildSummary#iconUrl}) — рисуем её поверх градиента,
     * по центру-кропу; ссылка читается в фоне. Пока картинка грузится
     * или если её нет/не удалось скачать — на градиенте (оттенок
     * выводится из id сборки, у каждой свой) написано название сборки.
     */
    private StackPane buildBuildCover(BuildSummary summary, boolean large) {
        int hash = Math.abs(summary.id().hashCode());
        // Гамма 130..219 — изумрудно-бирюзово-синий фирменный диапазон
        double hue = 130 + (hash % 90);
        double sat = 0.32 + (hash % 4) * 0.07;
        javafx.scene.paint.Color top =
                javafx.scene.paint.Color.hsb(hue, sat, 0.32);
        javafx.scene.paint.Color bottom =
                javafx.scene.paint.Color.hsb(hue, sat + 0.08, 0.11);
        String gradient = String.format(java.util.Locale.ROOT,
                "-fx-background-color: linear-gradient(to bottom right, "
                        + "#%02X%02X%02X 0%%, #%02X%02X%02X 100%%);",
                (int) (top.getRed() * 255),
                (int) (top.getGreen() * 255),
                (int) (top.getBlue() * 255),
                (int) (bottom.getRed() * 255),
                (int) (bottom.getGreen() * 255),
                (int) (bottom.getBlue() * 255));

        double height = large ? 170 : 136;
        double width = (large ? 520 : 288) - 2; // минус граница карточки

        StackPane cover = new StackPane();
        cover.getStyleClass().add("pack-cover");
        cover.setPrefHeight(height);
        cover.setMinHeight(height);
        cover.setMaxHeight(height);
        cover.setStyle(gradient);
        clipTopRounded(cover, 13);

        // Название на обложке: по центру; размер шрифта подбирается
        // по фактической ширине текста — название всегда заполняет
        // обложку крупными буквами (до разумного максимума), для
        // очень длинных имён шрифт плавно ужимается и текст
        // переносится на вторую строку
        Label nameOnCover = new Label(summary.displayName());
        nameOnCover.getStyleClass().add("pack-cover-name");
        nameOnCover.setAlignment(Pos.CENTER);
        nameOnCover.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameOnCover.setWrapText(true);
        nameOnCover.maxWidthProperty().bind(
                cover.widthProperty().subtract(20));
        StackPane.setAlignment(nameOnCover, Pos.CENTER);
        double maxFont = large ? 44 : 32;
        Runnable fitCoverFont = () -> {
            double available = cover.getWidth() - 20;
            if (available <= 0) {
                return;
            }
            String text = summary.displayName();
            javafx.scene.text.Font probe = javafx.scene.text.Font.font(
                    nameOnCover.getFont().getFamily(),
                    javafx.scene.text.FontWeight.EXTRA_BOLD, maxFont);
            double measured = measureTextWidth(text, probe);
            double size = measured <= available
                    ? maxFont
                    : Math.max(13, maxFont * available / measured);
            nameOnCover.setFont(javafx.scene.text.Font.font(
                    probe.getFamily(),
                    javafx.scene.text.FontWeight.EXTRA_BOLD, size));
        };
        cover.widthProperty().addListener((obs, was, now) -> fitCoverFont.run());
        fitCoverFont.run();

        Label versionBadge = new Label(
                "v" + shortVersion(summary.version()));
        versionBadge.getStyleClass().add("pack-version-badge");
        StackPane.setAlignment(versionBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(versionBadge, new Insets(8));

        cover.getChildren().addAll(nameOnCover, versionBadge);

        // Реальная обложка из источника: грузится в фоне поверх названия
        String iconUrl = summary.iconUrl();
        if (iconUrl != null && !iconUrl.isBlank()) {
            Image image = COVER_ICONS.computeIfAbsent(iconUrl,
                    url -> new Image(url, 288, height, false, true, true));
            if (!image.isError()) {
                ImageView view = new ImageView(image);
                view.setFitWidth(width);
                view.setFitHeight(height);
                view.setPreserveRatio(false);
                view.setSmooth(true);
                Runnable apply = () -> {
                    if (image.isError()) {
                        view.setVisible(false);
                        return;
                    }
                    // Кроп по центру под пропорции обложки
                    double iw = image.getWidth();
                    double ih = image.getHeight();
                    if (iw <= 0 || ih <= 0) {
                        return;
                    }
                    double targetRatio = width / height;
                    double srcRatio = iw / ih;
                    double vw = srcRatio > targetRatio
                            ? ih * targetRatio : iw;
                    double vh = srcRatio > targetRatio
                            ? ih : iw / targetRatio;
                    view.setViewport(new javafx.geometry.Rectangle2D(
                            (iw - vw) / 2, (ih - vh) / 2, vw, vh));
                    nameOnCover.setVisible(false);
                };
                image.progressProperty().addListener((obs, was, progress) -> {
                    if (progress.doubleValue() >= 1.0) {
                        apply.run();
                    }
                });
                image.errorProperty().addListener((obs, was, failed) -> {
                    if (failed) {
                        apply.run();
                    }
                });
                apply.run();
                cover.getChildren().add(view);
            }
        }
        return cover;
    }

    /** Ширина строки в заданном шрифте (для подбора размера текста). */
    private static double measureTextWidth(String text,
            javafx.scene.text.Font font) {
        javafx.scene.text.Text measure = new javafx.scene.text.Text(text);
        measure.setFont(font);
        return measure.getLayoutBounds().getWidth();
    }

    /**
     * Скругляет верхние углы контейнера обложки (клип по фактическим
     * размерам), чтобы картинка не выходила за скругление карточки.
     */
    private static void clipTopRounded(StackPane cover, double radius) {        javafx.scene.shape.SVGPath clip = new javafx.scene.shape.SVGPath();
        Runnable reshape = () -> {
            double width = cover.getWidth();
            double height = cover.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            clip.setContent("M0 " + radius + " Q0 0 " + radius + " 0 H"
                    + (width - radius) + " Q" + width + " 0 " + width
                    + " " + radius + " V" + height + " H0 Z");
        };
        cover.widthProperty().addListener((obs, was, now) -> reshape.run());
        cover.heightProperty().addListener((obs, was, now) -> reshape.run());
        cover.setClip(clip);
    }

    /**
     * Короткая форма версии для компактных мест: у timestamp-версий
     * вроде 2026.10.04.17.43.40 остаются первые три сегмента; обычные
     * версии (1.0.0) показываются как есть. Без выдумывания данных —
     * только аккуратное усечение с многоточием.
     */
    private static String shortVersion(String version) {
        if (version == null) {
            return "";
        }
        String trimmed = version.trim();
        String[] segments = trimmed.split("\\.");
        if (segments.length > 3) {
            return String.join(".",
                    segments[0], segments[1], segments[2]) + "\u2026";
        }
        return trimmed;
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
        String buildId = summary.id();
        MinecraftVersion mcVersion = null;
        for (MinecraftVersion candidate : manifestVersions) {
            if (candidate.id().equals(summary.minecraftVersion())) {
                mcVersion = candidate;
                break;
            }
        }
        if (mcVersion == null) {
            if (manifestVersions.isEmpty()) {
                // Классическая «тихая» блокировка установки: без
                // манифеста Mojang кнопку считать неотзывчивой —
                // повторная загрузка версий запускается сразу
                statusLabel.setText(Lang.tr("versions.retry"));
                LOG.warning("Server build install blocked: no version "
                        + "manifest (" + buildId + "); reloading versions");
                loadVersions();
                return;
            }
            // Сборка без build.json не знает свою версию игры —
            // спрашиваем пользователя, установка от этого не ломается
            Stage chooserOwner = (Stage) root.getScene().getWindow();
            mcVersion = ChooseMcVersionDialog.show(chooserOwner, summary,
                    manifestVersions);
            if (mcVersion == null) {
                LOG.info(() -> "Server build install cancelled: no MC "
                        + "version chosen (" + buildId + ")");
                return;
            }
            final String chosenId = mcVersion.id();
            LOG.info(() -> "MC version chosen for build " + buildId
                    + ": " + chosenId);
            // Показываем пользователю выбор вместо «unknown»
            summary = new BuildSummary(summary.id(), summary.version(),
                    summary.displayName(), summary.description(),
                    summary.loaderType(), mcVersion.id(),
                    summary.loaderVersion(), summary.iconUrl());
        }
        // Показываем пользователю случайный выбор вместо «unknown»:
        // effectiveSummary — effectively-final версия для Task ниже
        BuildSummary effectiveSummary = summary;
        LOG.info(() -> "Server build install requested: "
                + effectiveSummary.id()
                + " (" + effectiveSummary.minecraftVersion() + ")");

        statusLabel.setText(Lang.tr("server.builds.installing",
                effectiveSummary.summaryLine()));
        Stage owner = (Stage) root.getScene().getWindow();
        InstallProgressDialog progressDialog = new InstallProgressDialog(owner);
        progressDialog.setTitle(Lang.tr("server.builds.install.title"));
        progressDialog.show();

        MinecraftVersion finalMcVersion = mcVersion;
        Task<ServerBuildInstaller.Outcome> task = new Task<>() {
            @Override
            protected ServerBuildInstaller.Outcome call() throws Exception {
                return serverBuildInstaller.provision(serverSession,
                        effectiveSummary,
                        effectiveSummary.displayName(), finalMcVersion,
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
            LOG.log(Level.WARNING, "Server build install failed: "
                    + effectiveSummary.id(), cause);
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
        // Файл версий: полный манифест с URL доступен интерфейсу на
        // всех следующих запусках, даже когда сеть недоступна
        if (VersionManifestCache.save(versions)) {
            LOG.info(() -> "Version manifest saved: " + versions.size()
                    + " versions -> " + VersionManifestCache.FILE_NAME);
        }
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
        // Файл версий лаунчера (launcher/versions.json): полноценный
        // манифест с URL метаданных — главный источник офлайн-версий.
        List<MinecraftVersion> cached = new ArrayList<>(
                VersionManifestCache.load());
        if (cached.isEmpty()) {
            cached.addAll(loadInstalledDiscoveryVersions());
        }
        return cached;
    }

    /**
     * Царский одиночный источник первого запуска без интернета: версии,
     * metadata которых уже скачана парой {@code versions/<id>/<id>.json}.
     * Без URL — годятся только как известные id; полноценный путь
     * обеспечивает кэш-файл {@link VersionManifestCache}.
     */
    private List<MinecraftVersion> loadInstalledDiscoveryVersions() {
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
                    saved.language(), saved.theme(),
                    saved.customThemeBg(), saved.customThemeAccent()));
        } catch (IOException e) {
            // Некритично
        }
    }

    private final AtomicLong avatarRequestId = new AtomicLong();

    private void updateAvatar(GameProfile profile) {
        long requestId = avatarRequestId.incrementAndGet();
        if (profile == null) {
            avatarView.setVisible(false);
            avatarPlaceholder.setVisible(false);
            offlineAccountBadge.setVisible(false);
            offlineAccountBadge.setManaged(false);
            return;
        }
        // Значок офлайн-аккаунта (🏠): игра без сервера и авторизации
        boolean offline = !profile.isElyBy();
        offlineAccountBadge.setVisible(offline);
        offlineAccountBadge.setManaged(offline);
        if (profile.skinUrl().isEmpty()) {
            avatarView.setVisible(false);
            avatarPlaceholder.setVisible(true);
            return;
        }
        avatarPlaceholder.setVisible(false);
        skinService.loadAvatarAsync(profile, 64)
                .thenAccept(optImg -> Platform.runLater(() -> {
                    if (requestId != avatarRequestId.get()) {
                        return;
                    }
                    if (optImg.isPresent()) {
                        avatarView.setImage(optImg.get());
                        avatarView.setVisible(true);
                        avatarPlaceholder.setVisible(false);
                    } else if (avatarView.getImage() == null) {
                        avatarView.setVisible(false);
                        avatarPlaceholder.setVisible(true);
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
        lastFailedProfileId = null;
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
                                LOG.warning(() -> "Game crashed: "
                                        + profile.versionId() + " ("
                                        + report.category() + ", exit "
                                        + report.exitCode() + ")");
                                Platform.runLater(() -> {
                                    lastFailedProfileId = profile.id();
                                    refreshLaunchButtons();
                                    showServerCrashBar(profile, report);
                                    CrashWindow.show(owner, report);
                                });
                            }

                            @Override
                            public void onFailed(String error) {
                                LOG.warning(() -> "Launch failed: "
                                        + profile.versionId() + " — " + error);
                                Platform.runLater(() -> {
                                    lastFailedProfileId = profile.id();
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
            lastFailedProfileId = profile.id();
            refreshLaunchButtons();
            Throwable cause = launchTask.getException();
            LOG.log(Level.WARNING, "Launch task failed: "
                    + profile.versionId(), cause);
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
            case RUNNING -> {
                // Игра успешно стартовала — ошибка прошлой попытки снята
                lastFailedProfileId = null;
                statusLabel.setText(Lang.tr("launch.running",
                        profile.versionId(), profile.name()));
            }
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
