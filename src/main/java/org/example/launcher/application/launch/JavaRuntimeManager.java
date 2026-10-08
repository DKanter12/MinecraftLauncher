package org.example.launcher.application.launch;

import java.nio.file.Path;
import java.util.Objects;

import org.example.launcher.application.java.JavaManager;
import org.example.launcher.domain.ErrorCode;
import org.example.launcher.domain.JavaException;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;

/**
 * Выбор Java, с которой запускается конкретная версия.
 * Правила совместимости — только здесь, а не разбросаны по запуску.
 */
public class JavaRuntimeManager {

    private final JavaManager javaManager;

    public JavaRuntimeManager(JavaManager javaManager) {
        this.javaManager = Objects.requireNonNull(javaManager, "javaManager");
    }

    /**
     * Определяет необходимую Java для версии и возвращает рантайм.
     *
     * @throws JavaException с кодом {@code JAVA_NOT_FOUND}, если подходящей нет
     */
    public JavaRuntime getJavaFor(VersionMetadata metadata)
            throws JavaException {
        Objects.requireNonNull(metadata, "metadata");
        return javaManager.resolve(metadata)
                .orElseThrow(() -> new JavaException(ErrorCode.JAVA_NOT_FOUND,
                        "No suitable Java runtime found for "
                                + metadata.id()));
    }

    /**
     * Проверяет совместимость рантайма с версией.
     * Legacy-версии (требование 8 и старше) запускаются только на точном
     * совпадении мажорной версии, остальные — на равной или новее.
     */
    public boolean isCompatible(JavaRuntime runtime, VersionMetadata metadata) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(metadata, "metadata");
        int required = metadata.javaVersion()
                .map(JavaVersion::majorVersion)
                .orElse(8);
        if (required <= 8) {
            return runtime.majorVersion() == required;
        }
        return runtime.majorVersion() >= required;
    }

    /** Путь к исполняемому файлу подходящей Java. */
    public Path getJavaExecutableFor(VersionMetadata metadata)
            throws JavaException {
        return getJavaFor(metadata).javaExecutable();
    }
}
