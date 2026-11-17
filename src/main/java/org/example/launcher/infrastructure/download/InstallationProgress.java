package org.example.launcher.infrastructure.download;

/**
 * Интерфейс обратных вызовов для получения обновлений прогресса установки.
 * <p>
 * Интерфейс реализует UI для показа полосы прогресса, имени текущего файла
 * и категории в процессе установки.
 */
public interface InstallationProgress {

    /**
     * Вызывается один раз после построения списка задач, до начала загрузок.
     *
     * @param totalTasks общее число файлов для обработки
     * @param totalBytes общий ожидаемый размер загрузки в байтах (может быть приблизительным)
     */
    void onStart(int totalTasks, long totalBytes);

    /**
     * Вызывается перед обработкой файла (загрузкой или пропуском).
     *
     * @param taskIndex 0-индекс текущей задачи
     * @param task      задача, которая будет обработана
     */
    void onFileStart(int taskIndex, DownloadTask task);

    /**
     * Вызывается после обработки файла.
     *
     * @param taskIndex 0-индекс завершённой задачи
     * @param result    результат загрузки
     */
    void onFileComplete(int taskIndex, DownloadResult result);

    /**
     * Вызывается при завершении всей установки (успешно или нет).
     *
     * @param result сводный результат установки
     */
    void onComplete(InstallationResult result);

    /**
     * Пустая реализация для вызывающих, которым не нужны обновления прогресса.
     */
    InstallationProgress NONE = new InstallationProgress() {
        @Override public void onStart(int totalTasks, long totalBytes) {}
        @Override public void onFileStart(int taskIndex, DownloadTask task) {}
        @Override public void onFileComplete(int taskIndex, DownloadResult result) {}
        @Override public void onComplete(InstallationResult result) {}
    };
}
