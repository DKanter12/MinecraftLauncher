# Архитектурный аудит Minecraft Launcher

Дата: 2026-10-09. Baseline: JDK 17.0.12, Gradle 8.5, `gradlew test` — BUILD SUCCESSFUL.
Ветка работ: `code-cleanup` (10 коммитов поверх `main`).

Названия пакетов НЕ считаются доказательством архитектуры ниже —
проверены фактические вызовы (`grep` по `src/main` и `src/test`).

Легенда вызовов: `main:N` — N вызовов из `src/main` вне собственного файла;
`test` — используется только тестами; `dead` — нет вызовов вообще.

## 1. Карта по пакетам

### `app` — точка входа

| Класс | Ответственность | Вызывает | Вызывают | Данные | IO/сеть | FX | Тесты |
|---|---|---|---|---|---|---|---|
| `LauncherApplication` | JavaFX-старт, сборка графа сервисов (~90 строк `new`), сцена 1180x680, `loadVersions()` | все сервисы напрямую | никто (точка входа) | нет | нет напрямую | да | нет |

Проблема A1 (архитектурная): единственный Composition Root смешан с UI-стартом,
16+ `new` без фабрик. План: `LauncherCompositionRoot.createContext()`.

### `core`

| Класс | Ответственность | Вызывает | Вызывают | IO | Тесты |
|---|---|---|---|---|---|
| `LauncherContext` | Держатель 16 сервисов, геттеры | никого (DTO-контейнер) | `LauncherApplication`, `MainView` | нет | нет |

Чистый. Замечание: хранит конкретные классы вместо интерфейсов
(`FileSystemBuildRepository`, `DefaultJavaResolutionService` через других) —
см. проблему A2.

### `domain` — исключения + `model` + `port`

Исключения (`LauncherException`+`ErrorCode`, 7 наследников): никем не бросаются
в проде, только типизируют будущие ошибки. Тестов нет. Статус: каркас без
покрытия (потенциальная проблема P1 — либо начать использовать, либо удалить).

`domain/model` (17 классов, все чистые данные, без IO/FX/HTTP):
`AssetIndex`, `AssetIndexContent`, `AssetObject`, `DownloadInfo`, `GameProfile`,
`JavaResolutionResult`, `JavaRuntime`, `JavaVersion`, `LaunchArguments`,
`LaunchResult`, `Library`, `MinecraftProcess`, `MinecraftVersion`,
`ModdedProfile`, `ModLoaderType`, `ModLoaderVersion`, `VersionManifest`(legacy!),
`VersionMetadata`, `LauncherVersion`.
Замечание: `model/VersionManifest` + `model/MinecraftVersion` — старые дубли,
миграция запланирована отдельным этапом (проблема C1).

`domain/port`: `MinecraftVersionRepository` (реализации: Mojang + Cached),
`RetryPolicy` (реализация: `FixedRetryPolicy`). Чисто.

### `application/build` — создание сборок

| Класс | Ответственность | IO/сеть |
|---|---|---|
| `Build` | Сущность: имя, MC-версия, ядро, версия ядра, валидация | нет |
| `BuildRequest` | Конверт выполнения + проверка согласованности | нет |
| `CreateBuildUseCase` | `isBuildAvailable` → `createBuild` (версия → каталог → папки) | через менеджер |
| `MinecraftVersionManager` | `isVersionDownloaded` / `downloadVersion` | сеть/файлы |
| `ModScanner` | Подсчёт jar-модов | файлы (только чтение) |

### `application/version` — каталог и установка версий

`MinecraftVersions` (`getMinecraftVanillaVersions`/`getMinecraftModdedVersions`),
`MinecraftVersionSorter`, `MinecraftVersionChecker` (`isInstalled`/`isValid`),
`MinecraftVersionDownloader` (`download`), `MinecraftVersionStorage` (пути),
`MinecraftLoaderInstaller` (`installFabric/Quilt/NeoForge/Forge`),
`ManifestEntries` (мост домен→старая модель — кандидат на удаление после
дедупликации моделей, проблема C2).

### `application/launch` — запуск

`LaunchManager` (стадии verify/repair/java/launch + чистые `decide/needsJava8`),
`LaunchDecision`, `CrashAnalyzer`/`CrashReport`/`CrashCategory`,
`LaunchCommandBuilder` (делегируетargen-билдеру), `LaunchSettings`,
`GameLaunchCommand`, `GameProcess`/`GameProcessManager`/`GameProcessMonitor`/`GameExitHandler`,
`JavaRuntimeManager`, `ElyByAuthenticator`, `GameAuthenticationProvider`,
`AuthData`, `LaunchArgumentBuilder` (интерфейс), `LaunchService` (интерфейс),
`LaunchBuildUseCase` (полный запуск телом + `verifyFiles`).

Проблема C3 (подтверждённая): `LaunchManager.launch` и `LaunchBuildUseCase.launch`
перекрываются; `LaunchCommandBuilder`/`GameLaunchCommandBuilder`/`LaunchArgumentBuilder`
— три уровня построения команды. Требуется решение §7.2 (один публичный путь).

Проблема C4: `JavaRuntimeManager.isCompatible` нигде не вызывается в проде
(только тесты) и дублирует правило из `DefaultJavaResolutionService`.

### `application/account`, `application/java`

`AccountManager` (валидация, дедуп, выбор) — чистый, тестирован.
`JavaManager` (`resolve`/`ensureInstalled`/`adoptManaged`) — тонкий, тестирован.

### `infrastructure/download` — скачивание и установка

`MinecraftInstaller` (оркестратор задач), `HttpFileDownloader` (`.part` +
атомарный move), `Sha1ChecksumVerifier`, `FileIntegrityChecker`
(`exists/isValid/checksumMatches/verifyFiles`), `DownloadTask/DownloadResult`,
`InstallationService/Progress/Result`, `FixedRetryPolicy`.
Таймауты: коннект 15с, запрос 5мин. Ретраи: 3, только IO/SHA-mismatch
(валидационные ошибки не повторяются — корректно).

### `infrastructure/filesystem`

`GameDirectory` (все пути, без дублирования — `LibraryPaths.resolve`
использует его, не конкурирует), `ProfileService` (JSON аккаунтов),
`FileSystemBuildRepository` (~600 строк: CRUD + JSON + sanitize + память —
кандидат на разделение, проблема C5), `ModdedProfileServiceTest` покрывает.

### `infrastructure/mojang`, `infrastructure/loaders`

`MojangVersionService/MetadataService/AssetIndexService` + интерфейсы.
`ModdedVersionService`, `ModdedProfileVerificationService` (~500 строк, 7
зависимостей — кандидат на разделение, проблема C6),
4 провайдера + 2 установщика + `ModLoaderRegistry` + `ModLoaderMetadataMerger`.
`StubJavaRuntimeInstaller` уже удалён ранее.

### `infrastructure/java`, `elyby`, `skins`, `minecraft`

`SystemJavaDetector` (JAVA_HOME/PATH/реестр), `AdoptiumJavaRuntimeInstaller`,
`DefaultJavaResolutionService` (точное совпадение для ≤8 — осознанно),
`ElyAuthService` (пароль только в параметрах вызовов, в логах нет),
`SkinService` (кэш `assets/skins`, самовосстановление),
`AuthlibInjectorManager`, `NativeExtractor` (пропускает `META-INF`,
zip-slip невозможен — распаковка без пользовательских путей).

### `infrastructure/server` — серверные сборки

`ServerAuthService` (хранит только токен), `ServerSession`, `GitHubBuildApi`,
`YandexDiskBuildApi`, `OfflineLauncherServerApi`, `DistributionSources`.
**Мертвы в проде** (`main:0`, только тесты): `RemoteBuildService`,
`ModpackProvisioner`, `LoaderMismatchDetector`, `LoaderFallbackPolicy`,
`AdminLauncherServerApi` (проблема C7 — либо wire в UI-поток серверных сборок,
либо удалить; тесты их покрывают, удаление тестов запрещено без замены).

Потенциальная проблема P2: `GitHubBuildApi`/`YandexDiskBuildApi` кладут
введённый пароль как токен сессии → пароль в открытом виде в
`server_session.json`. Для Yandex это OAuth-токен (норма), для GitHub —
персональный токен (норма только если пользователь ввёл именно токен).

### `infrastructure/updater`

`UpdateService` (фасад: check/download/stage), `LauncherUpdateManager`,
`LauncherVersionProvider/Checker`, `ReleaseNotesProvider`,
`LauncherUpdateDownloader` → `UpdateDownloadResult`,
`DownloadVerifier` + `UpdateVerifier` (**дубль SHA-проверки**, проблема C8),
`LauncherUpdateInstaller`, `LauncherUpdateProcess`, `LauncherRestartManager`,
`LauncherUpdateRollback`, `UpdateApplier` (bat-скрипт), `AppVersion`,
`LauncherUpdate`. `clearStaged()` уже удалён ранее.

### `infrastructure/{common,http}`, `i18n`, `version`

`JsonStrings` (бывший `Json`), `JvmArgs`, `LibraryPaths`, `OsDetector`,
`HttpDefaults`, `UrlFetcher` (общий HTTP: редиректы, таймауты, `.part`).
`Lang` (RU/EN + дефолт). `version/`: `VersionTypeRegistry` + 4 типа
(`StandardVersionType`, `VersionType`, `ModLoaderFamilyType`,
`ModdedVersionType`) — дублируют `domain/model/VersionType` (проблема C1).

### `presentation` — JavaFX (интерфейс НЕ меняется в этом рефакторинге)

`MainView` (~2500 строк: топбар, 3 вида, карточки, аккаунты, настройки,
сервер, версии, запуск через `BuildLaunchManager`, все диалоги) —
главный кандидат на разделение (проблема C9). Остальное: 8 диалогов
(каждый свой файл — уже разделены), `FxTasks` (фон, ок), `UiErrors`,
`ErrorDialog`, `CrashWindow`, `LauncherUpdateWindow`.

Потоки: все долгие операции идут через `FxTasks`/Task, UI-правила через
`Platform.runLater` внутри диалогов прогресса. Ручная проверка: grep
`new Thread(` вне `FxTasks` — только `UpdateDialog.onDownload` (п. 6.4 ниже).

## 2. Список проблем

### Подтверждённые

| # | Файл:метод | Что не так | Почему плохо | Решение | Затронет | Тесты |
|---|---|---|---|---|---|---|
| C1 | `model/` + `version/` + `domain/model` | Два `MinecraftVersion`, два `VersionType` (+3 display-типа) | Одинаковые имена, разное поведение — источник ошибок | Этап 2 (выполнен в ветке): канонический домен, удалить дубли | ~25 файлов | Обновить 8 тест-файлов |
| C2 | `application/version/ManifestEntries.java` | Мост домен→старая модель | После C1 не нужен | Удалить вместе с тестом после C1 | `Checker/Downloader/LoaderInstaller` | Удалить `ManifestEntriesTest`, остальные обновить |
| C3 | `application/launch/*` | 3 построителя команды + 2 полных запуска | Неясно, какой путь основной | §7.2: один публичный путь, остальные — внутренние/удалить | `MainView`, тесты запуска | Сохранить покрытие verify/launch |
| C4 | `JavaRuntimeManager.isCompatible` | Не вызывается в проде, правило продублировано в резолвере | Мёртвый код с тестами | Вызвать из `GameLaunchCommandBuilder` как защиту или удалить + перенести тесты | `GameLaunchCommandBuilder` | Перенести 5 ассертов |
| C5 | `FileSystemBuildRepository` (~600 строк) | CRUD + JSON + sanitize + память + JVM-арги | Много причин для изменений | Выделить имена/память при касании, не сейчас | `MainView`, тесты профилей | Существующие |
| C6 | `ModdedProfileVerificationService` (~500 строк, 7 deps) | Проверка + починка + Java + отчёты | Сложно тестировать целиком | Делить только по подтверждённым швам (файлы vs метаданные) | Тесты верификации | Существующие |
| C7 | `RemoteBuildService`, `ModpackProvisioner`, `LoaderMismatchDetector`, `LoaderFallbackPolicy`, `AdminLauncherServerApi` | 0 вызовов из `src/main` | Мёртвый прод-код | ✅ РЕШЕНО (коммит `1b648fc`): удалены `RemoteBuildService`, `ModpackProvisioner`, `LoaderFallbackPolicy`, `AdminLauncherServerApi` (+ `CrashAnalyzer.suggestOlderLoader`, тесты-драйверы переписаны на прямые вызовы API). `LoaderMismatchDetector` оставлен — вызывается из `CrashAnalyzer`. `OfflineLauncherServerApi` переведён прямо на `LauncherServerApi` | Тесты distribution | Заменить/удалить синхронно |
| C8 | `DownloadVerifier` vs `UpdateVerifier` | Два SHA-компонента обновлений | Неясно, какой использовать | Один компонент (§12), тесты слить | `UpdateService`, 2 тест-файла | Слить, не удалять кейсы |
| C9 | `MainView` (~2500 строк) | Вся логика интерфейса в одном файле | Любое изменение risky | Поэтапное выделение видов (§10), без смены визуала | Все диалоги | Ручная проверка сценариев |

### Потенциальные

| # | Где | Риск | Решение |
|---|---|---|---|
| P1 | `domain/*Exception` + `ErrorCode` | Каркас без бросаний и тестов | Начать использовать в новых ветках (build already-exists) или удалить |
| P2 | `GitHubBuildApi:84-86`, `YandexDiskBuildApi:148` | Пароль сохраняется как токен сессии в открытом виде | Уточнить требование; минимум — документировать, что ожидается токен, не пароль |
| P3 | `ElyAuthService.refreshProfile` глотает все исключения | Протухшая сессия выглядит как валидная | Возвращать результат с признаком `refreshed`, а не молча кэш |
| P4 | `UpdateServiceTest` использует `file://` URL | Может скрыть регрессию HTTP-заголовков/статусов | Добавить тест HTTP-статуса через локальный `HttpServer` |
| P5 | `InstallProgressDialog`/`UpdateDialog.onDownload` — `new Thread` напрямую | Обход `FxTasks`, нет отмены | Перевести на `FxTasks`, добавить отмену где безопасно |

### Архитектурные

| # | Где | Суть |
|---|---|---|
| A1 | `LauncherApplication` (~90 строк `new`) | Нужен `LauncherCompositionRoot.createContext()` |
| A2 | `LauncherContext` хранит конкретные классы | Где есть контракты (`VersionService`, `InstallationService`), хранить их |
| A3 | `domain/model` vs будущие DTO | Пока DTO совпадают с моделями — не дублировать (YAGNI) |
| A4 | `LauncherPreferences` как god-объект настроек | Разделить модель настроек и репозиторий, если настроек станет больше (пока 7 ключей — рано) |

## 3. Что уже сделано до этого документа (ветка `code-cleanup`)

Дедуп SHA (`Sha1ChecksumVerifier` один), `BuildCreator→CreateBuildUseCase`,
`MinecraftLauncher→LaunchBuildUseCase`, `ModdedProfileService→FileSystemBuildRepository`,
`LauncherPreferences→FileSettingsRepository`, группировка сборок по версиям,
`Build/AccountManager`, `CrashAnalyzer`, `JavaManager`, `ModScanner`,
`LaunchManager.decide`, `UpdateVerifier`, `FxTasks/UiErrors`, удаление
`StubJavaRuntimeInstaller`, `clearStaged()`, `sameBuild`, JVM-полей из диалогов,
`Json→JsonStrings`, восстановление затёртых URL/UA/bat-имени.

## 4. Sweep неиспользуемого 2026-10-09

Проверен каждый класс src/main на вызовы вне собственного файла (точный подсчёт, не эвристика).

Удалено как мёртвое (0 вызовов в проде, дубли живого кода):
- infrastructure/server/BuildVersions + тест (осиротел после удаления RemoteBuildService; сравнение версий покрывает AppVersion);
- ersion/ целиком (VersionTypeRegistry — чипы диалога идут напрямую по domain VersionType);
- pplication/version/ целиком (6 классов) — дублировал рабочий слой (MojangVersionService, MinecraftVersionManager);
- MojangMinecraftVersionRepository, CachedMinecraftVersionRepository, domain/port/MinecraftVersionRepository — тот же дубль;
- ReleaseNotesProvider — однострочная обёртка без вызовов;
- тесты всех удалённых классов (кроме пристроенных кейсов, перенесённых в живые тесты).

Встроено вместо удаления (функциональная дыра, а не мёртвый код):
- NativeExtractor.extractNatives никто не вызывал — нативы никогда не распаковывались, хотя -Djava.library.path на них ссылается. Вызов добавлен в BuildLaunchManager на стадии STARTING, ошибка ведёт в onFailed.

Оставлено с обоснованием: LoaderMismatchDetector (зовёт CrashAnalyzer), исключения (JavaException бросается), FakeLauncherServerApi (нужен живому ServerAuthServiceTest).
