package org.example.launcher.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Saves builds of a game instance: named snapshots of the
 * instance's {@code mods} and {@code config} folders.
 * <p>
 * A build lives in {@code {gameDir}/builds/{name}/} (name sanitized
 * like instance directory names) with a {@code mods/} and/or
 * {@code config/} subtree — whatever was chosen when saving.
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
}
