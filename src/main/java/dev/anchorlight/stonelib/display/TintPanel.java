package dev.anchorlight.stonelib.display;

import dev.anchorlight.stonelib.region.Cuboid;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A flat, translucent coloured panel filling a {@link Cuboid}'s cross-section - a coloured portal
 * surface, a force field, a highlighted doorway - built from {@link TextDisplay} backgrounds, so any
 * ARGB colour works and nothing blocks movement or touches the world's blocks.
 *
 * <p>The panel runs along the cuboid's longer horizontal axis, centred in its thickness, and covers
 * its full height. Text displays only render from the front, so a panel is two displays facing
 * opposite ways, nudged apart so they never z-fight.
 *
 * <p>Panels are spawned <b>non-persistent</b>: they are never written to the world save, disappear
 * when their chunk unloads, and must be spawned again when it loads (check {@link #anchorsLoaded}
 * from a {@code ChunkLoadEvent}). Nothing is left behind if the plugin is removed.
 *
 * <pre>{@code
 * List<TextDisplay> panel = TintPanel.spawn(world, gate, ArgbColours.parse("#8033ccff").orElseThrow());
 * // later
 * panel.forEach(TextDisplay::remove);
 * }</pre>
 */
public final class TintPanel {

    /** Where one of the panel's two displays sits, and which way it faces. */
    public record Anchor(double x, double y, double z, float yaw) {
    }

    /** Gap between the two faces so they never z-fight. */
    static final double FACE_OFFSET = 0.01;
    /** Keeps the far anchor inside the cuboid's own chunk, so chunk-load respawning finds it. */
    static final double EDGE_EPSILON = 0.001;

    private TintPanel() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** Spawns both faces and returns them. The cuboid's world must be {@code world}. */
    public static List<TextDisplay> spawn(World world, Cuboid bounds, int argb) {
        Color colour = Color.fromARGB(argb);
        Transformation transformation = transformation(width(bounds), bounds.sizeY());
        List<TextDisplay> faces = new ArrayList<>(2);
        for (Anchor anchor : anchors(bounds)) {
            Location location = new Location(world, anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), 0f);
            faces.add(world.spawn(location, TextDisplay.class, face -> {
                face.setPersistent(false);
                face.text(Component.space());
                face.setDefaultBackground(false);
                face.setBackgroundColor(colour);
                face.setShadowed(false);
                face.setBillboard(Display.Billboard.FIXED);
                face.setBrightness(new Display.Brightness(15, 15));
                face.setTransformation(transformation);
            }));
        }
        return faces;
    }

    /** True when every chunk the panel's displays live in is loaded. */
    public static boolean anchorsLoaded(World world, Cuboid bounds) {
        for (Anchor anchor : anchors(bounds)) {
            if (!world.isChunkLoaded((int) Math.floor(anchor.x()) >> 4, (int) Math.floor(anchor.z()) >> 4)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The two display positions. At yaw 0 a text display faces +Z and its text runs towards +X;
     * each 90 degrees of yaw rotates both, which is what places each face's left edge.
     */
    public static List<Anchor> anchors(Cuboid bounds) {
        if (bounds.sizeX() >= bounds.sizeZ()) {
            double z = bounds.minZ() + bounds.sizeZ() / 2.0;
            return List.of(
                    new Anchor(bounds.minX(), bounds.minY(), z + FACE_OFFSET, 0f),
                    new Anchor(bounds.maxX() + 1 - EDGE_EPSILON, bounds.minY(), z - FACE_OFFSET, 180f));
        }
        double x = bounds.minX() + bounds.sizeX() / 2.0;
        return List.of(
                new Anchor(x - FACE_OFFSET, bounds.minY(), bounds.minZ(), 90f),
                new Anchor(x + FACE_OFFSET, bounds.minY(), bounds.maxZ() + 1 - EDGE_EPSILON, -90f));
    }

    /**
     * At scale 1 a single-space text display's background spans x in [-0.05, 0.075] and y in
     * [0, 0.25] blocks. Scaling by (8w, 4h) and shifting by 0.4w maps it exactly onto [0, w] x [0, h].
     */
    static Transformation transformation(int width, int height) {
        return new Transformation(
                new Vector3f(0.4f * width, 0f, 0f), new AxisAngle4f(),
                new Vector3f(8f * width, 4f * height, 1f), new AxisAngle4f());
    }

    private static int width(Cuboid bounds) {
        return Math.max(bounds.sizeX(), bounds.sizeZ());
    }
}
