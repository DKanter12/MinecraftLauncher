package org.example.launcher.distribution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.launcher.distribution.api.LauncherServerApi;
import org.example.launcher.service.modloader.ModLoaderType;

/**
 * In-memory launcher server for tests: serves a configurable catalog,
 * hands out sessions and writes file contents on download, recording
 * every download so tests can assert what was re-downloaded.
 */
public class FakeLauncherServerApi implements LauncherServerApi {

    public ServerSession sessionToReturn;
    public IOException loginFailure;

    public final Map<String, BuildDescriptor> builds = new LinkedHashMap<>();
    public final Map<String, byte[]> contents = new LinkedHashMap<>();
    public final List<String> downloads = new ArrayList<>();

    /** Starts defining a build the fake server will publish. */
    public DescriptorBuilder build(String id, String version) {
        return new DescriptorBuilder(this, id, version);
    }

    @Override
    public ServerSession login(String login, String password) throws IOException {
        if (loginFailure != null) {
            throw loginFailure;
        }
        if (sessionToReturn == null) {
            throw new IOException("Invalid credentials");
        }
        return sessionToReturn;
    }

    @Override
    public List<BuildSummary> listBuilds(ServerSession session) throws IOException {
        requireSession(session);
        return builds.values().stream().map(BuildDescriptor::summary).toList();
    }

    @Override
    public BuildDescriptor fetchBuild(ServerSession session, String buildId) throws IOException {
        requireSession(session);
        BuildDescriptor descriptor = builds.get(buildId);
        if (descriptor == null) {
            throw new IOException("Unknown build: " + buildId);
        }
        return descriptor;
    }

    @Override
    public void downloadFile(ServerSession session, BuildDescriptor build, BuildFileEntry file, Path target) throws IOException {
        requireSession(session);
        byte[] content = contents.get(contentKey(build.id(), build.version(), file.key()));
        if (content == null) {
            throw new IOException("No content for " + build.id() + "/" + file.key());
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, content);
        downloads.add(build.id() + "/" + file.key());
    }

    private void requireSession(ServerSession session) throws IOException {
        if (session == null) {
            throw new IOException("Not signed in");
        }
    }

    private String contentKey(String buildId, String version, String fileKey) {
        return buildId + "|" + version + "|" + fileKey;
    }

    /** Collects files (with content, so SHA-1 and downloads stay consistent) for one build version. */
    public static final class DescriptorBuilder {

        private final FakeLauncherServerApi api;
        private final String id;
        private final String version;
        private String displayName;
        private String description;
        private ModLoaderType loaderType = ModLoaderType.FABRIC;
        private String minecraftVersion = "1.21.4";
        private String loaderVersion = "0.16.9";
        private final List<BuildFileEntry> files = new ArrayList<>();

        private DescriptorBuilder(FakeLauncherServerApi api, String id, String version) {
            this.api = api;
            this.id = id;
            this.version = version;
            this.displayName = id;
        }

        public DescriptorBuilder named(String name) {
            this.displayName = name;
            return this;
        }

        public DescriptorBuilder described(String description) {
            this.description = description;
            return this;
        }

        public DescriptorBuilder meta(ModLoaderType loaderType, String minecraftVersion, String loaderVersion) {
            this.loaderType = loaderType;
            this.minecraftVersion = minecraftVersion;
            this.loaderVersion = loaderVersion;
            return this;
        }

        public DescriptorBuilder file(BuildFileCategory category, String relativePath, String text) {
            byte[] content = text.getBytes(StandardCharsets.UTF_8);
            files.add(new BuildFileEntry(relativePath, category, sha1(content), content.length));
            api.contents.put(api.contentKey(id, version, category.folder() + "/" + relativePath), content);
            return this;
        }

        /** Registers the build on the fake server and returns its descriptor. */
        public BuildDescriptor publish() {
            BuildDescriptor descriptor = new BuildDescriptor(
                    new BuildSummary(id, version, displayName, description, loaderType,
                            minecraftVersion, loaderVersion),
                    files, BuildOrigin.SERVER);
            api.builds.put(id, descriptor);
            return descriptor;
        }

        private static String sha1(byte[] content) {
            try {
                return hex(MessageDigest.getInstance("SHA-1").digest(content));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String hex(byte[] digest) {
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }
    }
}
