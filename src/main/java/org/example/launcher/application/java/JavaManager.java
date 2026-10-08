package org.example.launcher.application.java;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.java.DefaultJavaResolutionService;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;

/**
 * Верх подсистемы Java: найти → подобрать → поставить.
 * Политика в одном месте: сначала ищет подходящую среди установленных,
 * ставит только если не нашла. Выбор пользователя (override пути)
 * применяется сам, а не молча переопределяется.
 */
public class JavaManager {

    private final JavaResolutionService resolutionService;
    private final JavaRuntimeInstaller installer;

    public JavaManager(JavaResolutionService resolutionService,
                       JavaRuntimeInstaller installer) {
        this.resolutionService = Objects.requireNonNull(resolutionService, "resolutionService");
        this.installer = Objects.requireNonNull(installer, "installer");
    }

    /**
     * Подбирает Java под метаданные версии.
     *
     * @return найденный рантайм либо пусто
     */
    public Optional<JavaRuntime> resolve(VersionMetadata metadata) {
        JavaResolutionResult result = resolutionService.resolve(metadata);
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
        JavaResolutionResult result = resolutionService.resolve(requiredMajor);
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
        if (resolutionService instanceof DefaultJavaResolutionService svc) {
            svc.setCustomJavaPath(runtime.javaExecutable());
        }
    }
}
