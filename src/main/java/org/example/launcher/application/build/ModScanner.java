package org.example.launcher.application.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Статистика сборки: подсчёт модов и файлов.
 * Репозиторий сборки не считает — только хранит.
 */
public final class ModScanner {

    private ModScanner() {
    }

    /**
     * Считает jar-моды в {@code mods/} игрового каталога.
     *
     * @return число {@code *.jar}; {@code -1}, если каталог не читается;
     *         для ваниллы без папки тоже {@code -1}, иначе {@code 0}
     */
    public static int countMods(Path gameDir, boolean vanilla) {
        try {
            Path mods = gameDir.resolve("mods");
            if (!Files.isDirectory(mods)) {
                return vanilla ? -1 : 0;
            }
            try (var stream = Files.list(mods)) {
                return (int) stream
                        .filter(f -> f.getFileName().toString()
                                .endsWith(".jar"))
                        .count();
            }
        } catch (IOException e) {
            return -1;
        }
    }
}
