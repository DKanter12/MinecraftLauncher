package org.example.launcher.application.launch;

/** Следующий шаг запуска после неуспешной проверки сборки. */
public enum LaunchDecision {
    /** Поставить Java 8 (legacy, не хватает только рантайма). */
    INSTALL_JAVA8,
    /** Починить докачкой и перепроверить. */
    REPAIR,
    /** Показать ошибки, чинить нечего. */
    FAIL
}
