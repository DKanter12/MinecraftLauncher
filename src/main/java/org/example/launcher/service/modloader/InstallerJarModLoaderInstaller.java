package org.example.launcher.service.modloader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.example.launcher.install.FileDownloader;
import org.example.launcher.install.GameDirectory;
import org.example.launcher.install.InstallationProgress;
import org.example.launcher.install.InstallationResult;
import org.example.launcher.install.InstallationService;
import org.example.launcher.model.JavaResolutionResult;
import org.example.launcher.model.MinecraftVersion;
import org.example.launcher.model.ModLoaderVersion;
import org.example.launcher.model.VersionMetadata;
import org.example.launcher.service.JavaResolutionService;
import org.example.launcher.version.ModdedVersionType;

/**
 * {@link ModLoaderInstaller} for loaders distributed as installer JARs
 * (Forge and NeoForge), following the Forge headless-installation
 * approach: the official installer is downloaded and executed with
 * {@code --installClient <gameDir>} — fully automatic, no GUI, no
 * manual steps.
 * <p>
 * Flow:
 * <ol>
 *   <li>download the installer JAR from the loader's Maven repository
 *       into {@code installers/};</li>
 *   <li>resolve the Java runtime required by the vanilla version
 *       (the installer requires a compatible JVM);</li>
 *   <li>run {@code java -jar installer.jar --installClient <gameDir>}
 *       and wait for completion;</li>
 *   <li>locate the created {@code versions/{id}/{id}.json} launch
 *       configuration;</li>
 *   <li>merge it with vanilla metadata and run the standard
 *       {@link InstallationService} to fetch the vanilla base
 *       (client JAR, libraries, assets) and verify every file —
 *       including the libraries the installer downloaded.</li>
 * </ol>
 */
public class InstallerJarModLoaderInstaller implements ModLoaderInstaller {

    private static final long INSTALLER_TIMEOUT_MINUTES = 15;

    private final FileDownloader fileDownloader;
    private final JavaResolutionService javaResolutionService;
    private final ModLoaderMetadataMerger merger;
    private final InstallationService installationService;

    public InstallerJarModLoaderInstaller(FileDownloader fileDownloader,
                                          JavaResolutionService javaResolutionService,
                                          ModLoaderMetadataMerger merger,
                                          InstallationService installationService) {
        this.fileDownloader = fileDownloader;
        this.javaResolutionService = javaResolutionService;
        this.merger = merger;
        this.installationService = installationService;
    }

    @Override
    public ModLoaderInstallResult install(MinecraftVersion vanillaVersion,
                                          VersionMetadata vanillaMetadata,
                                          ModLoaderVersion loader,
                                          GameDirectory gameDir,
                                          InstallationProgress progress) throws IOException {
        String installerUrl = loader.installerUrlOpt()
                .orElseThrow(() -> new IOException(
                        "No installer URL available for " + loader));

        // 1. Download installer JAR
        Path installerFile = gameDir.root().resolve("installers")
                .resolve(installerFileName(installerUrl));
        Files.createDirectories(installerFile.getParent());
        fileDownloader.download(installerUrl, installerFile);
        if (!Files.isRegularFile(installerFile)) {
            throw new IOException("Installer download failed: " + installerUrl);
        }

        // 2. Resolve Java runtime required to run the installer
        JavaResolutionResult javaResult = javaResolutionService.resolve(vanillaMetadata);
        if (!javaResult.isFound() || javaResult.runtime().isEmpty()) {
            throw new IOException("No suitable Java runtime to run the "
                    + loader.loaderType().displayName() + " installer: "
                    + javaResult.reason().orElse("Java not found"));
        }
        Path javaExecutable = javaResult.runtime().get().javaExecutable();

        // 3. Run installer headless
        runInstaller(javaExecutable, installerFile, gameDir.root().toAbsolutePath(),
                loader);

        // 4. Locate the created launch configuration
        String expectedId = loader.installedVersionId();
        Path versionJson = locateVersionJson(gameDir, expectedId, loader);
        if (versionJson == null) {
            throw new IOException(loader.loaderType().displayName()
                    + " installer did not create a version JSON (expected "
                    + expectedId + ")");
        }
        String loaderJson = Files.readString(versionJson, StandardCharsets.UTF_8);

        // 5. Merge + standard installation (vanilla base + verification)
        VersionMetadata merged = merger.merge(vanillaMetadata, loaderJson);
        String versionId = merged.id();

        MinecraftVersion moddedVersion = new MinecraftVersion(
                versionId, ModdedVersionType.INSTANCE, null, null);
        InstallationResult files = installationService.install(
                moddedVersion, merged, gameDir, progress);

        if (files.hasFailures()) {
            throw new IOException(loader.loaderType().displayName()
                    + " installation finished with " + files.failed()
                    + " failed downloads");
        }

        return new ModLoaderInstallResult(versionId, files);
    }

    private void runInstaller(Path javaExecutable, Path installerJar,
                              Path gameRoot, ModLoaderVersion loader) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                javaExecutable.toAbsolutePath().toString(),
                "-jar", installerJar.toAbsolutePath().toString(),
                "--installClient",
                gameRoot.toString());
        pb.directory(gameRoot.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Failed to start "
                    + loader.loaderType().displayName() + " installer: " + e.getMessage(), e);
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (!process.waitFor(INSTALLER_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException(loader.loaderType().displayName()
                        + " installer timed out after "
                        + INSTALLER_TIMEOUT_MINUTES + " minutes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while running installer", e);
        }

        if (process.exitValue() != 0) {
            throw new IOException(loader.loaderType().displayName()
                    + " installer exited with code " + process.exitValue()
                    + ":\n" + tail(output, 20));
        }
    }

    /**
     * Locates the version JSON created by the installer: first the
     * expected id, then a fallback scan of {@code versions/} for a
     * matching profile (robust against loader id-convention changes).
     */
    private Path locateVersionJson(GameDirectory gameDir, String expectedId,
                                   ModLoaderVersion loader) {
        Path expected = gameDir.versionMetadata(expectedId);
        if (Files.isRegularFile(expected)) {
            return expected;
        }

        Path versionsDir = gameDir.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return null;
        }

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
            stream.forEach(candidates::add);
        } catch (IOException e) {
            return null;
        }

        String loaderVersion = loader.loaderVersion();
        String mcVersion = loader.minecraftVersion();
        for (Path dir : candidates) {
            Path json = dir.resolve(dir.getFileName() + ".json");
            if (!Files.isRegularFile(json)) continue;
            try {
                JsonObject root = JsonParser.parseString(
                                Files.readString(json, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String id = root.has("id") && root.get("id").isJsonPrimitive()
                        ? root.get("id").getAsString() : null;
                if (id == null) continue;
                if (id.contains(loaderVersion)
                        && (id.contains(mcVersion)
                                || root.has("inheritsFrom")
                                        && root.get("inheritsFrom").getAsString().equals(mcVersion))) {
                    return json;
                }
            } catch (Exception ignored) {
                // Not a valid version JSON — skip
            }
        }
        return null;
    }

    private static String installerFileName(String url) {
        int lastSlash = url.lastIndexOf('/');
        String name = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        return name.isBlank() ? "installer.jar" : name;
    }

    private static String tail(String text, int lines) {
        if (text == null || text.isBlank()) return "(no output)";
        String[] all = text.lines().toArray(String[]::new);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) {
            sb.append(all[i]).append('\n');
        }
        return sb.toString();
    }
}
