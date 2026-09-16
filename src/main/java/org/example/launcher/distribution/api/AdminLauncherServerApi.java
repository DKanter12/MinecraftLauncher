package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.distribution.BuildFileCategory;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;

/**
 * The administrative part of the launcher server's API. A superset of
 * {@link LauncherServerApi}: it is the surface an administrator's
 * launcher uses to create, upload, publish and retire builds.
 *
 * <p>Both the launcher and the server enforce the ADMIN role — a
 * regular user's session never reaches these functions.</p>
 */
public interface AdminLauncherServerApi extends LauncherServerApi {

    /**
     * Registers a new build (or a new version of an existing build id)
     * on the server, in draft state.
     *
     * @param session an ADMIN session
     * @param draft   id, version, names and the Minecraft/loader
     *                coordinates of the build
     * @return the stored summary as the server accepted it
     * @throws IOException on network errors or when the session is not ADMIN
     */
    BuildSummary createBuild(ServerSession session, BuildSummary draft) throws IOException;

    /**
     * Uploads one file of a draft build version.
     *
     * @param session      an ADMIN session
     * @param buildId      unique build id
     * @param version      the draft version the file belongs to
     * @param category     which part of the build the file is
     * @param relativePath path inside the category folder
     * @param file         local file to upload
     * @throws IOException on network errors or when the session is not ADMIN
     */
    void uploadBuildFile(ServerSession session, String buildId, String version,
                         BuildFileCategory category, String relativePath, Path file) throws IOException;

    /**
     * Publishes a draft build version: from this moment regular users
     * see it in their build list.
     *
     * @param session an ADMIN session
     * @param buildId unique build id
     * @param version the version to publish
     * @throws IOException on network errors or when the session is not ADMIN
     */
    void publishBuild(ServerSession session, String buildId, String version) throws IOException;

    /**
     * Hides a build from regular users without deleting it.
     *
     * @param session an ADMIN session
     * @param buildId unique build id
     * @throws IOException on network errors or when the session is not ADMIN
     */
    void hideBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Removes a build from the server entirely.
     *
     * @param session an ADMIN session
     * @param buildId unique build id
     * @throws IOException on network errors or when the session is not ADMIN
     */
    void deleteBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Lists the users registered on the launcher server, for
     * administration.
     *
     * @param session an ADMIN session
     * @return login names with their roles, never {@code null}
     * @throws IOException on network errors or when the session is not ADMIN
     */
    List<String> listUsers(ServerSession session) throws IOException;
}
