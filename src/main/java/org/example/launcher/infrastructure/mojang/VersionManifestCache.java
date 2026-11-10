package org.example.launcher.infrastructure.mojang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionType;
import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Файл версий лаунчера: {@code <gameDir>/launcher/versions.json} —
 * локальная копия официального манифеста Mojang для отображения в
 * интерфейсе и работы без интернета.
 *
 * <p>Записывается при каждой успешной загрузке манифеста (в файл
 * сохраняются id, тип, дата выхода и {@code metadataUrl} каждой версии —
 * полный комплект для последующего скачивания). Читается на старте
 * лаунчера: до любого сетевого запроса интерфейс уже имеет список
 * версий, а критичные без него операции — создание сборок и установка
 * сборок с Яндекс Диска — не блокируются, когда {@code launchermeta}
 * недоступен (офлайн, блокировка провайдера, обрыв).</p>
 *
 * <p>Разбор и сериализация изолированы от файловой системы, поэтому их
 * можно юнит-тестировать без записи на диск.</p>
 */
public final class VersionManifestCache {

    /** Имя файла версий внутри служебного каталога лаунчера. */
    public static final String FILE_NAME = "versions.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private VersionManifestCache() {
    }

    /** Полный путь файла версий по умолчанию. */
    public static Path defaultFile() {
        return GameDirectory.defaultDirectory().root()
                .resolve("launcher").resolve(FILE_NAME);
    }

    /**
     * Читает файл версий; пустой список при его отсутствии или битом
     * содержимом (файл перезапишется при следующем сетевом успехе).
     */
    public static List<MinecraftVersion> load() {
        return load(defaultFile());
    }

    /** Читает версии из указанного файла (для тестов с temp-каталогом). */
    public static List<MinecraftVersion> load(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return List.of();
            }
            return parse(Files.readString(file));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Разбирает JSON файла версий в доменные записи. Порядок сохранён
     * (от новых к старым, как в манифесте Mojang). Видимость на уровне
     * пакета — для юнит-тестов.
     */
    static List<MinecraftVersion> parse(String json) {
        CacheDto dto;
        try {
            dto = GSON.fromJson(json, CacheDto.class);
        } catch (JsonSyntaxException e) {
            return List.of();
        }
        if (dto == null || dto.versions == null) {
            return List.of();
        }
        List<MinecraftVersion> versions = new ArrayList<>(dto.versions.size());
        for (EntryDto entry : dto.versions) {
            if (entry == null || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            versions.add(MinecraftVersion.of(entry.id,
                    VersionType.fromMojangId(entry.type),
                    entry.releaseTime, entry.url));
        }
        return versions;
    }

    /**
     * Сохраняет список версий в файл по умолчанию. Лучший результат:
     * ошибка записи не должна ронять загрузку манифеста.
     *
     * @param versions отсортированный список от новых к старым
     * @return {@code true}, если файл записан
     */
    public static boolean save(List<MinecraftVersion> versions) {
        return save(defaultFile(), versions);
    }

    /** Запись в указанный файл (для тестов); {@code true} при успехе. */
    public static boolean save(Path file, List<MinecraftVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return false;
        }
        List<EntryDto> entries = new ArrayList<>(versions.size());
        for (MinecraftVersion version : versions) {
            EntryDto entry = new EntryDto();
            entry.id = version.id();
            entry.type = version.type().mojangId();
            entry.releaseTime = version.releaseTime()
                    .map(t -> t.toString()).orElse(null);
            entry.url = version.url();
            entries.add(entry);
        }
        CacheDto dto = new CacheDto();
        dto.source = "mojang";
        dto.fetched = OffsetDateTime.now().toString();
        dto.versions = entries;
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(dto));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- Gson-DTO, зеркалящие файл ----

    static final class CacheDto {
        String source;
        String fetched;
        List<EntryDto> versions;
    }

    static final class EntryDto {
        String id;
        String type;
        String releaseTime;
        String url;
    }
}
