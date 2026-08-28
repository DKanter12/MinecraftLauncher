package org.example.launcher.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.launcher.install.ChecksumVerifier;
import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.NativeExtractor;
import org.example.launcher.model.DownloadInfo;
import org.example.launcher.model.GameProfile;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.JavaRuntime;
import org.example.launcher.model.LaunchArguments;
import org.example.launcher.model.LaunchResult;
import org.example.launcher.model.Library;
import org.example.launcher.model.MinecraftProcess;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.util.OsDetector;

/**
 * Default {@link MinecraftLaunchService}.
 * <p>
 * Performs pre-launch verification (file existence + SHA-1 integrity),
 * Java runtime resolution, argument building, and process management.
 * <p>
 * File verification reuses the same path-resolution logic as
 * {@link MinecraftInstaller} to ensure consistency between what was
 * installed and what is verified at launch time.
 */
public class MinecraftLauncher implements MinecraftLaunchService {

    private final LaunchArgumentBuilder argumentBuilder;
    private final JavaResolutionService javaResolutionService;
    private final ChecksumVerifier checksumVerifier;

    public MinecraftLauncher(LaunchArgumentBuilder argumentBuilder,
                             JavaResolutionService javaResolutionService,
                             ChecksumVerifier checksumVerifier) {
        this.argumentBuilder = argumentBuilder;
        this.javaResolutionService = javaResolutionService;
        this.checksumVerifier = checksumVerifier;
    }

    // ------------------------------------------------------------------
    //  Launch
    // ------------------------------------------------------------------

    @Override
    public LaunchResult launch(VersionMetadata metadata,
                               GameDirectory gameDir,
                               GameProfile profile) {

        // 1. Verify files
        List<String> missing = verifyFiles(metadata, gameDir);
        if (!missing.isEmpty()) {
            return LaunchResult.fileCheckFailed(
                    "Missing or corrupt files (" + missing.size() + "): "
                            + String.join(", ", missing));
        }

        // 2. Resolve Java
        JavaResolutionResult javaResult = javaResolutionService.resolve(metadata);
        if (!javaResult.isFound()) {
            return LaunchResult.javaNotFound(
                    javaResult.reason().orElse("No suitable Java runtime found"));
        }

        JavaRuntime javaRuntime = javaResult.runtime().orElseThrow();

        // 3. Extract natives
        try {
            NativeExtractor.extractNatives(metadata, gameDir);
        } catch (IOException e) {
            return LaunchResult.launchFailed(
                    "Failed to extract native libraries: " + e.getMessage());
        }

        // 4. Build arguments
        LaunchArguments args = argumentBuilder.build(metadata, gameDir, profile, javaRuntime);

        // 5. Start process
        try {
            ProcessBuilder pb = new ProcessBuilder(args.fullCommand());
            pb.directory(args.workingDirectory().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            MinecraftProcess mcProcess = new MinecraftProcess(process);

            // Brief delay to catch immediate crashes (e.g. wrong Java version,
            // missing main class) so we can return a useful error
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!mcProcess.isAlive() && mcProcess.exitCode() != 0) {
                String err = mcProcess.stderr();
                if (err.isBlank()) {
                    err = mcProcess.stdout();
                }
                return LaunchResult.launchFailed(
                        "Process exited immediately (code " + mcProcess.exitCode() + ")"
                                + (err.isBlank() ? "" : ": " + err.lines()
                                        .limit(10).reduce("", (a, b) -> a + b + "\n")));
            }

            return LaunchResult.success(mcProcess);

        } catch (IOException e) {
            return LaunchResult.launchFailed(
                    "Failed to start Java process: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  File verification
    // ------------------------------------------------------------------

    /**
     * Verifies that all files required by the version metadata exist
     * locally and (where a SHA-1 hash is known) match the expected
     * checksum.
     *
     * @return a list of human-readable descriptions of missing/corrupt
     *         files; empty if everything is OK
     */
    public List<String> verifyFiles(VersionMetadata metadata, GameDirectory gameDir) {
        List<String> problems = new ArrayList<>();
        String osName = OsDetector.mojangName();

        // Client JAR
        metadata.clientDownload().ifPresent(dl -> {
            Path jar = gameDir.clientJar(metadata.id());
            checkFile(jar, dl.sha1().orElse(null), "client JAR", problems);
        });

        // Libraries + native JARs for current OS
        for (Library lib : metadata.libraries()) {
            lib.artifact().ifPresent(artifact -> {
                Path libPath = resolveLibraryPath(gameDir, artifact);
                checkFile(libPath, artifact.sha1().orElse(null),
                        "library " + lib.name(), problems);
            });

            var nativeDl = lib.nativeDownload(osName);
            if (nativeDl.isPresent()) {
                DownloadInfo dl = nativeDl.get();
                Path nativePath = resolveLibraryPath(gameDir, dl);
                checkFile(nativePath, dl.sha1().orElse(null),
                        "native " + lib.name() + " (" + osName + ")", problems);
            }
        }

        // Asset index file
        metadata.assetIndex().ifPresent(ai -> {
            Path indexFile = gameDir.assetIndexFile(ai.id());
            checkFile(indexFile, ai.sha1().orElse(null),
                    "asset index " + ai.id(), problems);
        });

        return problems;
    }

    private void checkFile(Path path, String expectedSha1, String description,
                           List<String> problems) {
        if (!Files.isRegularFile(path)) {
            problems.add(description + " (" + path + ")");
            return;
        }
        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            if (!checksumVerifier.verify(path, expectedSha1)) {
                problems.add(description + " (hash mismatch: " + path + ")");
            }
        }
    }

    private Path resolveLibraryPath(GameDirectory gameDir, DownloadInfo dl) {
        Optional<String> pathOpt = dl.path();
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
