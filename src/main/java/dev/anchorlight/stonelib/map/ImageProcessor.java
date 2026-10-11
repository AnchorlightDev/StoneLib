package dev.anchorlight.stonelib.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * Fits a decoded source image into a fixed-size canvas using one of the {@link ImageFit}
 * strategies, then applies restrained brightness/contrast/saturation/sharpen adjustments.
 *
 * <p>Pure {@code java.awt.image} logic, safe to run off the main thread: no Bukkit or
 * {@code MapCanvas} APIs are touched here. Slicing the canvas into 128x128 map tiles is
 * {@link TileSlicer}'s job.</p>
 *
 * <pre>{@code
 * BufferedImage canvas = ImageProcessor.process(source, 4 * 128, 3 * 128, ImageFit.CONTAIN,
 *         ImageProcessor.Adjustments.NONE, "#000000");
 * List<BufferedImage> tiles = TileSlicer.slice(canvas, 4, 3);
 * }</pre>
 *
 * <p>Ported from PixelPost.</p>
 */
public final class ImageProcessor {

    /**
     * Colour adjustments applied after fitting.
     *
     * @param focalX     focal point X in [0, 1], only used by {@link ImageFit#COVER}
     * @param focalY     focal point Y in [0, 1], only used by {@link ImageFit#COVER}
     * @param brightness multiplicative brightness, 1.0 = unchanged
     * @param contrast   multiplicative contrast, 1.0 = unchanged
     * @param saturation multiplicative saturation, 1.0 = unchanged, 0.0 = greyscale
     * @param sharpen    sharpen amount in [0, 1], 0 = off
     */
    public record Adjustments(double focalX, double focalY, double brightness, double contrast,
                              double saturation, double sharpen) {

        /** Centred, no colour changes, no sharpening. */
        public static final Adjustments NONE = new Adjustments(0.5, 0.5, 1.0, 1.0, 1.0, 0.0);

        /**
         * A mild lift that tends to survive the 248-colour map palette better: slightly more
         * contrast and saturation, a touch of sharpening.
         */
        public static final Adjustments MAP_FRIENDLY = new Adjustments(0.5, 0.5, 1.0, 1.08, 1.1, 0.25);
    }

    private ImageProcessor() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** {@link #process(BufferedImage, int, int, ImageFit, double, double, String, double, double, double, double)} with an {@link Adjustments}. */
    public static BufferedImage process(BufferedImage source, int canvasWidth, int canvasHeight, ImageFit fitMode,
                                        Adjustments adjustments, String letterboxHex) {
        Adjustments a = adjustments == null ? Adjustments.NONE : adjustments;
        return process(source, canvasWidth, canvasHeight, fitMode, a.focalX(), a.focalY(), letterboxHex,
                a.brightness(), a.contrast(), a.saturation(), a.sharpen());
    }

    /**
     * Produces a {@code canvasWidth x canvasHeight} {@link BufferedImage} of type
     * {@link BufferedImage#TYPE_INT_RGB} from the given source image.
     *
     * @param source       decoded source image
     * @param canvasWidth  exact output width in pixels (must be &gt; 0)
     * @param canvasHeight exact output height in pixels (must be &gt; 0)
     * @param fitMode      COVER, CONTAIN, or STRETCH
     * @param focalX       focal point X in [0.0, 1.0], only used by COVER
     * @param focalY       focal point Y in [0.0, 1.0], only used by COVER
     * @param letterboxHex background colour for CONTAIN letterbox bars, e.g. {@code "#000000"}
     * @param brightness   multiplicative brightness factor, 1.0 = unchanged
     * @param contrast     multiplicative contrast factor, 1.0 = unchanged
     * @param saturation   multiplicative saturation factor, 1.0 = unchanged, 0.0 = greyscale
     * @param sharpen      sharpen amount, 0.0 = disabled, capped at 1.0 internally
     * @return exact-dimension canvas in TYPE_INT_RGB
     */
    public static BufferedImage process(BufferedImage source, int canvasWidth, int canvasHeight, ImageFit fitMode,
                                         double focalX, double focalY, String letterboxHex,
                                         double brightness, double contrast, double saturation, double sharpen) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new IllegalArgumentException("canvasWidth and canvasHeight must be positive");
        }

        BufferedImage fitted = switch (fitMode) {
            case COVER -> cover(source, canvasWidth, canvasHeight, focalX, focalY);
            case CONTAIN -> contain(source, canvasWidth, canvasHeight, letterboxHex);
            case STRETCH -> stretch(source, canvasWidth, canvasHeight);
        };

        BufferedImage adjusted = applyAdjustments(fitted, brightness, contrast, saturation);
        if (sharpen > 0.0) {
            adjusted = applySharpen(adjusted, sharpen);
        }
        return toRgb(adjusted, canvasWidth, canvasHeight);
    }

    private static BufferedImage cover(BufferedImage source, int canvasWidth, int canvasHeight, double focalX, double focalY) {
        double scale = Math.max((double) canvasWidth / source.getWidth(), (double) canvasHeight / source.getHeight());
        int scaledWidth = Math.max(canvasWidth, (int) Math.ceil(source.getWidth() * scale));
        int scaledHeight = Math.max(canvasHeight, (int) Math.ceil(source.getHeight() * scale));
        BufferedImage scaled = scaleTo(source, scaledWidth, scaledHeight);

        int maxOffsetX = Math.max(0, scaledWidth - canvasWidth);
        int maxOffsetY = Math.max(0, scaledHeight - canvasHeight);
        int offsetX = (int) Math.round(clamp01(focalX) * maxOffsetX);
        int offsetY = (int) Math.round(clamp01(focalY) * maxOffsetY);

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        applyQualityHints(g);
        g.drawImage(scaled, -offsetX, -offsetY, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage contain(BufferedImage source, int canvasWidth, int canvasHeight, String letterboxHex) {
        double scale = Math.min((double) canvasWidth / source.getWidth(), (double) canvasHeight / source.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        // Guard against rounding pushing the scaled image a pixel over the canvas bounds.
        scaledWidth = Math.min(scaledWidth, canvasWidth);
        scaledHeight = Math.min(scaledHeight, canvasHeight);
        BufferedImage scaled = scaleTo(source, scaledWidth, scaledHeight);

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        applyQualityHints(g);
        g.setColor(decodeColor(letterboxHex));
        g.fillRect(0, 0, canvasWidth, canvasHeight);
        int x = (canvasWidth - scaledWidth) / 2;
        int y = (canvasHeight - scaledHeight) / 2;
        g.drawImage(scaled, x, y, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage stretch(BufferedImage source, int canvasWidth, int canvasHeight) {
        return scaleTo(source, canvasWidth, canvasHeight);
    }

    private static BufferedImage scaleTo(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        applyQualityHints(g);
        g.drawImage(source, 0, 0, Math.max(1, width), Math.max(1, height), null);
        g.dispose();
        return result;
    }

    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    private static BufferedImage applyAdjustments(BufferedImage image, double brightness, double contrast, double saturation) {
        boolean identity = brightness == 1.0 && contrast == 1.0 && saturation == 1.0;
        if (identity) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = new Color(image.getRGB(x, y));
                float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
                float saturated = clampFloat(hsb[1] * (float) saturation);
                int rgb = Color.HSBtoRGB(hsb[0], saturated, hsb[2]);
                Color saturatedColor = new Color(rgb);

                int r = clampByte((int) Math.round(((saturatedColor.getRed() - 127) * contrast + 127) * brightness));
                int g2 = clampByte((int) Math.round(((saturatedColor.getGreen() - 127) * contrast + 127) * brightness));
                int b = clampByte((int) Math.round(((saturatedColor.getBlue() - 127) * contrast + 127) * brightness));
                result.setRGB(x, y, new Color(r, g2, b).getRGB());
            }
        }
        return result;
    }

    private static BufferedImage applySharpen(BufferedImage image, double amount) {
        float a = (float) Math.min(1.0, Math.max(0.0, amount));
        if (a == 0f) {
            return image;
        }
        float center = 1f + 4f * a;
        float edge = -a;
        Kernel kernel = new Kernel(3, 3, new float[]{
            0, edge, 0,
            edge, center, edge,
            0, edge, 0
        });
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage destination = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        return op.filter(image, destination);
    }

    /**
     * Normalizes the final image to TYPE_INT_RGB and guards against any upstream logic error
     * producing a canvas of the wrong size.
     */
    private static BufferedImage toRgb(BufferedImage image, int expectedWidth, int expectedHeight) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB
                && image.getWidth() == expectedWidth && image.getHeight() == expectedHeight) {
            return image;
        }
        BufferedImage converted = new BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = converted.createGraphics();
        applyQualityHints(g);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return converted;
    }

    private static Color decodeColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static float clampFloat(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
