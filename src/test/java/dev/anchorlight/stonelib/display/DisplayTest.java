package dev.anchorlight.stonelib.display;

import dev.anchorlight.stonelib.region.Cuboid;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayTest {

    // --- ArgbColours -------------------------------------------------------------

    @Test
    void parsesHexWithAndWithoutAlpha() {
        assertEquals(Optional.of(0x8033CCFF), ArgbColours.parse("#33ccff"));
        assertEquals(Optional.of(0x8033CCFF), ArgbColours.parse("33CCFF"));
        assertEquals(Optional.of(0xFF33CCFF), ArgbColours.parse("#FF33CCFF"));
    }

    @Test
    void parsesDyeNamesAndSpellings() {
        assertEquals(Optional.of(0x803AB3DA), ArgbColours.parse("light-blue"));
        assertEquals(ArgbColours.parse("light_gray"), ArgbColours.parse("Light Grey"));
        assertTrue(ArgbColours.names().contains("purple"));
    }

    @Test
    void rejectsInvalidColours() {
        assertTrue(ArgbColours.parse("#12345").isEmpty());
        assertTrue(ArgbColours.parse("chartreuse").isEmpty());
        assertTrue(ArgbColours.parse(null).isEmpty());
    }

    @Test
    void formatRoundTrips() {
        assertEquals(Optional.of(0x4011AA22), ArgbColours.parse(ArgbColours.format(0x4011AA22)));
        assertEquals("#4011AA22", ArgbColours.format(0x4011AA22));
    }

    // --- TintPanel ---------------------------------------------------------------

    @Test
    void panelAlongXFacesBothWaysCentredInZ() {
        List<TintPanel.Anchor> anchors = TintPanel.anchors(new Cuboid("world", 10, 64, 5, 12, 67, 5));
        assertEquals(2, anchors.size());
        assertEquals(new TintPanel.Anchor(10, 64, 5.5 + TintPanel.FACE_OFFSET, 0f), anchors.get(0));
        assertEquals(13 - TintPanel.EDGE_EPSILON, anchors.get(1).x(), 1e-9);
        assertEquals(5.5 - TintPanel.FACE_OFFSET, anchors.get(1).z(), 1e-9);
        assertEquals(180f, anchors.get(1).yaw());
    }

    @Test
    void panelAlongZWhenThinInX() {
        List<TintPanel.Anchor> anchors = TintPanel.anchors(new Cuboid("world", 5, 64, 10, 5, 67, 13));
        assertEquals(new TintPanel.Anchor(5.5 - TintPanel.FACE_OFFSET, 64, 10, 90f), anchors.get(0));
        assertEquals(-90f, anchors.get(1).yaw());
        assertEquals(14 - TintPanel.EDGE_EPSILON, anchors.get(1).z(), 1e-9);
    }

    @Test
    void panelAnchorsStayInsideCuboidChunks() {
        Cuboid bounds = new Cuboid("world", 0, 64, 0, 15, 66, 15); // exactly chunk 0
        for (TintPanel.Anchor anchor : TintPanel.anchors(bounds)) {
            assertEquals(0, (int) Math.floor(anchor.x()) >> 4);
            assertEquals(0, (int) Math.floor(anchor.z()) >> 4);
        }
    }

    @Test
    void panelTransformationMapsBackgroundOntoWidthByHeight() {
        Transformation transformation = TintPanel.transformation(3, 4);
        float scaleX = transformation.getScale().x();
        float scaleY = transformation.getScale().y();
        float shiftX = transformation.getTranslation().x();
        // background spans x [-0.05, 0.075] and y [0, 0.25] at scale 1
        assertEquals(0f, -0.05f * scaleX + shiftX, 1e-5);
        assertEquals(3f, 0.075f * scaleX + shiftX, 1e-5);
        assertEquals(4f, 0.25f * scaleY, 1e-5);
    }
}
