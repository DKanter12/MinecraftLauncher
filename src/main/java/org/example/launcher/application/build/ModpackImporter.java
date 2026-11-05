package org.example.launcher.application.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.ModdedProfile;
import org.example.launcher.domain.model.ModLoaderType;
import org.example.launcher.domain.model.ModLoaderVersion;
import org.example.launcher.infrastructure.download.DownloadResult;
import org.example.launcher.infrastructure.download.DownloadTask;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.http.UrlFetcher;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;

/**
 * Импорт модпаков в лаунчер (Модуль 5 ТЗ): открытый формат
 * <a href="https://docs.modrinth.com/docs/modpacks/submission-format/">.mrpack</a>
 * (Modrinth) — архив с манифестом {@code modrinth.index.json} и списком
 * файлов по внешним ссылкам.
 *
 * <p>Пайплайн: разбор манифеста (версия Minecraft + загрузчик) → выбор
 * версии Mojang из манифеста и версии загрузчика из реестра → создание
 * изолированного инстанса через {@link CreateBuildUseCase} → скачивание
 * файлов сборки по ссылкам в каталог инстанса с проверкой SHA-1.</p>
 *
 * <p>Файлы со стратегией {@code client: "optional"} / {@code "unsupported"}
 * пропускаются — лаунчер импортирует только клиентский контент.</p>
 */
public class ModpackImporter {

    /** Итог импорта: созданный инстанс плюс сводка по файлам. */
    public record Result(ModdedProfile profile,
                         String packName,
                         int downloaded,
                         int skipped,
                         int failed) {
    }

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private final CreateBuildUseCase createBuild;
    private final ModLoaderRegistry loaders;
    private final FileSystemBuildRepository builds;
    private final UrlFetcher fetcher;

    public ModpackImporter(CreateBuildUseCase createBuild,
                           ModLoaderRegistry loaders,
                           FileSystemBuildRepository builds,
                           UrlFetcher fetcher) {
        this.createBuild = Objects.requireNonNull(createBuild, "createBuild");
        this.loaders = Objects.requireNonNull(loaders, "loaders");
        this.builds = Objects.requireNonNull(builds, "builds");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    /**
     * Импортирует {@code .mrpack}-модпак как новый изолированный инстанс.
     *
     * @param pack             путь к архиву (.mrpack / .zip)
     * @param manifestVersions манифест Mojang для подбора версии игры
     * @param progress         прогресс (может быть {@code null})
     * @return итог импорта
     * @throws IOException если архив не .mrpack, манифест повреждён,
     *                     версия игры/загрузчика недоступна или установка не удалась
     */
    public Result importPack(Path pack,
                             List<MinecraftVersion> manifestVersions,
                             InstallationProgress progress) throws IOException {
        Objects.requireNonNull(pack, "pack");
        InstallationProgress sink =
                progress == null ? InstallationProgress.NONE : progress;
        if (!Files.isRegularFile(pack)) {
            throw new IOException("File not found: " + pack);
        }

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("modrinth.index.json");
            if (manifestEntry == null) {
                throw new IOException(
                        "Not an .mrpack archive: modrinth.index.json "
                        + "is missing");
            }
            JsonObject manifest = JsonParser.parseString(
                    readEntry(zip, manifestEntry)).getAsJsonObject();
            return importMrpack(zip, manifest, pack, manifestVersions, sink);
        }
    }

    private Result importMrpack(ZipFile zip,
                                JsonObject manifest,
                                Path pack,
                                List<MinecraftVersion> manifestVersions,
                                InstallationProgress sink) throws IOException {
        String game = string(manifest, "game", "minecraft");
        if (!"minecraft".equals(game)) {
            throw new IOException("Unsupported modpack game: " + game);
        }
        LoaderRef loaderRef = readLoader(manifest);
        MinecraftVersion mcVersion = findMinecraftVersion(manifestVersions,
                loaderRef.minecraftVersion());
        org.example.launcher.domain.model.ModLoaderVersion loader =
                resolveLoader(loaderRef);

        String packName = archiveDisplayName(pack);
        Build build = new Build(packName, loaderRef.minecraftVersion(),
                loaderRef.type(), loaderRef.type() == ModLoaderType.VANILLA
                        ? "" : loaderRef.loaderVersion());
        BuildRequest request = new BuildRequest(build, mcVersion,
                loaderRef.type() == ModLoaderType.VANILLA ? null : loader,
                List.of());
        ModdedProfile profile = createBuild.createBuild(request, sink);

        Path gameDir = builds.resolveGameDir(profile);
        FileSync sync = downloadFiles(zip, manifest, gameDir, sink);
        return new Result(profile, packName, sync.downloaded, sync.skipped,
                sync.failed);
    }

    // ------------------------------------------------------------------
    //  Манифест .mrpack
    // ------------------------------------------------------------------

    private record LoaderRef(ModLoaderType type, String minecraftVersion,
                             String loaderVersion) {
    }

    private LoaderRef readLoader(JsonObject manifest) throws IOException {
        JsonObject dependencies = manifest.getAsJsonObject("dependencies");
        if (dependencies == null) {
            throw new IOException("modrinth.index.json: dependencies missing");
        }
        String minecraft = string(dependencies, "minecraft", null);
        if (minecraft == null || minecraft.isBlank()) {
            throw new IOException("modrinth.index.json: minecraft "
                    + "dependency missing");
        }
        for (ModLoaderType type : ModLoaderType.values()) {
            if (type == ModLoaderType.VANILLA) continue;
            String key = loaderKey(type);
            String version = string(dependencies, key, null);
            if (version != null && !version.isBlank()) {
                return new LoaderRef(type, minecraft, version);
            }
        }
        return new LoaderRef(ModLoaderType.VANILLA, minecraft, "");
    }

    /** Ключ зависимости для Modrinth (например, {@code fabric-loader}). */
    private static String loaderKey(ModLoaderType type) {
        return switch (type) {
            case VANILLA -> "minecraft";
            case FABRIC -> "fabric-loader";
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case QUILT -> "quilt-loader";
        };
    }

    private MinecraftVersion findMinecraftVersion(
            List<MinecraftVersion> manifestVersions, String id)
            throws IOException {
        for (MinecraftVersion v : manifestVersions) {
            if (v.id().equals(id)) {
                return v;
            }
        }
        throw new IOException("Minecraft " + id
                + " is not in the version manifest");
    }

    private org.example.launcher.domain.model.ModLoaderVersion resolveLoader(
            LoaderRef ref) throws IOException {
        if (ref.type() == ModLoaderType.VANILLA) {
            return null;
        }
        ModLoaderRegistry.Entry entry = loaders.get(ref.type())
                .orElseThrow(() -> new IOException(
                        "Loader is not supported: " + ref.type()));
        List<ModLoaderVersion> versions =
                entry.provider().fetchVersions(ref.minecraftVersion());
        for (ModLoaderVersion v : versions) {
            if (v.loaderVersion().equals(ref.loaderVersion())) {
                return v;
            }
        }
        throw new IOException("Loader version is not available: "
                + ref.type().displayName() + " " + ref.loaderVersion()
                + " for Minecraft " + ref.minecraftVersion());
    }

    private static String archiveDisplayName(Path pack) {
        String name = pack.getFileName() == null ? "Modpack"
                : pack.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ------------------------------------------------------------------
    //  Файлы манифеста
    // ------------------------------------------------------------------

    private record FileRef(String path, String sha1, List<String> urls,
                           long size) {
    }

    private List<FileRef> readFiles(JsonObject manifest) throws IOException {
        List<FileRef> refs = new ArrayList<>();
        if (!manifest.has("files") || !manifest.get("files").isJsonArray()) {
            return refs;
        }
        for (JsonElement elem : manifest.getAsJsonArray("files")) {
            if (!elem.isJsonObject()) continue;
            JsonObject file = elem.getAsJsonObject();
            String path = string(file, "path", null);
            if (path == null || path.isBlank()) continue;

            // Требуется контент для клиента (env может отсутствовать)
            if (!requiredForClient(file)) continue;

            JsonObject hashes = file.has("hashes")
                    && file.get("hashes").isJsonObject()
                    ? file.getAsJsonObject("hashes") : new JsonObject();
            String sha1 = string(hashes, "sha1", null);
            if (sha1 == null || sha1.isBlank()) {
                continue; // без SHA-1 данные не проверяются — не качаем
            }
            List<String> urls = new ArrayList<>();
            if (file.has("downloads")
                    && file.get("downloads").isJsonArray()) {
                for (JsonElement urlElem : file.getAsJsonArray("downloads")) {
                    if (urlElem.isJsonPrimitive()) {
                        urls.add(urlElem.getAsString());
                    }
                }
            }
            if (urls.isEmpty()) continue;
            refs.add(new FileRef(path, sha1.toLowerCase(Locale.ROOT), urls,
                    file.has("fileSize") && file.get("fileSize").isJsonPrimitive()
                            ? file.get("fileSize").getAsLong() : 0));
        }
        return refs;
    }

    /** {@code env.client}: required / отсутствует — качаем; optional/unsupported — нет. */
    private boolean requiredForClient(JsonObject file) {
        if (!file.has("env") || !file.get("env").isJsonObject()) {
            return true;
        }
        String client = string(file.getAsJsonObject("env"), "client", "");
        return client.isBlank() || "required".equals(client);
    }

    private record FileSync(int downloaded, int skipped, int failed,
                            List<String> errors) {
    }

    private FileSync downloadFiles(ZipFile zip,
                                   JsonObject manifest,
                                   Path gameDir,
                                   InstallationProgress sink)
            throws IOException {
        List<FileRef> files = readFiles(manifest);
        sink.onStart(files.size(), files.stream()
                .mapToLong(FileRef::size).sum());
        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (FileRef ref : files) {
            String category = categoryOf(ref.path());
            DownloadTask task = new DownloadTask(
                    ref.urls().get(0), safeTarget(gameDir, ref.path()),
                    ref.sha1(), ref.size(), category, ref.path());
            sink.onFileStart(index, task);
            DownloadResult result = downloadOne(ref, task, gameDir);
            sink.onFileComplete(index, result);
            if (result.isDownloaded()) downloaded++;
            else if (result.isSkipped()) skipped++;
            else {
                failed++;
                errors.add(task.name());
            }
            index++;
        }
        sink.onComplete(new InstallationResult(files.size(), downloaded,
                skipped, failed, files.stream().mapToLong(FileRef::size).sum(),
                List.of()));
        return new FileSync(downloaded, skipped, failed, errors);
    }

    /** Категория для прогресса: mods/config/other по верхнему каталогу. */
    private static String categoryOf(String path) {
        String top = path.contains("/") ? path.substring(0, path.indexOf('/'))
                : "other";
        return switch (top) {
            case "mods" -> "mods";
            case "config" -> "config";
            default -> "other";
        };
    }

    private DownloadResult downloadOne(FileRef ref, DownloadTask task,
                                       Path gameDir) {
        Path target;
        try {
            target = safeTarget(gameDir, ref.path());
        } catch (IOException unsafe) {
            return DownloadResult.failed(task, unsafe.getMessage(), 1);
        }
        try {
            if (Files.isRegularFile(target)
                    && org.example.launcher.application.build
                            .ServerBuildInstaller.digestOf(target, "SHA-1")
                            .equalsIgnoreCase(ref.sha1())) {
                return DownloadResult.skipped(task);
            }
            IOException lastError = null;
            for (String url : ref.urls()) {
                try {
                    fetcher.download(url, target, DOWNLOAD_TIMEOUT,
                            (long l, long t) -> { });
                    if (ServerBuildInstaller.digestOf(target, "SHA-1")
                            .equalsIgnoreCase(ref.sha1())) {
                        return DownloadResult.downloaded(task,
                                Files.size(target), 1);
                    }
                    Files.deleteIfExists(target);
                    lastError = new IOException("SHA-1 mismatch from " + url);
                } catch (IOException e) {
                    Files.deleteIfExists(target);
                    lastError = e;
                }
            }
            return DownloadResult.failed(task,
                    lastError == null ? "No mirrors" : lastError.getMessage(),
                    ref.urls().size());
        } catch (IOException e) {
            return DownloadResult.failed(task, e.getMessage(), 1);
        }
    }

    /** Каталог цели с защитой от zip-slip (путь обязан остаться в gameDir). */
    private static Path safeTarget(Path gameDir, String path)
            throws IOException {
        if (path.isBlank() || path.startsWith("/")
                || path.startsWith("\\") || path.contains("..")
                || path.contains(":")) {
            throw new IOException("Unsafe path in modpack: " + path);
        }
        Path resolved = gameDir.resolve(path.replace("\\", "/")).normalize();
        if (!resolved.startsWith(gameDir)) {
            throw new IOException("Unsafe path in modpack: " + path);
        }
        Path parent = resolved.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return resolved;
    }

    private static String readEntry(ZipFile zip, ZipEntry entry)
            throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Строка из JSON или дефолт, если поле отсутствует. */
    static String string(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key)
                || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }
}
