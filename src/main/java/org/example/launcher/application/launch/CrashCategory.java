package org.example.launcher.application.launch;

/** Категория причины падения игры, распознанная по логу. */
public enum CrashCategory {
    /** Несовместимость загрузчика или модов. */
    WRONG_LOADER,
    /** Нехватка памяти. */
    OUT_OF_MEMORY,
    /** Неподходящая Java. */
    WRONG_JAVA,
    /** Причина не распознана. */
    UNKNOWN
}
