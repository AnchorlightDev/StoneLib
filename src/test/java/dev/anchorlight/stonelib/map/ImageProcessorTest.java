package dev.anchorlight.stonelib.map;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImageProcessorTest {

    private BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    /** Two vertical stripes: left half one colour, right half another. Used to detect horizontal cropping. */
    private BufferedImage verticalStripes(int width, int height, Color left, Color right) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(right);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    /** Two horizontal stripes: top half one colour, bottom half another. Used to detect vertical cropping. */
    private BufferedImage horizontalStripes(int width, int height, Color top, Color bottom) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(top);
        g.fillRect(0, 0, width, height / 2);
        g.setColor(bottom);
        g.fillRect(0, height / 2, width, height - height / 2);
        g.dispose();
        return image;
    }

    private int rgbOpaque(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) | 0xFF000000;
    }

    // ---- COVER ----

    @Test
    void coverModeProducesExactCanvasDimensions() {
        BufferedImage source = solidImage(1000, 500, Color.RED);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
    }

    @Test
    void coverModeOnLandscapeSourceCropsSidesNotTopOrBottom() {
        // 1000x500 landscape source onto a 384x384 (square) canvas: the height matches the
        // canvas exactly once scaled, so the excess must come off the left/right edges, not
        // the top/bottom. Split the source into left/right colour halves and confirm a single
        // column has a uniform colour from top to bottom (no vertical cropping occurred).
        BufferedImage source = verticalStripes(1000, 500, Color.RED, Color.BLUE);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
        int topLeft = rgbOpaque(result, 0, 0);
        int bottomLeftSameColumn = rgbOpaque(result, 0, 383);
        assertEquals(topLeft, bottomLeftSameColumn, "vertical cropping should not occur for a landscape source on a matching-height canvas");
        // Left/right edges should show the two different source colours (horizontal cropping occurred).
        int left = rgbOpaque(result, 0, 192);
        int right = rgbOpaque(result, 383, 192);
        assertNotEquals(left, right, "left and right edges should differ since content was cropped from the sides");
    }

    @Test
    void coverModeOnPortraitSourceCropsTopAndBottomNotSides() {
        // 500x1000 portrait source onto a 384x384 (square) canvas: the width matches the canvas
        // exactly once scaled, so the excess must come off the top/bottom, not the left/right.
        BufferedImage source = horizontalStripes(500, 1000, Color.RED, Color.BLUE);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
        int topLeft = rgbOpaque(result, 0, 0);
        int topRightSameRow = rgbOpaque(result, 383, 0);
        assertEquals(topLeft, topRightSameRow, "horizontal cropping should not occur for a portrait source on a matching-width canvas");
        int top = rgbOpaque(result, 192, 0);
        int bottom = rgbOpaque(result, 192, 383);
        assertNotEquals(top, bottom, "top and bottom edges should differ since content was cropped from top/bottom");
    }

    @Test
    void coverModeWithFocalPointZeroKeepsLeftEdgeContent() {
        BufferedImage source = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 200, 100);
        g.dispose();

        BufferedImage result = ImageProcessor.process(source, 100, 100, ImageFit.COVER, 0.0, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        int leftPixel = rgbOpaque(result, 1, 50);
        assertEquals(Color.RED.getRGB(), leftPixel);
    }

    @Test
    void coverModeWithFocalPointOneKeepsRightEdgeContent() {
        BufferedImage source = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 200, 100);
        g.dispose();

        BufferedImage result = ImageProcessor.process(source, 100, 100, ImageFit.COVER, 1.0, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        int rightPixel = rgbOpaque(result, 98, 50);
        assertEquals(Color.BLUE.getRGB(), rightPixel);
    }

    // ---- CONTAIN ----

    @Test
    void containModeLetterboxesWithConfiguredColorOnLongAxis() {
        BufferedImage source = solidImage(1000, 200, Color.BLUE);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.CONTAIN, 0.5, 0.5, "#00FF00", 1.0, 1.0, 1.0, 0.0);

        int corner = result.getRGB(1, 1);
        assertEquals(new Color(0, 255, 0).getRGB(), corner | 0xFF000000);
    }

    @Test
    void containModeOnLandscapeSourceLetterboxesTopAndBottom() {
        // Very wide source on a square canvas: full width is used, so the letterbox bars fall
        // on top/bottom, and the vertical center should show the source colour, not the letterbox.
        BufferedImage source = solidImage(1000, 200, Color.RED);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.CONTAIN, 0.5, 0.5, "#00FF00", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
        Color letterbox = new Color(0, 255, 0);
        assertEquals(letterbox.getRGB(), rgbOpaque(result, 192, 0), "top-center should be letterbox bar");
        assertEquals(letterbox.getRGB(), rgbOpaque(result, 192, 383), "bottom-center should be letterbox bar");
        assertEquals(Color.RED.getRGB(), rgbOpaque(result, 192, 192), "vertical center should show the source image");
    }

    @Test
    void containModeOnPortraitSourceLetterboxesLeftAndRight() {
        // Very tall source on a square canvas: full height is used, so the letterbox bars fall
        // on left/right, and the horizontal center should show the source colour.
        BufferedImage source = solidImage(200, 1000, Color.RED);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.CONTAIN, 0.5, 0.5, "#00FF00", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
        Color letterbox = new Color(0, 255, 0);
        assertEquals(letterbox.getRGB(), rgbOpaque(result, 0, 192), "left-center should be letterbox bar");
        assertEquals(letterbox.getRGB(), rgbOpaque(result, 383, 192), "right-center should be letterbox bar");
        assertEquals(Color.RED.getRGB(), rgbOpaque(result, 192, 192), "horizontal center should show the source image");
    }

    // ---- STRETCH ----

    @Test
    void stretchModeProducesExactCanvasDimensionsIgnoringAspectRatio() {
        BufferedImage source = solidImage(100, 900, Color.GREEN);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.STRETCH, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
    }

    @Test
    void stretchModeFillsFullCanvasWithNoLetterboxOrCrop() {
        // Stretch must fill every pixel with source content - a uniform colour source should
        // produce that colour everywhere, including corners (no letterbox bars introduced).
        BufferedImage source = solidImage(100, 900, Color.GREEN);

        BufferedImage result = ImageProcessor.process(source, 384, 200, ImageFit.STRETCH, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(384, result.getWidth());
        assertEquals(200, result.getHeight());
        assertEquals(Color.GREEN.getRGB(), rgbOpaque(result, 0, 0));
        assertEquals(Color.GREEN.getRGB(), rgbOpaque(result, 383, 199));
    }

    // ---- Exact dimensions across board sizes ----

    @Test
    void exactDimensionsForDefaultThreeByThreeBoard() {
        // Default 3x3 map board: 3*128 x 3*128 = 384x384.
        BufferedImage source = solidImage(640, 480, Color.CYAN);

        BufferedImage cover = ImageProcessor.process(source, 384, 384, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);
        BufferedImage contain = ImageProcessor.process(source, 384, 384, ImageFit.CONTAIN, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);
        BufferedImage stretch = ImageProcessor.process(source, 384, 384, ImageFit.STRETCH, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        for (BufferedImage result : new BufferedImage[]{cover, contain, stretch}) {
            assertEquals(384, result.getWidth());
            assertEquals(384, result.getHeight());
        }
    }

    @Test
    void exactDimensionsForNonSquareBoard() {
        // A 4x2 map board: 4*128 x 2*128 = 512x256.
        BufferedImage source = solidImage(300, 900, Color.MAGENTA);

        BufferedImage cover = ImageProcessor.process(source, 512, 256, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);
        BufferedImage contain = ImageProcessor.process(source, 512, 256, ImageFit.CONTAIN, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);
        BufferedImage stretch = ImageProcessor.process(source, 512, 256, ImageFit.STRETCH, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        for (BufferedImage result : new BufferedImage[]{cover, contain, stretch}) {
            assertEquals(512, result.getWidth());
            assertEquals(256, result.getHeight());
        }
    }

    // ---- Output format ----

    @Test
    void outputIsAlwaysTypeIntRgb() {
        BufferedImage source = solidImage(50, 50, Color.WHITE);

        BufferedImage result = ImageProcessor.process(source, 128, 128, ImageFit.COVER, 0.5, 0.5, "#000000", 1.0, 1.0, 1.0, 0.0);

        assertEquals(BufferedImage.TYPE_INT_RGB, result.getType());
    }

    @Test
    void adjustmentsAndSharpenDoNotAlterOutputDimensions() {
        BufferedImage source = solidImage(200, 400, Color.ORANGE);

        BufferedImage result = ImageProcessor.process(source, 384, 384, ImageFit.COVER, 0.5, 0.5, "#000000", 1.2, 1.1, 0.8, 0.5);

        assertEquals(384, result.getWidth());
        assertEquals(384, result.getHeight());
        assertEquals(BufferedImage.TYPE_INT_RGB, result.getType());
    }

    @Test
    void nonIdentityAdjustmentsActuallyChangePixelValues() {
        // Use a two-colour image (not a flat solid) so sharpen's edge-detecting kernel has
        // actual contrast to act on, in addition to the brightness/contrast/saturation passes.
        BufferedImage source = verticalStripes(200, 200, new Color(180, 90, 60), new Color(60, 120, 200));

        BufferedImage identity = ImageProcessor.process(source, 200, 200, ImageFit.STRETCH, 0.5, 0.5, "#000000",
                1.0, 1.0, 1.0, 0.0);
        BufferedImage adjusted = ImageProcessor.process(source, 200, 200, ImageFit.STRETCH, 0.5, 0.5, "#000000",
                1.5, 1.3, 1.5, 0.5);

        assertEquals(identity.getWidth(), adjusted.getWidth());
        assertEquals(identity.getHeight(), adjusted.getHeight());

        boolean anyPixelDiffers = false;
        for (int y = 0; y < identity.getHeight() && !anyPixelDiffers; y++) {
            for (int x = 0; x < identity.getWidth(); x++) {
                if (rgbOpaque(identity, x, y) != rgbOpaque(adjusted, x, y)) {
                    anyPixelDiffers = true;
                    break;
                }
            }
        }
        assertEquals(true, anyPixelDiffers,
                "non-identity brightness/contrast/saturation/sharpen adjustments should alter at least one pixel "
                        + "compared to an identity-adjustment run on the same source");

        // Also check specific, known sample points on each side of the stripe boundary.
        int identityLeft = rgbOpaque(identity, 20, 100);
        int adjustedLeft = rgbOpaque(adjusted, 20, 100);
        assertNotEquals(identityLeft, adjustedLeft, "left stripe sample point should differ after adjustments");

        int identityRight = rgbOpaque(identity, 180, 100);
        int adjustedRight = rgbOpaque(adjusted, 180, 100);
        assertNotEquals(identityRight, adjustedRight, "right stripe sample point should differ after adjustments");
    }
}
