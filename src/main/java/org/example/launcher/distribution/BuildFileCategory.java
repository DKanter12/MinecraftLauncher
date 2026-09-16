package org.example.launcher.distribution;

/**
 * Separates the different kinds of files a distributed build consists
 * of. The category decides where a file lands inside an instance's
 * build folder, which keeps build information, the Minecraft files,
 * mods, configs and additional resources strictly apart:
 *
 * <ul>
 *   <li>{@code MINECRAFT} — the Minecraft side of the build (the
 *       version and loader to run it with). This is descriptive
 *       metadata carried by {@link BuildSummary}; no per-file entries
 *       of this category are downloaded into the build folder.</li>
 *   <li>{@code MODS} — mod jars, installed into {@code mods/}.</li>
 *   <li>{@code CONFIGS} — configuration files, installed into
 *       {@code config/}.</li>
 *   <li>{@code RESOURCES} — everything else (resource packs, shader
 *       packs, additional files), installed into {@code resources/}.</li>
 * </ul>
 */
public enum BuildFileCategory {
    MINECRAFT("minecraft"),
    MODS("mods"),
    CONFIGS("config"),
    RESOURCES("resources");

    private final String folder;

    BuildFileCategory(String folder) {
        this.folder = folder;
    }

    /** @return the folder name inside a build directory this category maps to. */
    public String folder() {
        return folder;
    }

    /**
     * @return true when files of this category are copied into the
     * instance's live folders when the build is applied.
     */
    public boolean isAppliedToInstance() {
        return this == MODS || this == CONFIGS;
    }
}
