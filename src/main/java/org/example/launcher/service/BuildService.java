package org.example.launcher.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Manages builds of a game instance: named snapshots of the
 * instance's {@code mods} and {@code config} folders the user can
 * switch between.
 * <p>
 * A build lives in {@code {gameDir}/builds/{name}/} (name sanitized
 * like instance directory names) with a {@code mods/} and/or
 * {@code config/} subtree — whatever was chosen when saving. The
 * instance's active build is recorded in
 * {@code {gameDir}/builds/active-build} and is applied right before
 * launch: the game directory's {@code mods}/{@code config} folders
 * are replaced with the build's contents, so many builds can coexist
 * for one version and switching them is a single click.
 */
public class BuildService {

    /**
     * A stored build: sanitized name plus which folders it contains.
     *
     * @param name    sanitized build name (directory name)
     * @param mods    whether the build includes the mods folder
     * @param configs whether the build includes the config folder
     */
    public record BuildInfo(String name, boolean mods, boolean configs) {

        /** Human-readable contents, e.g. {@code "mods + configs"}. */
        public String description() {
            if (mods && configs) {
                return "mods + configs";
            }
            if (mods) {
                return "mods";
            }
            if (configs) {
                return "configs";
            }
            return "empty";
        }
    }

    private static final String BUILDS_DIR = "builds";
    private static final String ACTIVE_MARKER = "active-build";

    /**
     * All saved builds of the instance, sorted by name. Directories
     * containing neither {@code mods} nor {@code config} are ignored.
     */
    public List<BuildInfo> listBuilds(Path gameDir) throws IOException {
        Path builds = gameDir.resolve(BUILDS_DIR);
        if (!Files.isDirectory(builds)) {
            return List.of();
        }
        List<BuildInfo> result = new ArrayList<>();
        try (var stream = Files.list(builds)) {
            stream.filter(Files::isDirectory)
                    .map(dir -> new BuildInfo(
                            dir.getFileName().toString(),
                            Files.isDirectory(dir.resolve("mods")),
                            Files.isDirectory(dir.resolve("config"))))
                    .filter(info -> info.mods() || info.configs())
                    .sorted(Comparator.comparing(BuildInfo::name))
                    .forEach(result::add);
        }
        return result;
    }

    /**
     * Saves a new build from the instance's current folders.
     *
     * @param name             user-given build name (sanitized into
     *                         the directory name)
     * @param includeMods      snapshot the {@code mods} folder
     * @param includeConfigs   snapshot the {@code config} folder
     * @return the stored build
     * @throws IOException on a blank/unsafe name, a duplicate build
     *                     name, or when a requested source folder does
     *                     not exist
     */
    public BuildInfo saveBuild(Path gameDir,
                               String name,
                               boolean includeMods,
                               boolean includeConfigs) throws IOException {
        if (!includeMods && !includeConfigs) {
            throw new IOException("Choose what to save: mods and/or configs");
        }
        String dirName = ModdedProfileService.sanitize(name);
        if (dirName.isBlank()) {
            throw new IOException("Build name must contain letters or digits");
        }
        Path builds = gameDir.resolve(BUILDS_DIR);
        Path target = builds.resolve(dirName);
        if (Files.exists(target)) {
            throw new IOException("Build '" + dirName + "' already exists");
        }
        Path modsSource = gameDir.resolve("mods");
        Path configSource = gameDir.resolve("config");
        if (includeMods && !Files.isDirectory(modsSource)) {
            throw new IOException(
                    "No mods folder in the game directory yet — nothing to save");
        }
        if (includeConfigs && !Files.isDirectory(configSource)) {
            throw new IOException(
                    "No config folder in the game directory yet — nothing to save");
        }
        if (includeMods) {
            copyTree(modsSource, target.resolve("mods"));
        }
        if (includeConfigs) {
            copyTree(configSource, target.resolve("config"));
        }
        return new BuildInfo(dirName, includeMods, includeConfigs);
    }

    /**
     * Marks the build the instance launches with; {@code null} clears
     * the selection (launch with the current folders).
     */
    public void selectBuild(Path gameDir, String name) throws IOException {
        Path builds = gameDir.resolve(BUILDS_DIR);
        if (name == null) {
            Files.deleteIfExists(builds.resolve(ACTIVE_MARKER));
            return;
        }
        if (!Files.isDirectory(builds.resolve(name))) {
            throw new IOException("Build '" + name + "' does not exist");
        }
        Files.createDirectories(builds);
        Files.writeString(builds.resolve(ACTIVE_MARKER), name,
                StandardCharsets.UTF_8);
    }

    /**
     * The build to apply at launch, or empty when none is selected.
     * A marker pointing to a deleted build is treated as no
     * selection.
     */
    public Optional<String> selectedBuild(Path gameDir) {
        try {
            Path marker = gameDir.resolve(BUILDS_DIR).resolve(ACTIVE_MARKER);
            if (!Files.isRegularFile(marker)) {
                return Optional.empty();
            }
            String name = Files.readString(marker, StandardCharsets.UTF_8).trim();
            if (name.isBlank()
                    || !Files.isDirectory(
                            gameDir.resolve(BUILDS_DIR).resolve(name))) {
                return Optional.empty();
            }
            return Optional.of(name);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Deletes a stored build; clears the selection if it was active. */
    public void deleteBuild(Path gameDir, String name) throws IOException {
        Path target = gameDir.resolve(BUILDS_DIR).resolve(name);
        if (!Files.isDirectory(target)) {
            throw new IOException("Build '" + name + "' does not exist");
        }
        deleteTree(target);
        if (name.equals(selectedBuild(gameDir).orElse(null))) {
            selectBuild(gameDir, null);
        }
    }

    /**
     * Applies a build to the game directory: every folder the build
     * contains replaces the current one ({@code mods} and/or
     * {@code config} are wiped and re-copied) — called right before
     * launch.
     */
    public void applyBuild(Path gameDir, String name) throws IOException {
        Path build = gameDir.resolve(BUILDS_DIR).resolve(name);
        if (!Files.isDirectory(build)) {
            throw new IOException("Build '" + name + "' does not exist");
        }
        if (Files.isDirectory(build.resolve("mods"))) {
            replaceDir(gameDir.resolve("mods"), build.resolve("mods"));
        }
        if (Files.isDirectory(build.resolve("config"))) {
            replaceDir(gameDir.resolve("config"), build.resolve("config"));
        }
    }

    private static void replaceDir(Path target, Path source)
            throws IOException {
        deleteTree(target);
        copyTree(source, target);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir,
                    IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
