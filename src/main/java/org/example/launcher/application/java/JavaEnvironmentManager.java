package org.example.launcher.application.java;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.domain.model.JavaFindResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.java.DefaultJavaFinder;
import org.example.launcher.infrastructure.java.JavaFinder;
import org.example.launcher.infrastructure.java.JavaInstaller;

/**
 * Верх подсистемы Java: найти → подобрать → поставить.
 * Политика в одном месте: сначала ищет подходящую среди установленных,
 * ставит только если не нашла. Выбор пользователя (override пути)
 * применяется сам, а не молча переопределяется.
 */
public class JavaEnvironmentManager {

    private final JavaFinder finder;
    private final JavaInstaller installer;

    public JavaEnvironmentManager(JavaFinder finder,
                                   JavaInstaller installer) {
        this.finder = Objects.requireNonNull(finder, "finder");
        this.installer = Objects.requireNonNull(installer, "installer");
    }

    /**
     * Подбирает Java под метаданные версии.
     *
     * @return найденный рантайм либо пусто
     */
    public Optional<JavaRuntime> find(VersionMetadata metadata) {
        JavaFindResult result = finder.find(metadata);
        return result.runtime();
    }

    /**
     * Возвращает подходящий рантайм, при отсутствии ставит через установщик.
     *
     * @param requiredMajor минимальная мажорная версия
     * @param targetDir     каталог для управляемой установки
     */
    public JavaRuntime ensureInstalled(int requiredMajor, Path targetDir)
            throws Exception {
        JavaFindResult result = finder.find(requiredMajor);
        if (result.isFound() && result.runtime().isPresent()) {
            return result.runtime().get();
        }
        return installer.install(requiredMajor, targetDir);
    }

    /**
     * Делает только что поставленный рантайм используемым по умолчанию.
     * Работает только с резолвером по умолчанию, чужой выбор не трогает.
     */
    public void adoptManaged(JavaRuntime runtime) {
        if (finder instanceof DefaultJavaFinder svc) {
            svc.setCustomJavaPath(runtime.javaExecutable());
        }
    }
}
