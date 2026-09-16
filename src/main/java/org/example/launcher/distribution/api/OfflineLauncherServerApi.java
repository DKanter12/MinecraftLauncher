package org.example.launcher.distribution.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.example.launcher.distribution.BuildDescriptor;
import org.example.launcher.distribution.BuildFileCategory;
import org.example.launcher.distribution.BuildFileEntry;
import org.example.launcher.distribution.BuildSummary;
import org.example.launcher.distribution.ServerSession;

/**
 * The default implementation used when no launcher server is
 * configured: the launcher runs in pure local mode, exactly as it did
 * before the distribution architecture existed. Local builds,
 * installation and launching keep working untouched.
 *
 * <p>Sign-in and every administrative function fail with a clear
 * message instead of silently doing nothing; the build catalog is
 * simply empty.</p>
 */
public class OfflineLauncherServerApi implements AdminLauncherServerApi {

    private static final String NOT_CONFIGURED =
            "Launcher server is not configured — the launcher runs in local mode";
    private static final String ADMIN_UNAVAILABLE =
            "Administrator functions require a configured launcher server";

    @Override
    public ServerSession login(String login, String password) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }

    @Override
    public List<BuildSummary> listBuilds(ServerSession session) throws IOException {
        if (session == null) {
            throw new IOException(NOT_CONFIGURED);
        }
        return List.of();
    }

    @Override
    public BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }

    @Override
    public void downloadFile(ServerSession session, BuildDescriptor build, BuildFileEntry file, Path target) throws IOException {
        throw new IOException(NOT_CONFIGURED);
    }

    @Override
    public BuildSummary createBuild(ServerSession session, BuildSummary draft) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }

    @Override
    public void uploadBuildFile(ServerSession session, String buildId, String version,
                                BuildFileCategory category, String relativePath, Path file) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }

    @Override
    public void publishBuild(ServerSession session, String buildId, String version) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }

    @Override
    public void hideBuild(ServerSession session, String buildId) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }

    @Override
    public void deleteBuild(ServerSession session, String buildId) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }

    @Override
    public List<String> listUsers(ServerSession session) throws IOException {
        throw new IOException(ADMIN_UNAVAILABLE);
    }
}
