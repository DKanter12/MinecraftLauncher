package org.example.launcher.domain.model;

import java.util.Map;
import java.util.Optional;

/**
 * Описывает одну зависимость-библиотеку, требуемую версии Minecraft.
 * <p>
 * Каждая библиотека имеет основной артефакт (JAR) и необязательный набор
 * загрузок классификаторов для нативных файлов (например, {@code natives-windows}).
 * Отображение {@code natives} связывает имя ОС с именем классификатора,
 * который следует распаковать для этой платформы.
 */
public final class Library {

    private final String name;
    private final DownloadInfo artifact;
    private final Map<String, DownloadInfo> classifiers;
    private final Map<String, String> natives;

    public Library(String name,
                   DownloadInfo artifact,
                   Map<String, DownloadInfo> classifiers,
                   Map<String, String> natives) {
        this.name = name;
        this.artifact = artifact;
        this.classifiers = classifiers != null
                ? Map.copyOf(classifiers) : Map.of();
        this.natives = natives != null
                ? Map.copyOf(natives) : Map.of();
    }

    public String name() {
        return name;
    }

    public Optional<DownloadInfo> artifact() {
        return Optional.ofNullable(artifact);
    }

    /**
     * Возвращает загрузку нативного классификатора для заданного имени ОС
     * (например, {@code "windows"}, {@code "linux"}, {@code "osx"}) либо
     * пустое значение, если у библиотеки нет нативных файлов для этой платформы.
     */
    public Optional<DownloadInfo> nativeDownload(String osName) {
        String classifier = natives.get(osName);
        if (classifier == null) return Optional.empty();
        return Optional.ofNullable(classifiers.get(classifier));
    }

    public boolean hasNatives() {
        return !natives.isEmpty();
    }
}
