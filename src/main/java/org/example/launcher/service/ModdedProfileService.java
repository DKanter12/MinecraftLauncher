package org.example.launcher.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.ModdedProfile;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * Manages game instances (vanilla and modded): creation, persistence,
 * deletion and the per-instance game directory layout.
 * <p>
 * Instances are stored in {@code instances.json} in the storage root
 * (a legacy {@code modded_profiles.json} is read as a fallback). Each
 * instance owns a game directory under {@code profiles/{id}/} with
 * the standard pack layout ({@code mods}, {@code config},
 * {@code resourcepacks}, {@code shaderpacks}, {@code saves},
 * {@code logs}), created automatically. Different instances never
 * share these directories and therefore never conflict; shared
 * resources (client JAR, libraries, assets) live in the storage root
 * and are reused.
 */
public class ModdedProfileService {

    /**
     * Standard directories every instance game directory contains.
     * Users can drop mods, resource packs, shader packs and worlds
     * into them manually.
     */
    public static final List<String> STANDARD_FOLDERS = List.of(
            "mods", "config", "resourcepacks", "shaderpacks", "saves", "logs");

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Path profilesFile;
    private final Path legacyFile;
    private final GameDirectory storage;
    private final Gson gson;

    public ModdedProfileService(GameDirectory storage) {
        this.storage = storage;
        this.profilesFile = storage.instancesFile();
        this.legacyFile = storage.legacyModdedProfilesFile();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    /**
     * Loads all instances; an empty list if none exist yet. A legacy
     * {@code modded_profiles.json} is read when {@code instances.json}
     * does not exist yet.
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
     * Persists the full profile list.
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
    //  Profile lifecycle
    // ------------------------------------------------------------------

    /**
     * Creates a new game instance with its own game directory.
     * <p>
     * The instance may be vanilla ({@code loaderType == VANILLA},
     * {@code loaderVersion} empty) or a mod loader installation. The
     * display name defaults to the loader and the Minecraft version
     * but can be chosen freely and renamed later; the directory name
     * is derived from it (sanitized) and made unique by appending a
     * numeric suffix if needed. The standard folder layout is created
     * immediately so the user can start dropping mods into
     * {@code mods/} right away.
     *
     * @param loaderType      the instance type (VANILLA or a loader)
     * @param loaderVersion   the loader version (empty for vanilla)
     * @param minecraftVersion the target Minecraft version
     * @param versionId       the installed version id this instance
     *                        launches
     * @param extraJvmArgs    additional JVM launch parameters
     *                        (may be empty)
     * @param displayName     human-readable instance name; blank means
     *                        the automatic "Loader MC" name
     * @param memoryMb        dedicated RAM limit in megabytes;
     *                        {@code <= 0} means automatic (no limit)
     * @return the created, persisted instance
     * @throws IOException if the instance cannot be persisted or the
     *                     game directory cannot be created
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
        String dirName = uniqueDirectoryName(name, existing);
        String now = OffsetDateTime.now().format(TIME_FORMAT);

        List<String> components = new ArrayList<>();
        components.add("minecraft:" + minecraftVersion);
        if (!vanilla) {
            components.add(loaderType.name().toLowerCase(Locale.ROOT)
                    + ":" + loaderVersion);
        }

        ModdedProfile profile = new ModdedProfile(
                dirName,
                name,
                loaderType,
                vanilla ? "" : loaderVersion,
                minecraftVersion,
                versionId,
                "profiles/" + dirName,
                List.copyOf(components),
                extraJvmArgs == null ? List.of() : List.copyOf(extraJvmArgs),
                normalizeMemory(memoryMb),
                now,
                null);

        // Create the profile's game directory with the standard layout
        ensureProfileFolders(storage.moddedProfileDir(dirName));

        existing.add(profile);
        saveProfiles(existing);
        return profile;
    }

    /**
     * Deletes a profile from the registry.
     * <p>
     * The profile's game directory (with its mods and saves) is NOT
     * deleted — only the registry entry is removed, so no user data
     * is ever destroyed. The directory path is returned so callers
     * can inform the user where their files remain.
     *
     * @return the orphaned game directory, if the profile existed
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
        return Optional.of(storage.moddedProfileDir(removed.get().id()));
    }

    /**
     * Updates a profile's display name, extra JVM launch arguments
     * and memory limit.
     * <p>
     * Renaming also renames the game directory (and the profile id,
     * which follows the folder), so the folder always matches the
     * launcher name; mods, saves and builds move along untouched. A
     * blank name keeps the current one. When the target folder cannot
     * be renamed (e.g. the game is running from it), nothing is
     * persisted and an error is thrown.
     *
     * @param id            the profile to update
     * @param extraJvmArgs  new JVM launch parameters (may be empty)
     * @param displayName   new display name (blank keeps the old one)
     * @param memoryMb      new RAM limit in megabytes;
     *                      {@code <= 0} means automatic (no limit)
     * @return the updated profile, or empty if no profile with this id
     *         exists
     * @throws IOException if the profile list cannot be persisted
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
                // Renaming also renames the folder, so it always
                // matches the launcher name; the id follows the
                // folder. Mods, saves and builds move along untouched.
                String dirName = uniqueDirectoryName(name, profiles, p.id());
                if (!dirName.equals(p.id())) {
                    Path source = storage.moddedProfileDir(p.id());
                    Path target = storage.moddedProfileDir(dirName);
                    try {
                        if (Files.exists(source)) {
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
                        dirName, name, p.loaderType(), p.loaderVersion(),
                        p.minecraftVersion(), p.versionId(),
                        "profiles/" + dirName,
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
     * Records a launch in the profile's {@code lastPlayed} timestamp.
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
     * Resolves a profile's game directory against the storage root.
     */
    public Path resolveGameDir(ModdedProfile profile) {
        return storage.root().resolve(profile.gameDirPath());
    }

    /**
     * Creates the standard modded-pack folder layout inside the given
     * directory ({@code mods}, {@code config}, {@code resourcepacks},
     * {@code shaderpacks}, {@code saves}, {@code logs}). Existing
     * folders and any other user files are left untouched.
     */
    public static void ensureProfileFolders(Path gameDir) throws IOException {
        Files.createDirectories(gameDir);
        for (String folder : STANDARD_FOLDERS) {
            Files.createDirectories(gameDir.resolve(folder));
        }
    }

    // ------------------------------------------------------------------
    //  Directory naming
    // ------------------------------------------------------------------

    /**
     * Derives a filesystem-safe, unique directory name from the
     * display name. Unsafe characters are replaced, a fallback name is
     * used when nothing safe remains, and numeric suffixes ensure
     * uniqueness against both existing profiles and existing
     * directories on disk.
     */
    private String uniqueDirectoryName(String displayName,
                                       List<ModdedProfile> existing) {
        return uniqueDirectoryName(displayName, existing, null);
    }

    /**
     * Derives a filesystem-safe, unique directory name from the
     * display name. The profile being renamed ({@code excludeId}) does
     * not block its own name, so cosmetic renames keep their folder.
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
     * Keeps only safe filename characters ([A-Za-z0-9_-]); Cyrillic
     * letters are transliterated first (so "Моя сборка" becomes
     * "moya-sborka"), everything else collapses to a single dash.
     */
    static String sanitize(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length();) {
            int codePoint = lower.codePointAt(i);
            String mapped = TRANSLIT.get(codePoint);
            if (mapped != null) {
                latin.append(mapped);
            } else {
                latin.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return latin.toString().replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /** Cyrillic → Latin, so folders stay readable ASCII. */
    private static final Map<Integer, String> TRANSLIT = buildTranslit();

    private static Map<Integer, String> buildTranslit() {
        Map<Integer, String> map = new HashMap<>();
        String[][] pairs = {
            {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "g"}, {"д", "d"},
            {"е", "e"}, {"ё", "yo"}, {"ж", "zh"}, {"з", "z"}, {"и", "i"},
            {"й", "y"}, {"к", "k"}, {"л", "l"}, {"м", "m"}, {"н", "n"},
            {"о", "o"}, {"п", "p"}, {"р", "r"}, {"с", "s"}, {"т", "t"},
            {"у", "u"}, {"ф", "f"}, {"х", "h"}, {"ц", "ts"}, {"ч", "ch"},
            {"ш", "sh"}, {"щ", "shch"}, {"ъ", ""}, {"ы", "y"}, {"ь", ""},
            {"э", "e"}, {"ю", "yu"}, {"я", "ya"},
            {"ґ", "g"}, {"є", "ye"}, {"і", "i"}, {"ї", "yi"},
        };
        for (String[] pair : pairs) {
            map.put(pair[0].codePointAt(0), pair[1]);
        }
        return Map.copyOf(map);
    }

    // ------------------------------------------------------------------
    //  JSON (de)serialization
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
            // Skip corrupt entries rather than failing the whole list
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
        return obj.has(key) && obj.get(key).isJsonPrimitive()
                ? obj.get(key).getAsString() : null;
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
    //  Names, memory and effective launch arguments
    // ------------------------------------------------------------------

    /** The automatic display name: "Loader MC" (e.g. "Forge 1.20.1"). */
    public static String defaultDisplayName(ModLoaderType loaderType,
                                            String minecraftVersion) {
        return loaderType.displayName() + " " + minecraftVersion;
    }

    /** Normalizes a RAM limit: {@code <= 0} means automatic. */
    public static int normalizeMemory(int memoryMb) {
        return Math.max(0, memoryMb);
    }

    /**
     * Short human form of a RAM limit: "4 GB", "512 MB" or "Auto".
     */
    public static String formatMemory(int memoryMb) {
        if (memoryMb <= 0) {
            return "Auto";
        }
        if (memoryMb >= 1024 && memoryMb % 1024 == 0) {
            return (memoryMb / 1024) + " GB";
        }
        return memoryMb + " MB";
    }

    /**
     * The JVM arguments actually used at launch: the configured
     * memory limit first (as {@code -Xmx}), then the profile's extra
     * arguments with any conflicting {@code -Xmx}/{@code -Xms}
     * removed. Without a configured limit the extra arguments are
     * used as-is.
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
