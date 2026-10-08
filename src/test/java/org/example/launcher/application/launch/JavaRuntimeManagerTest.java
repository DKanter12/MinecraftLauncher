package org.example.launcher.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.example.launcher.application.java.JavaManager;
import org.example.launcher.domain.ErrorCode;
import org.example.launcher.domain.JavaException;
import org.example.launcher.domain.model.JavaResolutionResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.example.launcher.infrastructure.java.JavaResolutionService;
import org.example.launcher.infrastructure.java.JavaRuntimeInstaller;

@DisplayName("JavaRuntimeManager")
class JavaRuntimeManagerTest {

    private static JavaRuntime runtime(int major) {
        return new JavaRuntime(Path.of("/java/bin/java"), major,
                JavaRuntime.Source.CUSTOM);
    }

    private static JavaResolutionService service(JavaRuntime found) {
        return new JavaResolutionService() {
            @Override
            public JavaResolutionResult resolve(VersionMetadata metadata) {
                return found == null
                        ? JavaResolutionResult.notFound("stub: no java")
                        : JavaResolutionResult.found(found);
            }

            @Override
            public JavaResolutionResult resolve(int requiredMajor) {
                return resolve((VersionMetadata) null);
            }
        };
    }

    private static JavaRuntimeInstaller failingInstaller() {
        return new JavaRuntimeInstaller() {
            @Override
            public JavaRuntime install(String component, Path targetDir) {
                throw new UnsupportedOperationException("stub");
            }

            @Override
            public JavaRuntime install(int requiredMajor, Path targetDir) {
                throw new UnsupportedOperationException("stub");
            }
        };
    }

    private static VersionMetadata meta(int javaMajor) {
        return new VersionMetadata("1.21.4", "release",
                "net.minecraft.client.main.Main", null, null,
                new JavaVersion("java-runtime-gamma", javaMajor), null,
                List.of(), List.of(), List.of(), null);
    }

    @Test
    @DisplayName("getJavaFor returns the resolved runtime executable")
    void resolves() throws Exception {
        var manager = new JavaRuntimeManager(
                new JavaManager(service(runtime(17)), failingInstaller()));

        assertEquals(Path.of("/java/bin/java"),
                manager.getJavaExecutableFor(meta(17)));
    }

    @Test
    @DisplayName("getJavaFor throws JAVA_NOT_FOUND when missing")
    void missing() {
        var manager = new JavaRuntimeManager(
                new JavaManager(service(null), failingInstaller()));

        JavaException error = assertThrows(JavaException.class,
                () -> manager.getJavaFor(meta(17)));
        assertEquals(ErrorCode.JAVA_NOT_FOUND, error.code());
    }

    @Test
    @DisplayName("legacy versions need the exact major, modern accept newer")
    void compatibility() {
        var manager = new JavaRuntimeManager(
                new JavaManager(service(null), failingInstaller()));

        assertTrue(manager.isCompatible(runtime(8), meta(8)));
        assertFalse(manager.isCompatible(runtime(17), meta(8)));
        assertTrue(manager.isCompatible(runtime(17), meta(17)));
        assertTrue(manager.isCompatible(runtime(21), meta(17)));
        assertFalse(manager.isCompatible(runtime(11), meta(17)));
    }
}
