package org.example.launcher.infrastructure.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.example.launcher.domain.model.JavaFindResult;
import org.example.launcher.domain.model.JavaRuntime;
import org.example.launcher.domain.model.JavaVersion;
import org.example.launcher.domain.model.VersionMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.example.launcher.infrastructure.java.DefaultJavaFinder;
import org.example.launcher.infrastructure.java.JavaScanner;

@DisplayName("DefaultJavaFinder")
class DefaultJavaFinderTest {

    private static final Path DUMMY_EXE = Path.of("/usr/bin/java");

    private JavaRuntime rt(int major, JavaRuntime.Source source) {
        return new JavaRuntime(DUMMY_EXE.resolveSibling("java" + major), major, source);
    }

    private JavaScanner scannerWith(JavaRuntime... runtimes) {
        return new JavaScanner() {
            @Override
            public List<JavaRuntime> scanInstalledRuntimes() {
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
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(17, JavaRuntime.Source.JAVA_HOME)));

        JavaFindResult result = svc.find(17);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("returns FOUND when a higher version runtime exists")
    void foundWhenHigherVersion() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(21, JavaRuntime.Source.PATH)));

        JavaFindResult result = svc.find(17);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("returns INCOMPATIBLE when all runtimes are too old")
    void incompatibleWhenTooOld() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(8, JavaRuntime.Source.PATH)));

        JavaFindResult result = svc.find(17);

        assertEquals(JavaFindResult.Status.INCOMPATIBLE, result.status());
        assertTrue(result.reason().isPresent());
        assertTrue(result.reason().get().contains("17"));
        assertTrue(result.reason().get().contains("8"));
    }

    @Test
    @DisplayName("returns NOT_FOUND when no runtimes detected")
    void notFoundWhenEmpty() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith());

        JavaFindResult result = svc.find(8);

        assertEquals(JavaFindResult.Status.NOT_FOUND, result.status());
        assertTrue(result.reason().isPresent());
    }

    @Test
    @DisplayName("picks the lowest compatible runtime (closest match)")
    void picksLowestCompatible() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(
                        rt(21, JavaRuntime.Source.PATH),
                        rt(17, JavaRuntime.Source.JAVA_HOME),
                        rt(11, JavaRuntime.Source.COMMON_LOCATION)));

        JavaFindResult result = svc.find(17);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("find(VersionMetadata) uses metadata javaVersion when present")
    void findsFromMetadata() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(21, JavaRuntime.Source.PATH)));

        VersionMetadata meta = new VersionMetadata(
                "1.21", "release", "net.minecraft.client.main.Main",
                "21", null,
                new JavaVersion("java-runtime-gamma", 21),
                null, List.of(), List.of(), List.of(), null);

        JavaFindResult result = svc.find(meta);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("find(VersionMetadata) uses fallback major when javaVersion absent")
    void usesFallbackWhenNoJavaVersion() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(8, JavaRuntime.Source.PATH)));

        VersionMetadata meta = new VersionMetadata(
                "1.7.10", "release", "net.minecraft.client.main.Main",
                null, null, null,
                null, List.of(), List.of(), List.of(), "--username");

        JavaFindResult result = svc.find(meta);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("custom fallback major is respected")
    void customFallback() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(rt(17, JavaRuntime.Source.PATH)), 17);

        VersionMetadata meta = new VersionMetadata(
                "legacy", "release", "Main", null, null, null,
                null, List.of(), List.of(), List.of(), null);

        JavaFindResult result = svc.find(meta);

        assertTrue(result.isFound());
        assertEquals(17, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("find(int) works independently of metadata")
    void findByInt() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(
                        rt(17, JavaRuntime.Source.JAVA_HOME),
                        rt(21, JavaRuntime.Source.PATH)));

        JavaFindResult result = svc.find(21);

        assertTrue(result.isFound());
        assertEquals(21, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("Java 8 requirement uses exact match (not 9+)")
    void java8ExactMatch() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(
                        rt(19, JavaRuntime.Source.JAVA_HOME),
                        rt(8, JavaRuntime.Source.COMMON_LOCATION)));

        JavaFindResult result = svc.find(8);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
    }

    @Test
    @DisplayName("Java 8 requirement rejects Java 9+ runtimes")
    void java8RejectsNewer() {
        DefaultJavaFinder svc = new DefaultJavaFinder(
                scannerWith(
                        rt(19, JavaRuntime.Source.JAVA_HOME),
                        rt(11, JavaRuntime.Source.PATH)));

        JavaFindResult result = svc.find(8);

        assertEquals(JavaFindResult.Status.INCOMPATIBLE, result.status());
        assertTrue(result.reason().orElse("").contains("Java 8"));
        assertTrue(result.reason().orElse("").contains("launchwrapper"));
    }

    @Test
    @DisplayName("custom Java path is included in resolution")
    void customJavaPathIncluded() {
        JavaRuntime customRt = new JavaRuntime(
                Path.of("/opt/java8/bin/java"), 8, JavaRuntime.Source.CUSTOM);
        JavaScanner scanner = new JavaScanner() {
            @Override
            public List<JavaRuntime> scanInstalledRuntimes() {
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

        DefaultJavaFinder svc = new DefaultJavaFinder(scanner);
        svc.setCustomJavaPath(Path.of("/opt/java8/bin/java"));

        JavaFindResult result = svc.find(8);

        assertTrue(result.isFound());
        assertEquals(8, result.runtime().orElseThrow().majorVersion());
        assertEquals(JavaRuntime.Source.CUSTOM, result.runtime().orElseThrow().source());
    }
}
