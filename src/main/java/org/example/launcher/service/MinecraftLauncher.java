package org.example.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.infrastructure.download.ChecksumVerifier;
import org.example.launcher.infrastructure.filesystem.GameDirectory;
import org.example.launcher.infrastructure.minecraft.NativeExtractor;
import org.example.launcher.domain.model.DownloadInfo;
import org.example.launcher.domain.model.GameProfile;
import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.LaunchArguments;
import org.example.launcher.domain.model.LaunchResult;
import org.example.launcher.domain.model.Library;
import org.example.launcher.domain.model.MinecraftProcess;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.util.LibraryPaths;
import org.example.launcher.util.OsDetector;

/**
 * Реализация {@link MinecraftLaunchService} по умолчанию.
 * <p>
 * Выполняет предстартовую проверку (наличие файлов + целостность по SHA-1),
 * разрешение рантайма Java, построение аргументов и управление процессом.
 * <p>
 * Проверка файлов переиспользует ту же логику разрешения путей, что и
 * {@link MinecraftInstaller}, чтобы гарантировать соответствие между тем,
 * что было установлено, и тем, что проверяется при запуске.
 */
public class MinecraftLauncher implements MinecraftLaunchService {

    private final LaunchArgumentBuilder argumentBuilder;
    private final JavaResolutionService javaResolutionService;
    private final ChecksumVerifier checksumVerifier;

    public MinecraftLauncher(LaunchArgumentBuilder argumentBuilder,
                             JavaResolutionService javaResolutionService,
                             ChecksumVerifier checksumVerifier) {
        this.argumentBuilder = Objects.requireNonNull(argumentBuilder, "argumentBuilder");
        this.javaResolutionService = Objects.requireNonNull(javaResolutionService, "javaResolutionService");
        this.checksumVerifier = Objects.requireNonNull(checksumVerifier, "checksumVerifier");
    }

    // ------------------------------------------------------------------
    //  Запуск
    // ------------------------------------------------------------------

    @Override
    public LaunchResult launch(VersionMetadata metadata,
                                GameDirectory gameDir,
                                GameProfile profile) {
        return launch(metadata, gameDir, profile, null, List.of());
    }

    @Override
    public LaunchResult launch(VersionMetadata metadata,
                                GameDirectory gameDir,
                                GameProfile profile,
                                Path runtimeDirectory,
                                List<String> extraJvmArgs) {

        // 1. Проверка файлов
        List<String> missing = verifyFiles(metadata, gameDir);
        if (!missing.isEmpty()) {
            return LaunchResult.fileCheckFailed(
                    "Missing or corrupt files (" + missing.size() + "): "
                            + String.join(", ", missing));
        }

        // 2. Подбор Java
        JavaResolutionResult javaResult = javaResolutionService.resolve(metadata);
        if (!javaResult.isFound()) {
            return LaunchResult.javaNotFound(
                    javaResult.reason().orElse("No suitable Java runtime found"));
        }

        JavaRuntime javaRuntime = javaResult.runtime().orElseThrow();

        // 3. Распаковка нативов
        try {
            NativeExtractor.extractNatives(metadata, gameDir);
        } catch (IOException e) {
            return LaunchResult.launchFailed(
                    "Failed to extract native libraries: " + e.getMessage());
        }

        // 4. Построение аргументов (рабочий каталог + доп. JVM-аргументы для
        //    модовых профилей; значения по умолчанию для ванильных запусков)
        LaunchArguments args = argumentBuilder.build(
                metadata, gameDir, profile, javaRuntime,
                runtimeDirectory, extraJvmArgs);

        // 5. Старт процесса
        try {
            ProcessBuilder pb = new ProcessBuilder(args.fullCommand());
            pb.directory(args.workingDirectory().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            MinecraftProcess mcProcess = new MinecraftProcess(process);

            // Короткая пауза для поимки мгновенных падений (например, неверная версия
            // Java, отсутствующий main-класс), чтобы вернуть полезную ошибку
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!mcProcess.isAlive() && mcProcess.exitCode() != 0) {
                String err = mcProcess.stderr();
                if (err.isBlank()) {
                    err = mcProcess.stdout();
                }
                return LaunchResult.launchFailed(
                        "Process exited immediately (code " + mcProcess.exitCode() + ")"
                                + (err.isBlank() ? "" : ": " + err.lines()
                                        .limit(10).reduce("", (a, b) -> a + b + "\n")));
            }

            return LaunchResult.success(mcProcess);

        } catch (IOException e) {
            return LaunchResult.launchFailed(
                    "Failed to start Java process: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Проверка файлов
    // ------------------------------------------------------------------

    /**
     * Проверяет, что все файлы, требуемые метаданными версии, существуют
     * локально и (где известен SHA-1 хэш) совпадают с ожидаемой
     * контрольной суммой.
     *
     * @return список человекочитаемых описаний отсутствующих/повреждённых
     *         файлов; пуст, если всё в порядке
     */
    public List<String> verifyFiles(VersionMetadata metadata, GameDirectory gameDir) {
        List<String> problems = new ArrayList<>();
        String osName = OsDetector.mojangName();

        // Клиентский JAR
        metadata.clientDownload().ifPresent(dl -> {
            Path jar = gameDir.clientJar(metadata.id());
            checkFile(jar, dl.sha1().orElse(null), "client JAR", problems);
        });

        // Библиотеки + нативные JAR под текущую ОС
        for (Library lib : metadata.libraries()) {
            lib.artifact().ifPresent(artifact -> {
                Path libPath = resolveLibraryPath(gameDir, artifact);
                checkFile(libPath, artifact.sha1().orElse(null),
                        "library " + lib.name(), problems);
            });

            lib.nativeDownload(osName).ifPresent(dl -> {
                Path nativePath = resolveLibraryPath(gameDir, dl);
                checkFile(nativePath, dl.sha1().orElse(null),
                        "native " + lib.name() + " (" + osName + ")", problems);
            });
        }

        // Файл asset-индекса
        metadata.assetIndex().ifPresent(ai -> {
            Path indexFile = gameDir.assetIndexFile(ai.id());
            checkFile(indexFile, ai.sha1().orElse(null),
                    "asset index " + ai.id(), problems);
        });

        return problems;
    }

    private void checkFile(Path path, String expectedSha1, String description,
                           List<String> problems) {
        if (!Files.isRegularFile(path)) {
            problems.add(description + " (" + path + ")");
            return;
        }
        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            if (!checksumVerifier.verify(path, expectedSha1)) {
                problems.add(description + " (hash mismatch: " + path + ")");
            }
        }
    }

    private Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        return LibraryPaths.resolve(gameDir, dl);
    }
}
