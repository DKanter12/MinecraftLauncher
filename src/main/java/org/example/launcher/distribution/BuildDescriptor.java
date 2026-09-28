package org.example.launcher.distribution;

import java.util.List;

/**
 * Полное описание распространяемой сборки: краткое описание плюс
 * полный список файлов, делающий сборку воспроизводимой на любой
 * машине. Это то, что лаунчер скачивает с сервера и
 * хранит как манифест {@code build.json} установленной
 * сборки.
 *
 * @param summary краткое описание: идентичность и описание сборки
 * @param files   каждый файл сборки, с хешами SHA-1
 * @param origin  откуда пришла сборка (SERVER для скачанных сборок)
 */
public record BuildDescriptor(
        BuildSummary summary,
        List<BuildFileEntry> files,
        BuildOrigin origin) {

    public BuildDescriptor {
        if (summary == null) {
            throw new IllegalArgumentException("summary must not be null");
        }
        if (files == null) {
            files = List.of();
        } else {
            files = List.copyOf(files);
        }
        if (origin == null) {
            origin = BuildOrigin.SERVER;
        }
    }

    public String id() {
        return summary.id();
    }

    public String version() {
        return summary.version();
    }

    /** @return true, если этот манифест описывает тот же id сборки, что и {@code other}. */
    public boolean sameBuild(BuildDescriptor other) {
        return other != null && id().equals(other.id());
    }

    /** @return отображаемая форма для диалогов. */
    @Override
    public String toString() {
        return summary.summaryLine();
    }
}
