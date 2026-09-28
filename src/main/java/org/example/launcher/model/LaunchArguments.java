package org.example.launcher.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Полностью разрешённые аргументы командной строки для запуска Minecraft.
 * <p>
 * Создаются построителем {@code LaunchArgumentBuilder} из метаданных версии,
 * раскладки игрового каталога, профиля игрока и разрешённой среды Java.
 * Используются службой запуска для старта игрового процесса.
 */
public final class LaunchArguments {

    private final Path javaExecutable;
    private final List<String> jvmArguments;
    private final String classpath;
    private final String mainClass;
    private final List<String> gameArguments;
    private final Path workingDirectory;

    public LaunchArguments(Path javaExecutable,
                           List<String> jvmArguments,
                           String classpath,
                           String mainClass,
                           List<String> gameArguments,
                           Path workingDirectory) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable);
        this.jvmArguments = List.copyOf(jvmArguments);
        this.classpath = Objects.requireNonNull(classpath);
        this.mainClass = Objects.requireNonNull(mainClass);
        this.gameArguments = List.copyOf(gameArguments);
        this.workingDirectory = Objects.requireNonNull(workingDirectory);
    }

    public Path javaExecutable() {
        return javaExecutable;
    }

    public List<String> jvmArguments() {
        return Collections.unmodifiableList(jvmArguments);
    }

    public String classpath() {
        return classpath;
    }

    public String mainClass() {
        return mainClass;
    }

    public List<String> gameArguments() {
        return Collections.unmodifiableList(gameArguments);
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    /**
     * Возвращает полный список токенов командной строки:
     * {@code java [jvmArgs] [mainClass] [gameArgs]}.
     * <p>
     * Примечание: {@code -cp} и значение classpath уже входят в
     * {@link #jvmArguments()} для современных версий либо подставляются
     * построителем для устаревших версий.
     */
    public List<String> fullCommand() {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaExecutable.toString());
        cmd.addAll(jvmArguments);
        cmd.add(mainClass);
        cmd.addAll(gameArguments);
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public String toString() {
        return "LaunchArguments{java=" + javaExecutable
                + ", mainClass=" + mainClass
                + ", jvmArgs=" + jvmArguments.size()
                + ", gameArgs=" + gameArguments.size()
                + ", classpath=" + classpath.length() + " chars"
                + ", workDir=" + workingDirectory + '}';
    }
}
