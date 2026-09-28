package org.example.launcher.service;

import java.nio.file.Path;

import org.example.launcher.model.JavaRuntime;

/**
 * Заглушка {@link JavaRuntimeInstaller}.
 * <p>
 * Всегда сообщает о неудаче. Нужна, чтобы лаунчер уже сейчас можно было
 * связать с полным пайплайном разрешения + установки Java, а реальную
 * реализацию подставить позже без изменения точек вызова.
 */
public class StubJavaRuntimeInstaller implements JavaRuntimeInstaller {

    @Override
    public JavaRuntime install(String component, Path targetDir) {
        throw new UnsupportedOperationException(
                "Automatic Java installation is not yet implemented. "
                + "Please install Java " + component + " manually.");
    }

    @Override
    public JavaRuntime install(int requiredMajor, Path targetDir) {
        throw new UnsupportedOperationException(
                "Automatic Java installation is not yet implemented. "
                + "Please install Java " + requiredMajor + "+ manually.");
    }
}
