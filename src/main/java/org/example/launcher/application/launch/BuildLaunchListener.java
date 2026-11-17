package org.example.launcher.application.launch;

/**
 * События запуска для интерфейса. Все вызываются из фонового потока —
 * реализация обязана сама переключаться в поток UI.
 */
public interface BuildLaunchListener {

    /** Смена стадии (строка статуса, диалог починки). */
    void onStage(LaunchStage stage);

    /** Игра завершилась чисто. */
    void onFinished();

    /** Игра упала — показать {@link CrashReport}. */
    void onCrashed(CrashReport report);

    /** Запуск невозможен — показать ошибку. */
    void onFailed(String error);
}
