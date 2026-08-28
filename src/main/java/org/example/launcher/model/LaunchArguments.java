package org.example.launcher.model;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fully resolved command-line arguments for launching Minecraft.
 * <p>
 * Produced by a {@code LaunchArgumentBuilder} from version metadata,
 * game directory layout, player profile, and resolved Java runtime.
 * Consumed by the launch service to start the game process.
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
     * Returns the complete command-line token list:
     * {@code java [jvmArgs] [mainClass] [gameArgs]}.
     * <p>
     * Note: {@code -cp} and the classpath value are already part of
     * {@link #jvmArguments()} for modern versions, or injected by the
     * builder for legacy versions.
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
