package dev.anchorlight.stonelib.map;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileSlicerTest {

    private BufferedImage quadrantCanvas() {
        BufferedImage canvas = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 128, 128);
        g.setColor(Color.GREEN);
        g.fillRect(128, 0, 128, 128);
        g.setColor(Color.BLUE);
        g.fillRect(0, 128, 128, 128);
        g.setColor(Color.YELLOW);
        g.fillRect(128, 128, 128, 128);
        g.dispose();
        return canvas;
    }

    /**
     * A 3-wide x 2-high board where every tile gets a distinct solid colour, so tile count,
     * dimensions, and position-specific content (including bottom-right) can all be verified.
     */
    private BufferedImage distinctTileCanvas(int boardWidth, int boardHeight) {
        BufferedImage canvas = new BufferedImage(boardWidth * 128, boardHeight * 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        for (int ty = 0; ty < boardHeight; ty++) {
            for (int tx = 0; tx < boardWidth; tx++) {
                int index = ty * boardWidth + tx;
                // Distinct, deterministic colour per tile index.
                int rgb = 0xFF000000 | ((index * 37) % 256) << 16 | ((index * 91) % 256) << 8 | ((index * 53) % 256);
                g.setColor(new Color(rgb, true));
                g.fillRect(tx * 128, ty * 128, 128, 128);
            }
        }
        g.dispose();
        return canvas;
    }

    @Test
    void producesCorrectTileCountForBoardDimensions() {
        assertEquals(4, TileSlicer.slice(quadrantCanvas(), 2, 2).size());

        BufferedImage wide = distinctTileCanvas(3, 2);
        assertEquals(6, TileSlicer.slice(wide, 3, 2).size());

        BufferedImage single = distinctTileCanvas(1, 1);
        assertEquals(1, TileSlicer.slice(single, 1, 1).size());
    }

    @Test
    void eachTileIsExactly128By128() {
        List<BufferedImage> tiles = TileSlicer.slice(distinctTileCanvas(3, 2), 3, 2);
        for (BufferedImage tile : tiles) {
            assertEquals(128, tile.getWidth());
            assertEquals(128, tile.getHeight());
        }
    }

    @Test
    void tilesAreOrderedRowMajorTopLeftFirstNoFlipOrMirror() {
        List<BufferedImage> tiles = TileSlicer.slice(quadrantCanvas(), 2, 2);

        assertEquals(Color.RED.getRGB(), tiles.get(0).getRGB(0, 0) | 0xFF000000);
        assertEquals(Color.GREEN.getRGB(), tiles.get(1).getRGB(0, 0) | 0xFF000000);
        assertEquals(Color.BLUE.getRGB(), tiles.get(2).getRGB(0, 0) | 0xFF000000);
        assertEquals(Color.YELLOW.getRGB(), tiles.get(3).getRGB(0, 0) | 0xFF000000);
    }

    @Test
    void tileZeroTopLeftPixelMatchesSourceCanvasTopLeftPixelExactly() {
        BufferedImage canvas = quadrantCanvas();
        List<BufferedImage> tiles = TileSlicer.slice(canvas, 2, 2);

        int sourcePixel = canvas.getRGB(5, 5) | 0xFF000000;
        int tilePixel = tiles.get(0).getRGB(5, 5) | 0xFF000000;
        assertEquals(sourcePixel, tilePixel);
    }

    @Test
    void bottomRightTileMatchesBottomRightSourceRegionExactly() {
        int boardWidth = 3;
        int boardHeight = 2;
        BufferedImage canvas = distinctTileCanvas(boardWidth, boardHeight);
        List<BufferedImage> tiles = TileSlicer.slice(canvas, boardWidth, boardHeight);

        BufferedImage bottomRight = tiles.get(tiles.size() - 1);
        int sourceOriginX = (boardWidth - 1) * 128;
        int sourceOriginY = (boardHeight - 1) * 128;

        for (int y = 0; y < 128; y += 31) {
            for (int x = 0; x < 128; x += 31) {
                int sourcePixel = canvas.getRGB(sourceOriginX + x, sourceOriginY + y);
                int tilePixel = bottomRight.getRGB(x, y);
                assertEquals(sourcePixel, tilePixel);
            }
        }
    }

    @Test
    void noVerticalFlipOrMirrorAcrossEveryTilePosition() {
        int boardWidth = 3;
        int boardHeight = 2;
        BufferedImage canvas = distinctTileCanvas(boardWidth, boardHeight);
        List<BufferedImage> tiles = TileSlicer.slice(canvas, boardWidth, boardHeight);

        for (int ty = 0; ty < boardHeight; ty++) {
            for (int tx = 0; tx < boardWidth; tx++) {
                BufferedImage tile = tiles.get(ty * boardWidth + tx);
                int originX = tx * 128;
                int originY = ty * 128;
                // Sample asymmetric points (not the tile centre) so a flip or mirror would
                // produce a mismatch instead of accidentally matching.
                for (int[] point : new int[][]{{0, 0}, {127, 0}, {0, 127}, {127, 127}, {10, 100}}) {
                    int sourcePixel = canvas.getRGB(originX + point[0], originY + point[1]);
                    int tilePixel = tile.getRGB(point[0], point[1]);
                    assertEquals(sourcePixel, tilePixel,
                        "mismatch at tile (" + tx + "," + ty + ") point (" + point[0] + "," + point[1] + ")");
                }
            }
        }
    }

    /**
     * A canvas where a single tile is otherwise uniform but has a small asymmetric marker
     * (a 10x10 square) painted only at the top-left corner of that tile's 128x128 region.
     * This lets a test distinguish a correctly-copied tile from one that was flipped/mirrored/
     * rotated internally, which a uniform-solid-colour tile cannot do.
     */
    private BufferedImage tileWithCornerMarkerCanvas(int boardWidth, int boardHeight, int markerTileX, int markerTileY) {
        BufferedImage canvas = new BufferedImage(boardWidth * 128, boardHeight * 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int originX = markerTileX * 128;
        int originY = markerTileY * 128;
        g.setColor(Color.WHITE);
        g.fillRect(originX, originY, 128, 128);
        g.setColor(Color.RED);
        g.fillRect(originX, originY, 10, 10);
        g.dispose();
        return canvas;
    }

    @Test
    void asymmetricCornerMarkerWithinTileStaysAtTopLeftDetectingFlipMirrorOrRotation() {
        int boardWidth = 3;
        int boardHeight = 2;
        int markerTileX = 1;
        int markerTileY = 1;
        BufferedImage canvas = tileWithCornerMarkerCanvas(boardWidth, boardHeight, markerTileX, markerTileY);
        List<BufferedImage> tiles = TileSlicer.slice(canvas, boardWidth, boardHeight);

        BufferedImage tile = tiles.get(markerTileY * boardWidth + markerTileX);

        // The marker must land at the tile's top-left, not top-right (would indicate a
        // horizontal mirror), bottom-left (vertical flip), or bottom-right (180-degree
        // rotation) — all of which a uniform-fill tile cannot distinguish from a correct copy.
        assertEquals(Color.RED.getRGB(), tile.getRGB(0, 0) | 0xFF000000, "marker missing at top-left");
        assertEquals(Color.RED.getRGB(), tile.getRGB(9, 9) | 0xFF000000, "marker missing at top-left interior");
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(127, 0) | 0xFF000000, "top-right should be plain white, not the marker (mirror check)");
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(0, 127) | 0xFF000000, "bottom-left should be plain white, not the marker (vertical flip check)");
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(127, 127) | 0xFF000000, "bottom-right should be plain white, not the marker (180-degree rotation check)");
        assertEquals(Color.WHITE.getRGB(), tile.getRGB(20, 20) | 0xFF000000, "rest of tile should be plain white");
    }

    @Test
    void rejectsCanvasWithWrongDimensions() {
        BufferedImage wrongSized = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> TileSlicer.slice(wrongSized, 2, 2));
    }

    /**
     * Regression test for the C-1 rehydration crash: a processed PNG cached on disk for one board
     * size (e.g. written by a wider/taller board that shares the same postId/mediaId-keyed cache
     * file) must be detected as a mismatch for a board of a different size, via
     * {@link TileSlicer#matchesBoardDimensions}, BEFORE it ever reaches {@link TileSlicer#slice}
     * -- which would otherwise throw {@link IllegalArgumentException} from inside the map render
     * loop.
     */
    @Test
    void matchesBoardDimensionsDetectsMismatchedCachedCanvas() throws Exception {
        // Simulate a cached PNG written for a 3x2 board (3*128 x 2*128 = 384x256).
        BufferedImage cachedForDifferentBoard = distinctTileCanvas(3, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(cachedForDifferentBoard, "png", out);
        byte[] cachedPngBytes = out.toByteArray();

        // Decode it exactly as buildRenderedPostFromCache does when rehydrating.
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(cachedPngBytes));

        // This board is only 2x2 (2*128 x 2*128 = 256x256) -- the cached file belongs to a
        // differently-sized board and must be rejected rather than sliced.
        assertFalse(TileSlicer.matchesBoardDimensions(decoded, 2, 2),
            "a 3x2 board's cached canvas must not match a 2x2 board's expected dimensions");

        // Slicing it for the mismatched board size would have thrown -- confirming the check
        // above is exactly what prevents that from happening during rehydration.
        assertThrows(IllegalArgumentException.class, () -> TileSlicer.slice(decoded, 2, 2));

        // Sanity check: the same decoded canvas DOES match its actual, correctly-sized board.
        assertTrue(TileSlicer.matchesBoardDimensions(decoded, 3, 2));
    }

    @Test
    void matchesBoardDimensionsRejectsNullCanvas() {
        assertFalse(TileSlicer.matchesBoardDimensions(null, 2, 2));
    }
}
