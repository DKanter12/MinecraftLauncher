package org.example.launcher.infrastructure.filesystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.launcher.infrastructure.common.JsonStrings;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.ModLoaderType;

/**
 * Управляет игровыми инстансами (ванильными и модовыми): создание, хранение,
 * удаление и раскладка игровых каталогов каждого инстанса.
 * <p>
 * Инстансы хранятся в {@code instances.json} в корне хранилища
 * (устаревший {@code modded_profiles.json} читается как запасной вариант). Каждый
 * инстанс владеет игровым каталогом в {@code profiles/<группа-версии>/<id>/} —
 * например {@code profiles/fabric-1.21.1/Моя сборка/} — со
 * стандартной раскладкой сборки ({@code mods}, {@code config},
 * {@code resourcepacks}, {@code shaderpacks}, {@code saves},
 * {@code logs}), создаваемой автоматически. Все сборки на одну версию
 * (загрузчик + Minecraft) лежат в одной группе-папке. Разные инстансы никогда
 * не делят эти каталоги и потому не конфликтуют; общие
 * ресурсы (клиентский JAR, библиотеки, ассеты) лежат в корне хранилища
 * и переиспользуются.
 * <p>
 * Старые плоские записи {@code profiles/<id>} (без группы) продолжают
 * читаться и работают как раньше; новые создаются уже с группировкой.
 */
public class FileSystemBuildRepository {

    /**
     * Стандартные каталоги в каждом игровом каталоге инстанса.
     * Пользователи могут вручную складывать в них моды, ресурс-паки,
     * шейдер-паки и миры.
     */
    public static final List<String> STANDARD_FOLDERS = List.of(
            "mods", "config", "resourcepacks", "shaderpacks", "saves", "logs");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Path profilesFile;
    private final Path legacyFile;
    private final GameDirectory storage;
    private final Gson gson;

    public FileSystemBuildRepository(GameDirectory storage) {
        this.storage = storage;
        this.profilesFile = storage.instancesFile();
        this.legacyFile = storage.legacyModdedProfilesFile();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ------------------------------------------------------------------
    //  Хранение
    // ------------------------------------------------------------------

    /**
     * Загружает все инстансы; пустой список, если их пока нет. Устаревший
     * {@code modded_profiles.json} читается, когда {@code instances.json}
     * ещё не существует.
     */
    public List<ModdedProfile> loadProfiles() throws IOException {
        Path file = profilesFile;
        if (!Files.isRegularFile(file) && Files.isRegularFile(legacyFile)) {
            file = legacyFile;
        }
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        JsonElement rootElem;
        try {
            rootElem = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8));
        } catch (JsonSyntaxException e) {
            throw new IOException("Instances file is corrupt: "
                    + file, e);
        }
        if (!rootElem.isJsonObject()) {
            throw new IOException("Modded profiles file has an invalid shape: "
                    + profilesFile);
        }

        List<ModdedProfile> result = new ArrayList<>();
        JsonObject root = rootElem.getAsJsonObject();
        if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            for (JsonElement elem : root.getAsJsonArray("profiles")) {
                if (!elem.isJsonObject()) continue;
                ModdedProfile profile = fromJson(elem.getAsJsonObject());
                if (profile != null) {
                    result.add(profile);
                }
            }
        }
        return result;
    }

    /**
     * Сохраняет полный список профилей.
     */
    public void saveProfiles(List<ModdedProfile> profiles) throws IOException {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (ModdedProfile profile : profiles) {
            arr.add(toJson(profile));
        }
        root.add("profiles", arr);
        Files.createDirectories(profilesFile.getParent());
        Files.writeString(profilesFile, gson.toJson(root), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    //  Жизненный цикл профиля
    // ------------------------------------------------------------------

    /**
     * Создаёт новый игровой инстанс с собственным игровым каталогом.
     * <p>
     * Инстанс может быть ванильным ({@code loaderType == VANILLA},
     * {@code loaderVersion} пуст) или установкой мод-загрузчика.
     * Отображаемое имя по умолчанию состоит из загрузчика и версии Minecraft,
     * но выбирается свободно и позже переименовывается; папка сборки создаётся
     * внутри группы версии {@code profiles/<загрузчик>-<mc>/} (например
     * {@code profiles/fabric-1.21.1/Моя сборка/}), имя каталога
     * выводится из отображаемого имени (очищенное) и делается уникальным
     * в пределах группы добавлением числового суффикса при необходимости.
     * Стандартная раскладка папок создаётся сразу, чтобы пользователь мог
     * тут же складывать моды в {@code mods/}.
     *
     * @param loaderType      тип инстанса (VANILLA или загрузчик)
     * @param loaderVersion   версия загрузчика (пустая для ваниллы)
     * @param minecraftVersion целевая версия Minecraft
     * @param versionId       id установленной версии, которую запускает инстанс
     * @param extraJvmArgs    дополнительные JVM-параметры запуска
     *                        (могут быть пустыми)
     * @param displayName     человекочитаемое имя инстанса; пустое означает
     *                        автоматическое имя «Loader MC»
     * @param memoryMb        лимит выделенной памяти в мегабайтах;
     *                        {@code <= 0} означает авто (без лимита)
     * @return созданный и сохранённый инстанс
     * @throws IOException если инстанс нельзя сохранить или нельзя создать
     *                     игровой каталог
     */
    public ModdedProfile createProfile(ModLoaderType loaderType,
                                       String loaderVersion,
                                       String minecraftVersion,
                                       String versionId,
                                       List<String> extraJvmArgs,
                                       String displayName,
                                       int memoryMb) throws IOException {
        boolean vanilla = loaderType == ModLoaderType.VANILLA;
        String name = (displayName == null || displayName.isBlank())
                ? defaultDisplayName(loaderType, minecraftVersion)
                : displayName.trim();

        List<ModdedProfile> existing = loadProfiles();
        // Папка сборки лежит в группе версии: profiles/<loader>-<mc>/<имя>/.
        // Имя папки следует за именем в лаунчере; уникальность — в пределах
        // группы, поэтому одинаковые имена на разных версиях не конфликтуют.
        String group = versionGroup(loaderType, minecraftVersion);
        String dirBase = sanitize(name);
        if (dirBase.isBlank()) {
            dirBase = defaultDisplayName(loaderType, minecraftVersion);
        }
        String leaf = uniqueLeafName(group, dirBase, existing, null);
        String fullId = group + "/" + leaf;
        String now = OffsetDateTime.now().format(TIME_FORMAT);

        List<String> components = new ArrayList<>();
        components.add("minecraft:" + minecraftVersion);
        if (!vanilla) {
            components.add(loaderType.name().toLowerCase(Locale.ROOT)
                    + ":" + loaderVersion);
        }

        ModdedProfile profile = new ModdedProfile(
                fullId,
                name,
                loaderType,
                vanilla ? "" : loaderVersion,
                minecraftVersion,
                versionId,
                "profiles/" + fullId,
                List.copyOf(components),
                extraJvmArgs == null ? List.of() : List.copyOf(extraJvmArgs),
                normalizeMemory(memoryMb),
                now,
                null);

        // Создать игровой каталог профиля со стандартной раскладкой
        ensureProfileFolders(storage.moddedProfileDir(fullId));

        existing.add(profile);
        saveProfiles(existing);
        return profile;
    }

    /**
     * Удаляет профиль из реестра.
     * <p>
     * Игровой каталог профиля (с его модами и сохранениями) НЕ
     * удаляется — убирается только запись реестра, поэтому пользовательские
     * данные никогда не уничтожаются. Путь к каталогу возвращается, чтобы
     * вызывающий мог сообщить пользователю, где остались его файлы.
     *
     * @return осиротевший игровой каталог, если профиль существовал
     */
    public Optional<Path> deleteProfile(String id) throws IOException {
        List<ModdedProfile> profiles = loadProfiles();
        Optional<ModdedProfile> removed = profiles.stream()
                .filter(p -> p.id().equals(id))
                .findFirst();
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        profiles.removeIf(p -> p.id().equals(id));
        saveProfiles(profiles);
        return Optional.of(resolveGameDir(removed.get()));
    }

    /**
     * Обновляет отображаемое имя профиля, доп. JVM-аргументы запуска
     * и лимит памяти.
     * <p>
     * Переименование также переименовывает папку сборки внутри её группы
     * версии (и id профиля, который следует за папкой), поэтому папка всегда
     * совпадает с именем в лаунчере; моды, сохранения и сборки переезжают
     * нетронутыми. Версия/группа при переименовании не меняются — сборка
     * не переезжает в чужую версию. Пустое имя сохраняет текущее. Когда
     * целевую папку переименовать нельзя (например, игра запущена из неё),
     * ничего не сохраняется и выбрасывается ошибка. Старые плоские записи
     * без группы переименовываются на месте как раньше.
     *
     * @param id            обновляемый профиль
     * @param extraJvmArgs  новые JVM-параметры запуска (могут быть пустыми)
     * @param displayName   новое отображаемое имя (пустое сохраняет старое)
     * @param memoryMb      новый лимит памяти в мегабайтах;
     *                      {@code <= 0} означает авто (без лимита)
     * @return обновлённый профиль либо пусто, если профиля с таким id нет
     * @throws IOException если список профилей нельзя сохранить
     */
    public Optional<ModdedProfile> updateProfile(String id,
                                                 List<String> extraJvmArgs,
                                                 String displayName,
                                                 int memoryMb)
            throws IOException {
        List<ModdedProfile> profiles = loadProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            ModdedProfile p = profiles.get(i);
            if (p.id().equals(id)) {
                String name = (displayName == null || displayName.isBlank())
                        ? p.name() : displayName.trim();
                String group = parentGroup(p);
                // Имя без пригодных символов откатывается на
                // загрузчик + версию MC вместо «profile-N».
                String dirBase = sanitize(name);
                if (dirBase.isBlank()) {
                    dirBase = defaultDisplayName(p.loaderType(),
                            p.minecraftVersion());
                }
                String fullId;
                if (group.isEmpty()) {
                    // Плоская legacy-запись: старое поведение без группы.
                    fullId = uniqueDirectoryName(dirBase, profiles, p.id());
                } else {
                    String leaf = uniqueLeafName(group, dirBase, profiles, p.id());
                    fullId = group + "/" + leaf;
                }
                if (!fullId.equals(p.id())) {
                    Path source = storage.moddedProfileDir(p.id());
                    Path target = storage.moddedProfileDir(fullId);
                    try {
                        if (Files.exists(source)) {
                            Files.createDirectories(target.getParent());
                            Files.move(source, target);
                        }
                    } catch (IOException e) {
                        throw new IOException(
                                "Could not rename the instance folder "
                                + "(is the game running?): "
                                + e.getMessage(), e);
                    }
                }
                ModdedProfile updated = new ModdedProfile(
                        fullId, name, p.loaderType(), p.loaderVersion(),
                        p.minecraftVersion(), p.versionId(),
                        "profiles/" + fullId,
                        p.components(),
                        extraJvmArgs == null ? List.of()
                                : List.copyOf(extraJvmArgs),
                        normalizeMemory(memoryMb),
                        p.createdTimeRaw(), p.lastPlayedTimeRaw());
                profiles.set(i, updated);
                saveProfiles(profiles);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    /**
     * Фиксирует запуск в метке {@code lastPlayed} профиля.
     */
    public void touchLastPlayed(String id) throws IOException {
        List<ModdedProfile> profiles = loadProfiles();
        for (int i = 0; i < profiles.size(); i++) {
            ModdedProfile p = profiles.get(i);
            if (p.id().equals(id)) {
                profiles.set(i, new ModdedProfile(
                        p.id(), p.name(), p.loaderType(), p.loaderVersion(),
                        p.minecraftVersion(), p.versionId(), p.gameDirPath(),
                        p.components(), p.extraJvmArgs(), p.memoryMb(),
                        p.createdTimeRaw(),
                        OffsetDateTime.now().format(TIME_FORMAT)));
                break;
            }
        }
        saveProfiles(profiles);
    }

    /**
     * Разрешает игровой каталог профиля относительно корня хранилища.
     */
    public Path resolveGameDir(ModdedProfile profile) {
        return storage.root().resolve(profile.gameDirPath());
    }

    /**
     * Создаёт стандартную раскладку папок модовой сборки внутри заданного
     * каталога ({@code mods}, {@code config}, {@code resourcepacks},
     * {@code shaderpacks}, {@code saves}, {@code logs}). Существующие
     * папки и прочие пользовательские файлы не трогаются.
     */
    public static void ensureProfileFolders(Path gameDir) throws IOException {
        Files.createDirectories(gameDir);
        for (String folder : STANDARD_FOLDERS) {
            Files.createDirectories(gameDir.resolve(folder));
        }
    }

    // ------------------------------------------------------------------
    //  Именование каталогов
    // ------------------------------------------------------------------

    /**
     * Группа версии для раскладки на диске: {@code <загрузчик>-<mc>},
     * например {@code fabric-1.21.1}, {@code forge-1.20.1},
     * {@code vanilla-1.21.4}. Все сборки на одну версию лежат в
     * {@code profiles/<группа>/}.
     */
    public static String versionGroup(ModLoaderType loaderType, String minecraftVersion) {
        String loader = loaderType == null
                ? "unknown"
                : loaderType.name().toLowerCase(Locale.ROOT);
        String mc = sanitize(minecraftVersion);
        if (mc.isBlank()) {
            mc = "unknown";
        }
        String group = sanitize(loader + "-" + mc);
        return group.isBlank() ? "unknown" : group;
    }

    /** Родительская группа id ({@code fabric-1.21.1} для {@code fabric-1.21.1/Сборка}); пусто для плоских legacy. */
    static String parentGroup(ModdedProfile profile) {
        return parentGroupOfId(profile.id());
    }

    static String parentGroupOfId(String id) {
        if (id == null) return "";
        int slash = id.lastIndexOf('/');
        if (slash <= 0) return "";
        return id.substring(0, slash);
    }

    /**
     * Выводит уникальное имя каталога из отображаемого имени: само имя,
     * когда оно безопасно для файловой системы (чтобы папка совпадала с именем
     * в лаунчере), числовые суффиксы против существующих профилей и
     * существующих каталогов на диске.
     * Оставлено для плоских legacy-записей без группы.
     */
    private String uniqueDirectoryName(String displayName,
                                       List<ModdedProfile> existing) {
        return uniqueDirectoryName(displayName, existing, null);
    }

    /**
     * Выводит безопасное для файловой системы уникальное имя каталога из
     * отображаемого имени. Переименовываемый профиль ({@code excludeId}) не
     * блокирует собственное имя, поэтому косметические переименования сохраняют папку.
     */
    private String uniqueDirectoryName(String displayName,
                                       List<ModdedProfile> existing,
                                       String excludeId) {
        String base = sanitize(displayName);
        if (base.isBlank()) {
            base = "profile";
        }

        String candidate = base;
        int suffix = 2;
        while (isTaken(candidate, existing, excludeId)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean isTaken(String dirName, List<ModdedProfile> existing,
                            String excludeId) {
        if (existing.stream().anyMatch(p -> p.id().equals(dirName)
                && !p.id().equals(excludeId))) {
            return true;
        }
        Path dir = storage.moddedProfileDir(dirName);
        if (excludeId != null
                && dir.equals(storage.moddedProfileDir(excludeId))) {
            return false;
        }
        return Files.exists(dir);
    }

    /**
     * Уникальное имя папки сборки внутри группы версии.
     * Одинаковые имена на разных версиях не конфликтуют.
     */
    private String uniqueLeafName(String group, String leafBase,
                                  List<ModdedProfile> existing,
                                  String excludeId) {
        String base = sanitize(leafBase);
        if (base.isBlank()) {
            base = "profile";
        }
        String candidate = base;
        int suffix = 2;
        while (isLeafTaken(group, candidate, existing, excludeId)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean isLeafTaken(String group, String leaf,
                                List<ModdedProfile> existing,
                                String excludeId) {
        String fullId = group + "/" + leaf;
        if (existing.stream().anyMatch(p -> p.id().equals(fullId)
                && !p.id().equals(excludeId))) {
            return true;
        }
        Path dir = storage.moddedProfileDir(fullId);
        if (excludeId != null
                && dir.equals(storage.moddedProfileDir(excludeId))) {
            return false;
        }
        return Files.exists(dir);
    }

    /**
     * Делает отображаемое имя безопасным для папки: удаляются только запрещённые
     * в файловой системе символы ({@code \ / : * ? " < > |} и управляющие),
     * концевые точки/пробелы обрезаются. Всё остальное — пробелы,
     * точки, верхний регистр, Unicode — остаётся, чтобы папка точно совпадала с
     * именем в лаунчере («Моя сборка» остаётся «Моя сборка»).
     * Возвращает {@code ""}, когда ничего пригодного не осталось.
     */
    static String sanitize(String name) {
        if (name == null) return "";
        String cleaned = name.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "");
        cleaned = cleaned.strip().replaceAll("[. ]+$", "");
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return "";
        }
        if (RESERVED_NAMES.contains(cleaned.toLowerCase(Locale.ROOT))) {
            cleaned += "_";
        }
        return cleaned;
    }

    /** Имена устройств Windows, которые не могут быть папками. */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5",
            "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5",
            "lpt6", "lpt7", "lpt8", "lpt9");

    // ------------------------------------------------------------------
    //  JSON (сериализация/десериализация)
    // ------------------------------------------------------------------

    private JsonObject toJson(ModdedProfile p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", p.id());
        obj.addProperty("name", p.name());
        obj.addProperty("loaderType", p.loaderType().name());
        obj.addProperty("loaderVersion", p.loaderVersion());
        obj.addProperty("minecraftVersion", p.minecraftVersion());
        obj.addProperty("versionId", p.versionId());
        obj.addProperty("gameDirPath", p.gameDirPath());
        obj.add("components", stringArray(p.components()));
        obj.add("extraJvmArgs", stringArray(p.extraJvmArgs()));
        obj.addProperty("memoryMb", p.memoryMb());
        obj.addProperty("createdTime", p.createdTimeRaw());
        if (p.lastPlayedTimeRaw() != null) {
            obj.addProperty("lastPlayedTime", p.lastPlayedTimeRaw());
        }
        return obj;
    }

    private ModdedProfile fromJson(JsonObject obj) {
        try {
            String id = requiredString(obj, "id");
            String name = requiredString(obj, "name");
            ModLoaderType loaderType = ModLoaderType.valueOf(
                    requiredString(obj, "loaderType"));
            String loaderVersion = requiredString(obj, "loaderVersion");
            String minecraftVersion = requiredString(obj, "minecraftVersion");
            String versionId = requiredString(obj, "versionId");
            String gameDirPath = requiredString(obj, "gameDirPath");

            List<String> components = stringList(obj, "components");
            List<String> extraJvmArgs = stringList(obj, "extraJvmArgs");
            int memoryMb = normalizeMemory(optionalInt(obj, "memoryMb"));
            String created = optionalString(obj, "createdTime");
            String lastPlayed = optionalString(obj, "lastPlayedTime");

            return new ModdedProfile(id, name, loaderType, loaderVersion,
                    minecraftVersion, versionId, gameDirPath, components,
                    extraJvmArgs, memoryMb, created, lastPlayed);
        } catch (Exception e) {
            // Пропустить повреждённые записи, а не ронять весь список
            return null;
        }
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        return arr;
    }

    private static List<String> stringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement elem : obj.getAsJsonArray(key)) {
                if (elem.isJsonPrimitive()) {
                    result.add(elem.getAsString());
                }
            }
        }
        return result;
    }

    private static String requiredString(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return obj.get(key).getAsString();
    }

    private static String optionalString(JsonObject obj, String key) {
        return JsonStrings.getStringOrNull(obj, key);
    }

    private static int optionalInt(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return 0;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    //  Имена, память и итоговые аргументы запуска
    // ------------------------------------------------------------------

    /** Автоматическое отображаемое имя: «Loader MC» (например, «Forge 1.20.1»). */
    public static String defaultDisplayName(ModLoaderType loaderType,
                                            String minecraftVersion) {
        return loaderType.displayName() + " " + minecraftVersion;
    }

    /** Нормализует лимит памяти: {@code <= 0} означает авто. */
    public static int normalizeMemory(int memoryMb) {
        return Math.max(0, memoryMb);
    }

    /**
     * Краткая человекочитаемая форма лимита памяти на текущем языке
     * интерфейса («4 GB», «512 MB» или «Auto»).
     */
    public static String formatMemory(int memoryMb) {
        if (memoryMb <= 0) {
            return org.example.launcher.i18n.Lang.tr("memory.auto");
        }
        if (memoryMb >= 1024 && memoryMb % 1024 == 0) {
            return org.example.launcher.i18n.Lang.tr("memory.gb",
                    memoryMb / 1024);
        }
        return org.example.launcher.i18n.Lang.tr("memory.mb", memoryMb);
    }

    /**
     * JVM-аргументы, фактически используемые при запуске: сначала настроенный
     * лимит памяти (как {@code -Xmx}), затем доп. аргументы профиля с удалёнными
     * конфликтующими {@code -Xmx}/{@code -Xms}. Без настроенного лимита доп.
     * аргументы используются как есть.
     */
    public static List<String> effectiveJvmArgs(ModdedProfile profile) {
        if (profile.memoryMb() <= 0) {
            return profile.extraJvmArgs();
        }
        List<String> args = new ArrayList<>();
        args.add("-Xmx" + profile.memoryMb() + "M");
        for (String arg : profile.extraJvmArgs()) {
            if (arg.matches("-Xmx\\d+[mMgG]") || arg.matches("-Xms\\d+[mMgG]")) {
                continue;
            }
            args.add(arg);
        }
        return List.copyOf(args);
    }
}
