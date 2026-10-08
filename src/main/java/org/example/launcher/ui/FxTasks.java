package org.example.launcher.ui;

import javafx.concurrent.Task;

/**
 * Единое место запуска фоновых задач: daemon-поток с именем.
 * Раньше в {@code MainView} было 11 одинаковых троек
 * {@code new Thread / setDaemon / start} — теперь одна.
 */
public final class FxTasks {

    private FxTasks() {
    }

    /** Запускает JavaFX-{@code Task} в именованном daemon-потоке. */
    public static Thread run(String name, Task<?> task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** Запускает произвольную работу в именованном daemon-потоке. */
    public static Thread run(String name, Runnable work) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
