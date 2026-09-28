package org.example.launcher.util;

import java.nio.file.Path;
import java.util.Objects;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.DownloadInfo;

/**
 * Единое место разрешения артефакта библиотеки в локальный путь.
 * <p>
 * Хранится в одном классе, чтобы установщик, проверщик и построитель аргументов
 * никогда не расходились во мнении о месте библиотеки на диске.
 */
public final class LibraryPaths {

    /** Запасное имя файла, если ни путь, ни URL не дают имени. */
    public static final String UNKNOWN_JAR = "unknown.jar";

    private LibraryPaths() {
    }

    /**
     * Разрешает локальный путь загрузки библиотеки.
     *
     * @param gameDir корень хранилища (не должен быть {@code null})
     * @param download координаты библиотеки (не должны быть {@code null})
     * @return локальный путь файла внутри {@code libraries/}
     */
    public static Path resolve(GameDirectory gameDir, DownloadInfo download) {
        Objects.requireNonNull(gameDir, "gameDir");
        Objects.requireNonNull(download, "download");

        if (download.path().isPresent()) {
            return gameDir.library(download.path().get());
        }
        String url = download.url();
        if (url != null && !url.isBlank()) {
            int idx = url.lastIndexOf('/');
            String fileName = idx >= 0 ? url.substring(idx + 1) : url;
            if (!fileName.isBlank()) {
                return gameDir.librariesDir().resolve(fileName);
            }
        }
        return gameDir.librariesDir().resolve(UNKNOWN_JAR);
    }
}
