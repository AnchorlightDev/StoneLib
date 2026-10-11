package dev.anchorlight.stonelib.map;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapImagesTest {

    @Test
    void pngRoundTrips() throws IOException {
        BufferedImage image = new BufferedImage(5, 7, BufferedImage.TYPE_INT_RGB);
        image.setRGB(2, 3, 0x123456);
        BufferedImage back = MapImages.decode(MapImages.png(image));
        assertEquals(5, back.getWidth());
        assertEquals(0x123456, back.getRGB(2, 3) & 0xFFFFFF);
    }

    @Test
    void decodeRejectsGarbage() {
        assertThrows(IOException.class, () -> MapImages.decode(new byte[]{1, 2, 3}));
    }

    @Test
    void screenTilesFitsAndSlices() {
        BufferedImage wide = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        List<BufferedImage> tiles = MapImages.screenTiles(wide, 4, 3, ImageFit.CONTAIN, ImageProcessor.Adjustments.NONE, "#000000");
        assertEquals(12, tiles.size());
        for (BufferedImage tile : tiles) {
            assertEquals(TileSlicer.TILE_SIZE, tile.getWidth());
            assertEquals(TileSlicer.TILE_SIZE, tile.getHeight());
        }
    }

    @Test
    void textCardFillsTheBackground() {
        List<BufferedImage> tiles = MapImages.textCard(2, 1, List.of("Welcome"), new Color(0x102030), Color.WHITE);
        assertEquals(2, tiles.size());
        assertEquals(0x102030, tiles.get(0).getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void imageFitParsesConfigValues() {
        assertEquals(ImageFit.CONTAIN, ImageFit.parse(" contain ").orElseThrow());
        assertTrue(ImageFit.parse("zoom").isEmpty());
        assertTrue(ImageFit.parse(null).isEmpty());
    }

    @Test
    void decodeWithALimitRefusesHugeDimensionsBeforeDecoding() throws IOException {
        byte[] png = MapImages.png(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB));
        assertEquals(300, MapImages.decode(png, 60_000).getWidth());
        IOException e = assertThrows(IOException.class, () -> MapImages.decode(png, 59_999));
        assertTrue(e.getMessage().contains("300x200"));
        assertThrows(IOException.class, () -> MapImages.decode(new byte[]{9, 9, 9}, 100));
    }
}
