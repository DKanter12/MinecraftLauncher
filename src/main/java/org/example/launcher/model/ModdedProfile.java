package org.example.launcher.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.launcher.service.modloader.ModLoaderType;

/**
 * A game instance: a named, self-contained play environment.
 * <p>
 * An instance is either {@code VANILLA} (plays the unmodified game)
 * or a mod loader installation (Fabric, Forge, NeoForge, Quilt). Each
 * instance owns a separate game directory (holding {@code mods/},
 * {@code config/}, {@code resourcepacks/}, {@code shaderpacks/},
 * {@code saves/}, {@code logs/} and other pack-specific files), so
 * different instances never conflict with each other. Shared
 * resources (the client JAR, libraries, assets, natives) live in the
 * launcher's storage root and are reused across instances without
 * re-downloading.
 * <p>
 * An instance records everything needed to reproduce and verify the
 * installation: the Minecraft version, the loader type and version,
 * the installed version id, the game directory, the installed
 * components and additional launch parameters.
 *
 * @param id              unique profile id (also the directory name
 *                        under {@code profiles/})
 * @param name            human-readable display name
 * @param loaderType      the mod loader family (Fabric, Forge, …)
 * @param loaderVersion   the loader's own version (e.g. "0.16.9")
 * @param minecraftVersion the target Minecraft version (e.g. "1.21.4")
 * @param versionId       the installed modded version id this profile
 *                        launches (e.g. "fabric-loader-0.16.9-1.21.4")
 * @param gameDirPath     the profile's game directory, relative to the
 *                        storage root (e.g. "profiles/MyPack")
 * @param components      the installed components, e.g.
 *                        ["minecraft:1.21.4", "fabric-loader:0.16.9"]
 * @param extraJvmArgs    additional JVM launch parameters for this
 *                        profile (e.g. "-Xmx4G")
 * @param createdTimeRaw     ISO-8601 creation timestamp
 * @param lastPlayedTimeRaw  ISO-8601 timestamp of the last launch,
 *                           or {@code null} if never played
 */
public record ModdedProfile(
        String id,
        String name,
        ModLoaderType loaderType,
        String loaderVersion,
        String minecraftVersion,
        String versionId,
        String gameDirPath,
        List<String> components,
        List<String> extraJvmArgs,
        String createdTimeRaw,
        String lastPlayedTimeRaw) {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public ModdedProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(loaderType, "loaderType");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(gameDirPath, "gameDirPath");
        components = List.copyOf(components == null ? List.of() : components);
        extraJvmArgs = List.copyOf(extraJvmArgs == null ? List.of() : extraJvmArgs);
    }

    /**
     * The version id this profile is expected to launch, derived from
     * the loader type/version and Minecraft version using the loader's
     * id convention. Compared against the installed version JSON's id
     * during verification. Vanilla instances launch the vanilla
     * version id itself.
     */
    public String expectedVersionId() {
        if (loaderType == ModLoaderType.VANILLA) {
            return minecraftVersion;
        }
        return new ModLoaderVersion(loaderType, loaderVersion, minecraftVersion,
                true, null).installedVersionId();
    }

    /** Whether this instance plays the unmodified vanilla game. */
    public boolean isVanilla() {
        return loaderType == ModLoaderType.VANILLA;
    }

    public Optional<OffsetDateTime> createdTime() {
        return parseTime(createdTimeRaw);
    }

    public Optional<OffsetDateTime> lastPlayedTime() {
        return parseTime(lastPlayedTimeRaw);
    }

    /** Short display line, e.g. "Fabric 0.16.9 · MC 1.21.4". */
    public String summary() {
        if (isVanilla()) {
            return "Vanilla · MC " + minecraftVersion;
        }
        return loaderType.displayName() + " " + loaderVersion
                + " · MC " + minecraftVersion;
    }

    private static Optional<OffsetDateTime> parseTime(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(OffsetDateTime.parse(raw, FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "ModdedProfile{id='" + id + "', name='" + name + "', "
                + summary() + ", versionId=" + versionId + "}";
    }
}
