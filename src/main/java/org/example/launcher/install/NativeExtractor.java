package org.example.launcher.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.example.launcher.model.Library;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.util.OsDetector;

/**
 * Extracts native libraries from their JAR containers into the
 * version-specific natives directory.
 * <p>
 * Minecraft requires native libraries (.dll, .so, .dylib) to be
 * present as individual files in the directory pointed to by
 * {@code -Djava.library.path}. The native JARs are downloaded by
 * the installer into the shared libraries directory; this class
 * extracts their contents before launch.
 * <p>
 * META-INF entries are skipped to avoid signature file conflicts.
 */
public final class NativeExtractor {

    private NativeExtractor() {
    }

    /**
     * Extracts all native libraries for the current OS from their
     * JAR containers into {@code gameDir.nativeDir(versionId)}.
     *
     * @param metadata  the version metadata
     * @param gameDir   the game directory layout
     * @return the number of files extracted
     * @throws IOException if extraction fails
     */
    public static int extractNatives(VersionMetadata metadata, GameDirectory gameDir)
            throws IOException {
        Path nativesDir = gameDir.nativeDir(metadata.id());
        Files.createDirectories(nativesDir);

        String osName = OsDetector.mojangName();
        int extracted = 0;

        for (Library lib : metadata.libraries()) {
            var nativeDl = lib.nativeDownload(osName);
            if (nativeDl.isEmpty()) continue;

            Path jarPath = resolveNativeJarPath(gameDir, nativeDl.get());
            if (!Files.isRegularFile(jarPath)) continue;

            extracted += extractJar(jarPath, nativesDir);
        }

        return extracted;
    }

    private static int extractJar(Path jarPath, Path targetDir) throws IOException {
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                if (name.startsWith("META-INF/")) continue;

                Path target = targetDir.resolve(name);
                if (Files.isRegularFile(target)) continue;

                Files.createDirectories(target.getParent());
                try (InputStream is = jar.getInputStream(entry)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                count++;
            }
        }
        return count;
    }

    private static Path resolveNativeJarPath(GameDirectory gameDir,
                                             org.example.launcher.model.DownloadInfo dl) {
        var pathOpt = dl.path();
        if (pathOpt.isPresent()) {
            return gameDir.library(pathOpt.get());
        }
        String url = dl.url();
        if (url != null && !url.isBlank()) {
            int idx = url.lastIndexOf('/');
            String fileName = (idx >= 0) ? url.substring(idx + 1) : url;
            return gameDir.librariesDir().resolve(fileName);
        }
        return gameDir.librariesDir().resolve("unknown.jar");
    }
}
