package org.example.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.JavaVersion;
import org.example.launcher.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DefaultJavaResolutionService")
class DefaultJavaResolutionServiceTest {

    private static final Path DUMMY_EXE = Path.of("/usr/bin/java");

    private JavaRuntime rt(int major, JavaRuntime.Source source) {
        return new JavaRuntime(DUMMY_EXE.resolveSibling("java" + major), major, source);
    }

    private JavaDetector detectorWith(JavaRuntime... runtimes) {
        return new JavaDetector() {
            @Override
            public List<JavaRuntime> detectInstalledRuntimes() {
                return List.of(runtimes);
            }

            @Override
            public JavaRuntime detectFromPath(Path homeDir, JavaRuntime.Source source) {
                return null;
            }
        };
    }

    @Test
    @DisplayName("returns FOUND when a compatible runtime exists")
    void foundWhenCompatible() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(17, JavaRuntime.Source.JAVA_HOME)));

        JavaResolutionResult result = svc.resolve(17);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("returns FOUND when a higher version runtime exists")
    void foundWhenHigherVersion() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(21, JavaRuntime.Source.PATH)));

        JavaResolutionResult result = svc.resolve(17);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("returns INCOMPATIBLE when all runtimes are too old")
    void incompatibleWhenTooOld() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(8, JavaRuntime.Source.PATH)));

        JavaResolutionResult result = svc.resolve(17);

        assertEquals(JavaResolutionResult.Status.INCOMPATIBLE, result.status());
        assertTrue(result.reason().isPresent());
        assertTrue(result.reason().get().contains("17"));
        assertTrue(result.reason().get().contains("8"));
    }

    @Test
    @DisplayName("returns NOT_FOUND when no runtimes detected")
    void notFoundWhenEmpty() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith());

        JavaResolutionResult result = svc.resolve(8);

        assertEquals(JavaResolutionResult.Status.NOT_FOUND, result.status());
        assertTrue(result.reason().isPresent());
    }

    @Test
    @DisplayName("picks the lowest compatible runtime (closest match)")
    void picksLowestCompatible() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(
                        rt(21, JavaRuntime.Source.PATH),
                        rt(17, JavaRuntime.Source.JAVA_HOME),
                        rt(11, JavaRuntime.Source.COMMON_LOCATION)));

        JavaResolutionResult result = svc.resolve(17);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("resolve(VersionMetadata) uses metadata javaVersion when present")
    void resolvesFromMetadata() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(21, JavaRuntime.Source.PATH)));

        VersionMetadata meta = new VersionMetadata(
                "1.21", "release", "net.minecraft.client.main.Main",
                "21", null,
                new JavaVersion("java-runtime-gamma", 21),
                null, List.of(), List.of(), List.of(), null);

        JavaResolutionResult result = svc.resolve(meta);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("resolve(VersionMetadata) uses fallback major when javaVersion absent")
    void usesFallbackWhenNoJavaVersion() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(8, JavaRuntime.Source.PATH)));

        VersionMetadata meta = new VersionMetadata(
                "1.7.10", "release", "net.minecraft.client.main.Main",
                null, null, null,
                null, List.of(), List.of(), List.of(), "--username");

        JavaResolutionResult result = svc.resolve(meta);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("custom fallback major is respected")
    void customFallback() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(rt(17, JavaRuntime.Source.PATH)), 17);

        VersionMetadata meta = new VersionMetadata(
                "legacy", "release", "Main", null, null, null,
                null, List.of(), List.of(), List.of(), null);

        JavaResolutionResult result = svc.resolve(meta);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("resolve(int) works independently of metadata")
    void resolveByInt() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(
                        rt(17, JavaRuntime.Source.JAVA_HOME),
                        rt(21, JavaRuntime.Source.PATH)));

        JavaResolutionResult result = svc.resolve(21);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("Java 8 requirement uses exact match (not 9+)")
    void java8ExactMatch() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(
                        rt(19, JavaRuntime.Source.JAVA_HOME),
                        rt(8, JavaRuntime.Source.COMMON_LOCATION)));

        JavaResolutionResult result = svc.resolve(8);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("Java 8 requirement rejects Java 9+ runtimes")
    void java8RejectsNewer() {
        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(
                detectorWith(
                        rt(19, JavaRuntime.Source.JAVA_HOME),
                        rt(11, JavaRuntime.Source.PATH)));

        JavaResolutionResult result = svc.resolve(8);

        assertEquals(JavaResolutionResult.Status.INCOMPATIBLE, result.status());
        assertTrue(result.reason().orElse("").contains("Java 8"));
        assertTrue(result.reason().orElse("").contains("launchwrapper"));
    }

    @Test
    @DisplayName("custom Java path is included in resolution")
    void customJavaPathIncluded() {
        JavaRuntime customRt = new JavaRuntime(
                Path.of("/opt/java8/bin/java"), 8, JavaRuntime.Source.CUSTOM);
        JavaDetector detector = new JavaDetector() {
            @Override
            public List<JavaRuntime> detectInstalledRuntimes() {
                return List.of(rt(19, JavaRuntime.Source.JAVA_HOME));
            }

            @Override
            public JavaRuntime detectFromPath(Path homeDir, JavaRuntime.Source source) {
                if (homeDir.equals(Path.of("/opt/java8"))) {
                    return customRt;
                }
                return null;
            }
        };

        DefaultJavaResolutionService svc = new DefaultJavaResolutionService(detector);
        svc.setCustomJavaPath(Path.of("/opt/java8/bin/java"));

        JavaResolutionResult result = svc.resolve(8);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
        assertEquals(JavaRuntime.Source.CUSTOM, result.runtime().orElseThrow().source());
    }
}
