package org.example.launcher.infrastructure.download;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.launcher.domain.model.DownloadInfo;
import org.example.launcher.domain.model.Library;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.common.LibraryPaths;
import org.example.launcher.infrastructure.common.OsDetector;
import org.example.launcher.infrastructure.filesystem.GameDirectory;

/**
 * Проверка целостности файлов: существование, валидность и совпадение
 * контрольной суммы. Используется и установщиком (пропуск целых),
 * и проверкой перед запуском.
 * <p>
 * Проверка файлов переиспользует ту же логику разрешения путей, что и
 * {@link MinecraftInstaller}, чтобы гарантировать соответствие между тем,
 * что было установлено, и тем, что проверяется при запуске.
 */
public class FileIntegrityChecker {

    private final ChecksumVerifier checksumVerifier;

    public FileIntegrityChecker(ChecksumVerifier checksumVerifier) {
        this.checksumVerifier =
                Objects.requireNonNull(checksumVerifier, "checksumVerifier");
    }

    /** Существует ли файл. */
    public boolean exists(Path path) {
        return path != null && Files.isRegularFile(path);
    }

    /**
     * Валиден ли файл: существует и (где известен SHA-1 хэш)
     * совпадает с ожидаемой контрольной суммой.
     * Без известного хэша достаточно существования.
     */
    public boolean isValid(Path path, String expectedSha1) {
        if (!exists(path)) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return true;
        }
        return checksumMatches(path, expectedSha1);
    }

    /** Совпадает ли SHA-1 файла с ожидаемым. */
    public boolean checksumMatches(Path path, String expectedSha1) {
        return checksumVerifier.verify(path, expectedSha1);
    }

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
        if (!exists(path)) {
            problems.add(description + " (" + path + ")");
            return;
        }
        if (!isValid(path, expectedSha1)) {
            problems.add(description + " (hash mismatch: " + path + ")");
        }
    }

    private Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        return LibraryPaths.resolve(gameDir, dl);
    }
}
