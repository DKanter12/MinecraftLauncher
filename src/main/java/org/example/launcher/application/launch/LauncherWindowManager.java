package org.example.launcher.application.launch;

/**
 * Управление главным окном лаунчера во время игры.
 * Реализация живёт в presentation (там окно), менеджер запуска
 * знает только этот интерфейс.
 */
public interface LauncherWindowManager {

    /** Скрыть окно на время игры. */
    void hide();

    /** Показать окно после игры. */
    void show();

    /** Свернуть окно. */
    void minimize();
}
