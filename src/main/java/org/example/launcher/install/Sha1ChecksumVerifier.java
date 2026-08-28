package org.example.launcher.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@link ChecksumVerifier} using the SHA1 algorithm.
 * <p>
 * Computes the SHA1 hash of the local file and compares it
 * (case-insensitively) to the expected hex string.
 */
public class Sha1ChecksumVerifier implements ChecksumVerifier {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public boolean verify(Path path, String expectedSha1) {
        if (path == null || expectedSha1 == null || expectedSha1.isBlank()) {
            return false;
        }
        if (!Files.isRegularFile(path)) {
            return false;
        }

        try {
            String actualHash = computeSha1(path);
            return actualHash.equalsIgnoreCase(expectedSha1.trim());
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * Computes the SHA1 hash of the given file.
     *
     * @return lowercase hex string
     */
    public static String computeSha1(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
