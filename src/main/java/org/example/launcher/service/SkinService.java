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
 * Скачивает и кэширует текстуры скинов игроков, извлекая лицо
 * (передний слой головы) для отображения в UI лаунчера.
 * <p>
 * Текстуры скинов Minecraft — это PNG 64×64 (или 64×32 для старых).
 * Лицо — область 8×8 в точке (8, 8), масштабируемая до нужного размера.
 * <p>
 * Скины кэшируются локально в {@code assets/skins/<hash>.png}, чтобы
 * не скачивать их при каждом старте лаунчера.
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
     * Асинхронно загружает аватар-лицо для заданного профиля.
     *
     * @param profile  профиль игрока (для аккаунтов Ely.by должен иметь URL скина)
     * @param size     целевой размер аватара в пикселях (например, 32)
     * @return CompletableFuture с изображением аватара
     *         либо пусто, если у профиля нет скина
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
     * Синхронно загружает (либо берёт из кэша) аватар-лицо.
     * <p>
     * Кэш читается через {@code ImageIO.read} (синхронно, с проверкой):
     * повреждённый или обрезанный кэшированный файл обнаруживается, удаляется, а
     * скин скачивается заново. Это самовосстанавливает битые аватары без
     * необходимости повторного логина.
     */
    public Image loadAvatar(String skinUrl, int size) throws IOException {
        String hash = urlToHash(skinUrl);
        Path cacheDir = gameDir.assetsDir().resolve("skins");
        Path cachedFile = cacheDir.resolve(hash + "_" + size + ".png");

        if (Files.isRegularFile(cachedFile)) {
            BufferedImage cached = ImageIO.read(cachedFile.toFile());
            if (cached != null) {
                return bufferedImageToJavaFX(cached);
            }
            Files.deleteIfExists(cachedFile);
        }

        BufferedImage skin = downloadSkin(skinUrl);
        if (skin == null) {
            throw new IOException("Invalid skin image data: " + skinUrl);
        }
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
     * Извлекает область лица 8×8 из текстуры скина и масштабирует её.
     */
    private BufferedImage extractFace(BufferedImage skin, int targetSize) {
        int w = skin.getWidth();
        int h = skin.getHeight();

        // Старые скины 64×32: лицо находится в (8,8) верхнего ряда
        // Современные скины 64×64: позиция та же, но есть ещё оверлей
        // Используем только базовый слой
        int srcX = FACE_X;
        int srcY = FACE_Y;

        // Также извлечь оверлей лица (слой шапки), если скин 64×64
        int overlaySrcX = 40;
        int overlaySrcY = 8;

        BufferedImage result = new BufferedImage(targetSize, targetSize,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(skin, 0, 0, targetSize, targetSize,
                srcX, srcY, srcX + FACE_SIZE, srcY + FACE_SIZE, null);

        // Наложить слой шапки, если доступен (только для скинов 64×64)
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
