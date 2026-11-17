package org.example.launcher.presentation;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/**
 * Отладочная утилита для проверки интерфейса: через заданную паузу
 * снимает содержимое узла JavaFX в PNG-файл ({@link Node#snapshot}) и
 * закрывает приложение. Окно при этом не выводится на передний план —
 * скриншот делается средствами самого JavaFX.
 *
 * <p>Включается системным свойством mdd.snapshot=&lt;путь&gt; (обычно
 * вместе с mdd.openView), используется только при разработке и
 * проверке вёрстки; в обычной работе не активируется.</p>
 */
public final class DebugSnapshot {

    private static final Logger LOG =
            Logger.getLogger(DebugSnapshot.class.getName());

    private DebugSnapshot() {
    }

    /** Через {@code delayMillis} сохраняет узел в PNG и выходит. */
    public static void schedule(Node node, String path, long delayMillis) {
        if (node == null || path == null || path.isBlank()) {
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.millis(delayMillis), e -> {
            try {
                save(node.snapshot(new SnapshotParameters(), null), path);
                LOG.info(() -> "Snapshot saved: " + path);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Snapshot failed", ex);
            } finally {
                Platform.exit();
            }
        }));
        timeline.setCycleCount(1);
        timeline.play();
    }

    private static void save(WritableImage image, String path)
            throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(buffered, "png", file);
    }
}
