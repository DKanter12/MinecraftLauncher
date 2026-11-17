package org.example.launcher.application.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.example.launcher.application.build.CreateBuildUseCase;
import org.example.launcher.application.build.MinecraftVersionManager;
import org.example.launcher.application.build.ModpackImporter;
import org.example.launcher.domain.model.MinecraftVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.download.InstallationProgress;
import org.example.launcher.infrastructure.download.InstallationResult;
import org.example.launcher.infrastructure.download.InstallationService;
import org.example.launcher.infrastructure.filesystem.FileSystemBuildRepository;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.http.UrlFetcher;
import org.example.launcher.infrastructure.loaders.ModLoaderRegistry;
import org.example.launcher.infrastructure.mojang.VersionMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ModpackImporter: распаковка overrides из .mrpack")
class ModpackImporterOverridesTest {

    @Test
    @DisplayName("clientOverrideItems: только contents/overrides, server-overrides исключены")
    void collectsClientOverridesOnly(@TempDir Path tempDir)
            throws IOException {
        Path pack = tempDir.resolve("Mini Pack.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack))) {
            writeEntry(zip, "modrinth.index.json", "{}");
            writeEntry(zip, "overrides/mods/a.jar", "jar-content");
            writeEntry(zip, "overrides/config/b.toml", "config-content");
            writeEntry(zip, "overrides/kubejs/x.js", "script");
            writeEntry(zip, "overrides/", "");                  // каталог — пропускается
            writeEntry(zip, "server-overrides/mods/server.jar", "s");
        }
        try (var zipFile = new java.util.zip.ZipFile(pack.toFile())) {
            List<ModpackImporter.OverrideItem> items =
                    ModpackImporter.clientOverrideItems(zipFile);

            assertEquals(3, items.size());
            assertEquals("mods/a.jar", items.get(0).path());
            assertEquals("config/b.toml", items.get(1).path());
            assertEquals("kubejs/x.js", items.get(2).path());
            assertEquals("jar-content".length(), items.get(0).size());
            // путей наружу, абсолютных и server-side быть не должно
            assertTrue(items.stream().noneMatch(
                    i -> i.path().startsWith("server-overrides")));
            assertTrue(items.stream().noneMatch(
                    i -> i.path().contains("..")));
        }
    }

    @Test
    @DisplayName("пустой архив без overrides даёт пустой список")
    void emptyArchive(@TempDir Path tempDir) throws IOException {
        Path pack = tempDir.resolve("Empty.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack))) {
            writeEntry(zip, "modrinth.index.json", "{}");
        }
        try (var zipFile = new java.util.zip.ZipFile(pack.toFile())) {
            assertTrue(ModpackImporter.clientOverrideItems(zipFile).isEmpty());
        }
    }

    @Test
    @DisplayName("повторный импорт сборки с существующим именем отклоняется")
    void rejectsDuplicatePackName(@TempDir Path tempDir) throws IOException {
        Path pack = tempDir.resolve("Mini Pack.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack))) {
            writeEntry(zip, "modrinth.index.json", "{"
                    + "\"formatVersion\": 1,"
                    + "\"game\": \"minecraft\","
                    + "\"dependencies\": {\"minecraft\": \"1.21.1\"},"
                    + "\"files\": []}");
        }
        // Домашний каталог того же импортера: в библиотеке уже есть
        // сборка с именем, совпадающим с именем архива
        GameDirectory gameDir = new GameDirectory(tempDir.resolve("home"));
        FileSystemBuildRepository seedRepository =
                new FileSystemBuildRepository(gameDir);
        seedRepository.createProfile(
                org.example.launcher.domain.model.ModLoaderType.VANILLA,
                "", "1.21.1", "1.21.1", List.of(), "Mini Pack", 2048);

        ModpackImporter importer = importer(tempDir);
        List<MinecraftVersion> versions = List.of(
                MinecraftVersion.of("1.21.1",
                        org.example.launcher.domain.model.VersionType.RELEASE,
                        "2024-01-01T00:00:00+00:00", null));
        ModpackImporter.DuplicatePackException thrown =
                assertThrows(ModpackImporter.DuplicatePackException.class,
                        () -> importer.importPack(pack, versions,
                                InstallationProgress.NONE));
        assertEquals("Mini Pack", thrown.packName());

        // Дубликат до создания профиля: в библиотеке по-прежнему одна сборка
        var profiles = new FileSystemBuildRepository(gameDir).loadProfiles();
        assertEquals(1, profiles.size());
    }

    @Test
    @DisplayName("импорт не .mrpack честно отклоняется")
    void rejectsNonMrpack(@TempDir Path tempDir) throws IOException {
        Path pack = tempDir.resolve("wrong.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(pack))) {
            writeEntry(zip, "something.txt", "no manifest");
        }
        ModpackImporter importer = importer(tempDir);
        IOException thrown = assertThrows(IOException.class,
                () -> importer.importPack(pack, List.of(), null));
        assertTrue(thrown.getMessage().contains("modrinth.index.json"),
                "ожидается внятная причина: отсутствие манифеста");
    }

    /**
     * Настоящий импортер со стабами сетевых зависимостей: достаточно
     * для проверки отклонения неправильного архива до любых сетевых
     * вызовов.
     */
    private ModpackImporter importer(@TempDir Path tempDir) {
        GameDirectory gameDir = new GameDirectory(tempDir.resolve("home"));
        var noopMetadata = new VersionMetadataService() {
            @Override
            public VersionMetadata fetchMetadata(
                    MinecraftVersion version) {
                throw new UnsupportedOperationException();
            }
        };
        var noopInstaller = new InstallationService() {
            @Override
            public InstallationResult install(VersionMetadata metadata,
                    GameDirectory gameDirectory,
                    InstallationProgress progress) {
                throw new UnsupportedOperationException();
            }
        };
        var versionManager = new MinecraftVersionManager(noopMetadata,
                noopInstaller, new ModLoaderRegistry(), gameDir);
        var repository = new FileSystemBuildRepository(gameDir);
        return new ModpackImporter(
                new CreateBuildUseCase(versionManager, repository, gameDir),
                new ModLoaderRegistry(), repository, new UrlFetcher());
    }

    private static void writeEntry(ZipOutputStream zip, String name,
                                   String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        if (!content.isEmpty()) {
            zip.write(content.getBytes("UTF-8"));
        }
        zip.closeEntry();
    }
}
