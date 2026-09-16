package org.example.launcher.distribution;

/**
 * One file of a distributed build, addressed by its category and a
 * path relative to that category's folder.
 *
 * <p>The SHA-1 lets the launcher detect which files it already has
 * (no re-download) and which files changed between build versions
 * (see {@link RemoteBuildService}).</p>
 *
 * @param relativePath path relative to the category folder, uses '/' separators
 * @param category     which part of the build this file belongs to
 * @param sha1         lowercase SHA-1 of the file content
 * @param size         file size in bytes
 */
public record BuildFileEntry(
        String relativePath,
        BuildFileCategory category,
        String sha1,
        long size) {

    public BuildFileEntry {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        if (sha1 == null || sha1.isBlank()) {
            throw new IllegalArgumentException("sha1 must not be blank");
        }
    }

    /**
     * @return the category folder and relative path joined with '/',
     *         the identity of a file within a build used for update
     *         diffs (added / changed / removed).
     */
    public String key() {
        return category.folder() + "/" + relativePath;
    }

    /** @return display form, e.g. {@code mods/sodium.jar}. */
    @Override
    public String toString() {
        return key();
    }
}
