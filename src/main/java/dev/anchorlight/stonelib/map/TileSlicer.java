package dev.anchorlight.stonelib.map;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Slices a full-canvas {@link BufferedImage} produced by {@link ImageProcessor} into ordered
 * 128x128 map tiles.
 *
 * <p>Tiles are returned row-major (index {@code = tileY * boardWidth + tileX}), with tile
 * {@code (0,0)} being the actual top-left 128x128 region of the source canvas. No vertical flip
 * or horizontal mirror is applied — pixel content is copied verbatim from the source region.</p>
 *
 * <p>Pure {@code java.awt.image} logic, safe off the main thread. Ported from PixelPost.</p>
 */
public final class TileSlicer {

    /** Pixels per side of one map. */
    public static final int TILE_SIZE = 128;

    private TileSlicer() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /**
     * Slices {@code canvas} into {@code boardWidth * boardHeight} tiles of exactly
     * {@value #TILE_SIZE}x{@value #TILE_SIZE} pixels each.
     *
     * @param canvas      source canvas, must be exactly {@code boardWidth*128 x boardHeight*128}
     * @param boardWidth  number of tiles wide (must be &gt; 0)
     * @param boardHeight number of tiles high (must be &gt; 0)
     * @return tiles in row-major order, tile {@code (0,0)} = top-left of the source canvas
     */
    public static List<BufferedImage> slice(BufferedImage canvas, int boardWidth, int boardHeight) {
        if (boardWidth <= 0 || boardHeight <= 0) {
            throw new IllegalArgumentException("boardWidth and boardHeight must be positive");
        }
        int expectedWidth = boardWidth * TILE_SIZE;
        int expectedHeight = boardHeight * TILE_SIZE;
        if (canvas.getWidth() != expectedWidth || canvas.getHeight() != expectedHeight) {
            throw new IllegalArgumentException("Canvas must be exactly " + expectedWidth + "x" + expectedHeight
                + " but was " + canvas.getWidth() + "x" + canvas.getHeight());
        }

        List<BufferedImage> tiles = new ArrayList<>(boardWidth * boardHeight);
        for (int tileY = 0; tileY < boardHeight; tileY++) {
            for (int tileX = 0; tileX < boardWidth; tileX++) {
                BufferedImage tile = canvas.getSubimage(tileX * TILE_SIZE, tileY * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                BufferedImage copy = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
                copy.getGraphics().drawImage(tile, 0, 0, null);
                tiles.add(copy);
            }
        }
        return tiles;
    }

    /**
     * Returns whether {@code canvas}'s actual pixel dimensions are exactly
     * {@code boardWidth*128 x boardHeight*128} -- i.e. whether it is safe to pass to
     * {@link #slice} for a board of this size without triggering its
     * {@link IllegalArgumentException} dimension check. Used by callers that decode a cached
     * canvas from disk (which may have been written for a differently-sized board) to validate it
     * up front instead of letting the exception reach {@link #slice}.
     */
    public static boolean matchesBoardDimensions(BufferedImage canvas, int boardWidth, int boardHeight) {
        if (canvas == null) {
            return false;
        }
        return canvas.getWidth() == boardWidth * TILE_SIZE && canvas.getHeight() == boardHeight * TILE_SIZE;
    }
}
