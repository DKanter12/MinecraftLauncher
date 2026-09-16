package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildFileEntry;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;

/**
 * The client side of the launcher server's API: everything a regular
 * (signed-in) user of the launcher needs. The server part (a plain
 * HTTPS service) is responsible for validating the bearer token on
 * every call and for only returning builds the administrator
 * published.
 *
 * <p>The launcher talks to exactly one implementation of this
 * interface; without a configured server that is
 * {@link OfflineLauncherServerApi} and the launcher stays fully
 * local.</p>
 */
public interface LauncherServerApi {

    /**
     * Signs an account in with login and password.
     *
     * <p>Security contract: credentials travel only inside this call
     * (over HTTPS on a real server) and are never persisted — the
     * launcher keeps the returned token for subsequent requests.</p>
     *
     * @param login    account login
     * @param password account password
     * @return the authorized session with token and role
     * @throws IOException on network errors or when the server rejects the credentials
     */
    ServerSession login(String login, String password) throws IOException;

    /**
     * Lists the builds the administrator published, newest version
     * of each build first.
     *
     * @param session the signed-in session
     * @return build summaries, never {@code null}
     * @throws IOException on network errors or when the session is invalid
     */
    List<BuildSummary> listBuilds(ServerSession session) throws IOException;

    /**
     * Fetches the complete description of one build — summary plus
     * the file list with hashes — for installation and updates.
     *
     * @param session the signed-in session
     * @param buildId unique build id
     * @return the build descriptor
     * @throws IOException on network errors or when the build is unknown
     */
    BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException;

    /**
     * Downloads one file of a build into {@code target}.
     *
     * @param session the signed-in session
     * @param build   the build being installed
     * @param file    the entry to download
     * @param target  local destination path, parent directories exist
     * @throws IOException on network errors
     */
    void downloadFile(ServerSession session, BuildDescriptor build, BuildFileEntry file, Path target) throws IOException;
}
