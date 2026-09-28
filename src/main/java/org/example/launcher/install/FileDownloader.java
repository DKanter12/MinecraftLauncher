package org.example.launcher.install;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Низкоуровневый порт загрузки файлов.
 * <p>
 * Отвечает только за загрузку одного файла из URL в локальный
 * путь. Вызывающий отвечает за создание каталогов и проверку
 * хэша.
 */
public interface FileDownloader {

    /**
     * Загружает файл из {@code url} в {@code targetPath}.
     * <p>
     * Родительские каталоги создаются при необходимости. Если целевой файл
     * уже существует, он перезаписывается.
     *
     * @param url        удалённый URL для загрузки
     * @param targetPath локальный файл для записи
     * @return число загруженных байтов
     * @throws IOException при любой ошибке загрузки
     */
    long download(String url, Path targetPath) throws IOException;
}
