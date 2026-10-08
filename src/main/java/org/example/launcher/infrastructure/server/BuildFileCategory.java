package org.example.launcher.infrastructure.server;

/**
 * Разделяет разные виды файлов, из которых состоит распространяемая сборка.
 * Категория решает, куда попадёт файл внутри папки сборки инстанса,
 * что строго разделяет информацию о сборке, файлы Minecraft,
 * моды, конфиги и дополнительные ресурсы:
 *
 * <ul>
 *   <li>{@code MINECRAFT} — сторона Minecraft в сборке (версия и загрузчик
 *       для её запуска). Это описательные
 *       метаданные из {@link BuildSummary}; отдельные файловые записи
 *       этой категории в папку сборки не скачиваются.</li>
 *   <li>{@code MODS} — jar-файлы модов, устанавливаются в {@code mods/}.</li>
 *   <li>{@code CONFIGS} — файлы конфигурации, устанавливаются в
 *       {@code config/}.</li>
 *   <li>{@code RESOURCES} — всё остальное (пакеты ресурсов, шейдеры,
 *       дополнительные файлы), устанавливается в {@code resources/}.</li>
 * </ul>
 */
public enum BuildFileCategory {
    MINECRAFT("minecraft"),
    MODS("mods"),
    CONFIGS("config"),
    RESOURCES("resources");

    private final String folder;

    BuildFileCategory(String folder) {
        this.folder = folder;
    }

    /** @return имя папки внутри каталога сборки, к которой относится категория. */
    public String folder() {
        return folder;
    }
}
