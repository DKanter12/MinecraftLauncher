package org.example.launcher.application.launch;

/** Стадия запуска для строки статуса интерфейса. */
public enum LaunchStage {
    /** Проверка файлов сборки. */
    CHECKING,
    /** Докачка отсутствующих/битых файлов. */
    REPAIRING,
    /** Установка подходящей Java. */
    INSTALLING_JAVA,
    /** Построение команды и старт процесса. */
    STARTING,
    /** Игра выполняется. */
    RUNNING
}
