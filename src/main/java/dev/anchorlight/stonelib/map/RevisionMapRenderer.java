package dev.anchorlight.stonelib.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Draws one 128x128 image onto a {@link MapView}, and redraws only when the image's revision
 * changes. Paper calls {@link #render} every tick for every viewer; decoding and re-drawing each
 * time would be wasted work, so the image supplier is consulted only on the first render and
 * whenever the revision supplier reports a new value.
 *
 * <p>The first render always draws, whatever the revision is, so a map shows its image after a
 * restart even when the revision never changes.
 *
 * <p>The renderer is non-contextual (every player sees the same image). Only
 * {@link MapCanvas#drawImage} is used - no raw palette bytes.
 *
 * <pre>{@code
 * MapView view = Bukkit.createMap(world);
 * view.getRenderers().forEach(view::removeRenderer);
 * view.addRenderer(RevisionMapRenderer.ofImage(() -> currentTile(index), revision::get));
 * }</pre>
 *
 * <p>Ported from PixelPost.
 */
public final class RevisionMapRenderer extends MapRenderer {

    private final Supplier<BufferedImage> imageSupplier;
    private final LongSupplier currentRevisionSupplier;
    private volatile long lastRenderedRevision = Long.MIN_VALUE;
    private volatile boolean rendered;

    private RevisionMapRenderer(Supplier<BufferedImage> imageSupplier, LongSupplier currentRevisionSupplier) {
        super(false);
        this.imageSupplier = imageSupplier;
        this.currentRevisionSupplier = currentRevisionSupplier;
    }

    /**
     * A renderer whose tile is supplied as encoded PNG bytes, decoded only when a redraw is due.
     * Suits tiles kept in a cache as files or byte arrays.
     */
    public static RevisionMapRenderer ofPng(Supplier<byte[]> tilePngSupplier, LongSupplier revision) {
        return new RevisionMapRenderer(() -> decodePng(tilePngSupplier.get()), revision);
    }

    /** A renderer whose tile is supplied as an image (any size; Paper clips to 128x128). */
    public static RevisionMapRenderer ofImage(Supplier<BufferedImage> imageSupplier, LongSupplier revision) {
        return new RevisionMapRenderer(imageSupplier, revision);
    }

    /** A renderer that draws one fixed image, once. */
    public static RevisionMapRenderer fixed(BufferedImage image) {
        return new RevisionMapRenderer(() -> image, () -> 0L);
    }

    /** A renderer driven by a revision counter you bump whenever the tile changes. */
    public static RevisionMapRenderer ofImage(Supplier<BufferedImage> imageSupplier, AtomicLong revision) {
        return new RevisionMapRenderer(imageSupplier, revision::get);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        redrawIfRevisionChanged(image -> canvas.drawImage(0, 0, image));
    }

    /**
     * The revision-gated decision on its own, for tests: {@code draw} is called in place of
     * {@code canvas.drawImage} whenever a redraw is due. Returns whether it drew.
     */
    public boolean redrawIfRevisionChanged(Consumer<BufferedImage> draw) {
        long currentRevision = currentRevisionSupplier.getAsLong();
        if (rendered && currentRevision == lastRenderedRevision) {
            return false;
        }
        BufferedImage image = imageSupplier.get();
        if (image == null) {
            return false;
        }
        draw.accept(image);
        lastRenderedRevision = currentRevision;
        rendered = true;
        return true;
    }

    private static BufferedImage decodePng(byte[] png) {
        if (png == null) {
            return null;
        }
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
            if (decoded == null) {
                throw new IOException("Not a readable image");
            }
            return decoded;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to decode tile PNG", e);
        }
    }
}
