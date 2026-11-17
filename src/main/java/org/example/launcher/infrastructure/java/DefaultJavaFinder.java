package org.example.launcher.infrastructure.java;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.example.launcher.domain.model.JavaFindResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Реализация {@link JavaFinder} по умолчанию.
 * <p>
 * Делегирует поиск рантаймов {@link JavaScanner} и выбирает
 * рантайм с минимальной major-версией, которая ещё удовлетворяет
 * требованию (чтобы не запускать игру на излишне новом JDK).
 * <p>
 * Если метаданные версии не содержат {@link JavaVersion},
 * используется настраиваемая запасная major-версия (по умолчанию: 8,
 * т.к. старые версии Minecraft рассчитаны на Java 8).
 * <p>
 * Пользовательский путь к Java задаётся через
 * {@link #setCustomJavaPath(Path)}; при установке сканер проверяет
 * этот путь, и найденный рантайм включается в список кандидатов
 * наряду с системными.
 */
public class DefaultJavaFinder implements JavaFinder {

    private final JavaScanner scanner;
    private final int fallbackMajor;
    private Path customJavaPath;

    public DefaultJavaFinder(JavaScanner scanner) {
        this(scanner, 8);
    }

    public DefaultJavaFinder(JavaScanner scanner, int fallbackMajor) {
        this.scanner = scanner;
        this.fallbackMajor = fallbackMajor;
    }

    /**
     * Задаёт пользовательский путь к исполняемому файлу Java для
     * учёта в будущих вызовах поиска. {@code null} сбрасывает настройку.
     *
     * @param javaExe путь к исполняемому файлу {@code java}/{@code java.exe}
     */
    public void setCustomJavaPath(Path javaExe) {
        this.customJavaPath = javaExe;
    }

    @Override
    public JavaFindResult find(VersionMetadata metadata) {
        int required = metadata.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(fallbackMajor);
        return find(required);
    }

    @Override
    public JavaFindResult find(int requiredMajor) {
        List<JavaRuntime> runtimes = new ArrayList<>(scanner.scanInstalledRuntimes());

        // Добавить пользовательский путь к Java, если задан
        if (customJavaPath != null) {
            JavaRuntime custom = scanner.detectFromPath(
                    customJavaPath.getParent().getParent(),
                    JavaRuntime.Source.CUSTOM);
            if (custom != null) {
                runtimes.add(custom);
            }
        }

        if (runtimes.isEmpty()) {
            return JavaFindResult.notFound(
                    "No Java runtime was found on this system.");
        }

        // Старые версии Minecraft (beta, alpha, 1.6–1.12) используют
        // net.minecraft.launchwrapper.Launch, который приводит системный
        // ClassLoader к URLClassLoader — это приведение падает на Java 9+.
        // Для таких версий (majorVersion <= 8) нужен ровно Java 8,
        // а не просто «8 или выше».
        boolean exactMatch = requiredMajor <= 8;

        var compatible = runtimes.stream()
                .filter(rt -> exactMatch
                        ? rt.majorVersion() == requiredMajor
                        : rt.satisfies(requiredMajor))
                .sorted(Comparator.comparingInt(JavaRuntime::majorVersion))
                .toList();

        if (compatible.isEmpty()) {
            if (exactMatch) {
                int bestAvailable = runtimes.stream()
                        .mapToInt(JavaRuntime::majorVersion)
                        .max().orElse(0);
                return JavaFindResult.incompatible(
                        "This version requires Java " + requiredMajor
                        + " exactly (launchwrapper is incompatible with Java 9+)."
                        + " The best available runtime is Java " + bestAvailable
                        + ". Please install Java 8 (e.g. from adoptium.net).");
            }
            int bestAvailable = runtimes.stream()
                    .mapToInt(JavaRuntime::majorVersion)
                    .max().orElse(0);
            return JavaFindResult.incompatible(
                    "Java " + requiredMajor + "+ is required, but the best "
                    + "available runtime is Java " + bestAvailable + ".");
        }

        return JavaFindResult.found(compatible.get(0));
    }
}
