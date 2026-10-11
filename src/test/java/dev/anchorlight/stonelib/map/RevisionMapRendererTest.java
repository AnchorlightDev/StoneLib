package dev.anchorlight.stonelib.map;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevisionMapRendererTest {

    @Test
    void redrawsOnlyWhenRevisionChanges() throws Exception {
        BufferedImage tile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(tile, "png", baos);
        byte[] tileBytes = baos.toByteArray();

        AtomicLong revision = new AtomicLong(1);
        AtomicInteger drawCount = new AtomicInteger();

        RevisionMapRenderer renderer = RevisionMapRenderer.ofPng(() -> tileBytes, revision::get);

        renderer.redrawIfRevisionChanged(image -> drawCount.incrementAndGet());
        renderer.redrawIfRevisionChanged(image -> drawCount.incrementAndGet());
        assertEquals(1, drawCount.get());

        revision.set(2);
        renderer.redrawIfRevisionChanged(image -> drawCount.incrementAndGet());
        assertEquals(2, drawCount.get());
    }

    /**
     * Regression test for the critical placeholder-never-drawn bug: a content-less board's revision
     * supplier is {@code RenderCoordinator#activatedRevision}, which reports {@code -1} as its
     * "nothing activated yet" sentinel. If {@code lastRenderedRevision} were initialized to that same
     * {@code -1} value, the very first render tick would see {@code currentRevision == lastRenderedRevision}
     * (both {@code -1}) and skip the draw entirely -- meaning the tile supplier (which falls back to
     * the placeholder image) would never be invoked and the map would stay blank forever, since the
     * revision never changes for a board that never gets real content. This test pins a revision
     * supplier that stays at {@code -1} throughout (exactly the content-less-board scenario) and
     * asserts the very first render still draws -- this would have failed against the old
     * {@code -1}-initialized renderer (drawCount would stay 0 instead of becoming 1).
     */
    @Test
    void firstRenderAlwaysDrawsEvenWhenRevisionSupplierReportsPlaceholderSentinel() throws Exception {
        BufferedImage tile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(tile, "png", baos);
        byte[] tileBytes = baos.toByteArray();

        AtomicInteger drawCount = new AtomicInteger();
        // Simulates RenderCoordinator#activatedRevision for a board with no active content: always -1.
        RevisionMapRenderer renderer = RevisionMapRenderer.ofPng(() -> tileBytes, () -> -1L);

        renderer.redrawIfRevisionChanged(image -> drawCount.incrementAndGet());
        assertEquals(1, drawCount.get());

        // Subsequent renders with the same unchanging -1 revision must not redraw again.
        renderer.redrawIfRevisionChanged(image -> drawCount.incrementAndGet());
        assertEquals(1, drawCount.get());
    }
}
