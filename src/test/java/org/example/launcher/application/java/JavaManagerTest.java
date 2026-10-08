package org.example.launcher.application.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;

@DisplayName("JavaManager")
class JavaManagerTest {

    private static JavaRuntime runtime() {
        return new JavaRuntime(Path.of("/java/bin/java"), 17,
                JavaRuntime.Source.CUSTOM);
    }

    private static JavaResolutionService foundService() {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata metadata) {
                return JavaResolutionResult.found(runtime());
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return JavaResolutionResult.found(runtime());
            }
        };
    }

    private static JavaResolutionService missingService() {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata metadata) {
                return JavaResolutionResult.notFound("stub");
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return JavaResolutionResult.notFound("stub");
            }
        };
    }

    private static final class RecordingInstaller implements JavaRuntimeInstaller {
        int calls;

        @Override
        public JavaRuntime install(String component, Path targetDir) {
            calls++;
            return runtime();
        }

        @Override
        public JavaRuntime install(int requiredMajor, Path targetDir) {
            calls++;
            return runtime();
        }
    }

    @Test
    @DisplayName("ensureInstalled returns existing runtime without installing")
    void returnsExisting(@TempDir Path dir) throws Exception {
        var installer = new RecordingInstaller();
        var manager = new JavaManager(foundService(), installer);

        assertEquals(runtime().javaExecutable(),
                manager.ensureInstalled(17, dir).javaExecutable());
        assertEquals(0, installer.calls);
    }

    @Test
    @DisplayName("ensureInstalled installs when nothing found")
    void installsWhenMissing(@TempDir Path dir) throws Exception {
        var installer = new RecordingInstaller();
        var manager = new JavaManager(missingService(), installer);

        JavaRuntime installed = manager.ensureInstalled(8, dir);

        assertEquals(1, installer.calls);
        assertEquals(17, installed.majorVersion());
    }

    @Test
    @DisplayName("adoptManaged is a no-op for foreign resolution services")
    void adoptForeignNoOp() {
        var manager = new JavaManager(missingService(), new RecordingInstaller());

        manager.adoptManaged(runtime());
    }

    @Test
    @DisplayName("resolve delegates to the resolution service")
    void delegatesResolve() {
        var manager = new JavaManager(foundService(), new RecordingInstaller());

        assertTrue(manager.resolve(null).isPresent());
    }
}
