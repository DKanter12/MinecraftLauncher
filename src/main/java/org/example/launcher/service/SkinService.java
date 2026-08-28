package org.example.launcher.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.example.launcher.install.GameDirectory;
import org.example.launcher.model.GameProfile;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/**
 * Downloads and caches player skin textures, extracting the face
 * (head front layer) for display in the launcher UI.
 * <p>
 * Minecraft skin textures are 64×64 (or 64×32 legacy) PNG files.
 * The face is the 8×8 region at (8, 8), scaled up to the desired size.
 * <p>
 * Skins are cached locally under {@code assets/skins/<hash>.png} to
 * avoid re-downloading on every launcher start.
 */
public class SkinService {

    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;

    private final HttpClient httpClient;
    private final GameDirectory gameDir;

    public SkinService(GameDirectory gameDir) {
        this.gameDir = gameDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Asynchronously loads the face avatar for the given profile.
     *
     * @param profile  the player profile (must have a skin URL for Ely.by accounts)
     * @param size     target avatar size in pixels (e.g. 32)
     * @return a CompletableFuture that resolves to the avatar Image,
     *         or empty if the profile has no skin
     */
    public CompletableFuture<Optional<Image>> loadAvatarAsync(GameProfile profile, int size) {
        if (profile.skinUrl().isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return Optional.ofNullable(loadAvatar(profile.skinUrl().get(), size));
            } catch (Exception e) {
                return Optional.<Image>empty();
            }
        });
    }

    /**
     * Synchronously loads (or retrieves from cache) the face avatar.
     */
    public Image loadAvatar(String skinUrl, int size) throws IOException {
        String hash = urlToHash(skinUrl);
        Path cacheDir = gameDir.assetsDir().resolve("skins");
        Path cachedFile = cacheDir.resolve(hash + "_" + size + ".png");

        if (Files.isRegularFile(cachedFile)) {
            return new Image(cachedFile.toUri().toString());
        }

        BufferedImage skin = downloadSkin(skinUrl);
        BufferedImage face = extractFace(skin, size);

        Files.createDirectories(cacheDir);
        ImageIO.write(face, "png", cachedFile.toFile());

        return bufferedImageToJavaFX(face);
    }

    private BufferedImage downloadSkin(String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " downloading skin");
            }

            return ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Skin download interrupted", e);
        }
    }

    /**
     * Extracts the 8×8 face region from a skin texture and scales it.
     */
    private BufferedImage extractFace(BufferedImage skin, int targetSize) {
        int w = skin.getWidth();
        int h = skin.getHeight();

        // Legacy 64×32 skins: face is at (8,8) on the top row
        // Modern 64×64 skins: same position, but there's also an overlay
        // We use the base layer only
        int srcX = FACE_X;
        int srcY = FACE_Y;

        // Also extract the face overlay (hat layer) if skin is 64×64
        int overlaySrcX = 40;
        int overlaySrcY = 8;

        BufferedImage result = new BufferedImage(targetSize, targetSize,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(skin, 0, 0, targetSize, targetSize,
                srcX, srcY, srcX + FACE_SIZE, srcY + FACE_SIZE, null);

        // Overlay the hat layer if available (64×64 skins only)
        if (w >= 64 && h >= 64) {
            g.drawImage(skin, 0, 0, targetSize, targetSize,
                    overlaySrcX, overlaySrcY,
                    overlaySrcX + FACE_SIZE, overlaySrcY + FACE_SIZE, null);
        }
        g.dispose();

        return result;
    }

    private Image bufferedImageToJavaFX(BufferedImage bi) {
        WritableImage wi = new WritableImage(bi.getWidth(), bi.getHeight());
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                wi.getPixelWriter().setArgb(x, y, bi.getRGB(x, y));
            }
        }
        return wi;
    }

    private String urlToHash(String url) {
        int lastSlash = url.lastIndexOf('/');
        String filename = (lastSlash >= 0) ? url.substring(lastSlash + 1) : url;
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx > 0) filename = filename.substring(0, dotIdx);
        return filename.length() > 40 ? filename.substring(0, 40) : filename;
    }
}
