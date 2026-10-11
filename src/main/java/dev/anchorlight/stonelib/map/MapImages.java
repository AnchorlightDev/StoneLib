package dev.anchorlight.stonelib.map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * PNG encoding and decoding for map tiles, plus a plain text card for when there is no image to
 * show yet. Pure {@code java.awt}, safe off the main thread.
 */
public final class MapImages {

    private MapImages() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** Decodes an image file's bytes; throws {@link IOException} when they are not a readable image. */
    public static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("Not a readable image");
        }
        return image;
    }

    /**
     * Decodes an image file's bytes, refusing pictures with more than {@code maxPixels} pixels
     * <i>before</i> decoding them. A small file can declare enormous dimensions; decoding it
     * blindly is an easy {@link OutOfMemoryError}.
     */
    public static BufferedImage decode(byte[] bytes, long maxPixels) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("Not a readable image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Not a readable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new IOException("Image is " + reader.getWidth(0) + "x" + reader.getHeight(0)
                            + ", more than " + maxPixels + " pixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Encodes {@code image} as PNG. */
    public static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("No PNG writer available");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Fits {@code source} to a {@code width x height} map screen and slices it into tiles, row-major.
     * The one call most screens need.
     */
    public static List<BufferedImage> screenTiles(BufferedImage source, int width, int height, ImageFit fit,
                                                  ImageProcessor.Adjustments adjustments, String backgroundHex) {
        BufferedImage canvas = ImageProcessor.process(source, width * TileSlicer.TILE_SIZE, height * TileSlicer.TILE_SIZE,
                fit, adjustments, backgroundHex);
        return TileSlicer.slice(canvas, width, height);
    }

    /**
     * A {@code width x height} map screen's worth of tiles showing {@code lines} centred on a flat
     * background - a placeholder, a title card, an "announcements will appear here" slide.
     */
    public static List<BufferedImage> textCard(int width, int height, List<String> lines, Color background, Color foreground) {
        int w = width * TileSlicer.TILE_SIZE;
        int h = height * TileSlicer.TILE_SIZE;
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(background);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(foreground);
            List<String> shown = lines == null ? List.of() : new ArrayList<>(lines);
            if (!shown.isEmpty()) {
                drawLines(g, shown, w, h);
            }
        } finally {
            g.dispose();
        }
        return TileSlicer.slice(canvas, width, height);
    }

    /** Text needs fonts, which a stripped-down headless JRE may not have; a blank card beats a crash. */
    private static void drawLines(Graphics2D g, List<String> shown, int w, int h) {
        try {
            {
                int size = Math.max(10, Math.min(h / (shown.size() * 2 + 1), w / 12));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
                FontMetrics metrics = g.getFontMetrics();
                int lineHeight = metrics.getHeight();
                int y = (h - lineHeight * shown.size()) / 2 + metrics.getAscent();
                for (String line : shown) {
                    int x = Math.max(0, (w - metrics.stringWidth(line)) / 2);
                    g.drawString(line, x, y);
                    y += lineHeight;
                }
            }
        } catch (RuntimeException | InternalError | UnsatisfiedLinkError | NoClassDefFoundError noFonts) {
            // Leave the card blank.
        }
    }
}
