package dev.anchorlight.stonelib.display;

import dev.anchorlight.stonelib.scheduler.SchedulerService;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Grows display entities into view and shrinks them away, animated on the client with Paper's
 * display interpolation: the server sends a start and an end transformation and every client
 * tweens between them smoothly, so an animation costs two entity updates, not one per tick.
 *
 * <p>Usage is two steps. Spawn the display already {@link #collapsed collapsed}, then call
 * {@link #appear} with the transformation it should end up at. To hide it, call
 * {@link #disappear}; it can remove the entity once the animation has played.
 *
 * <pre>{@code
 * DisplayAnimator animator = new DisplayAnimator(scheduler);
 * Transformation full = ...;
 * BlockDisplay block = world.spawn(loc, BlockDisplay.class, d -> {
 *     d.setBlock(Material.QUARTZ_BLOCK.createBlockData());
 *     d.setTransformation(DisplayAnimator.collapsed(full, DisplayAnimator.BLOCK_CENTRE));
 * });
 * animator.appear(block, full, 10, DisplayAnimator.stagger(i, count, 8));
 * // later
 * animator.disappear(block, DisplayAnimator.BLOCK_CENTRE, 8, 0, true);
 * }</pre>
 *
 * <p><b>Why the extra tick.</b> A transformation set in the same tick an entity is spawned is
 * sent with the spawn and treated by the client as the starting state - there is nothing to
 * interpolate from. {@link #appear} therefore applies the end state one tick later. Staggering
 * uses the client-side interpolation delay, so a whole wall of blocks can be sent in one tick and
 * still assemble one after another.
 *
 * <p>Pivots are points in the display's own model space: a block display's model spans the unit
 * cube from its origin, so {@link #BLOCK_CENTRE} grows a block from its middle; an item display's
 * model is centred on its origin, and a text display's on the bottom-centre of its text, so
 * {@link #ORIGIN} suits both.
 */
public final class DisplayAnimator {

    /** The middle of a block display's unit cube. */
    public static final Vector3f BLOCK_CENTRE = new Vector3f(0.5f, 0.5f, 0.5f);
    /** The model origin, right for item and text displays. */
    public static final Vector3f ORIGIN = new Vector3f();
    /**
     * The scale a collapsed display is given. Exactly zero is avoided because some clients treat
     * a degenerate matrix as "do not render" for a frame before interpolating, which shows as a
     * flicker.
     */
    public static final float COLLAPSED_SCALE = 0.001f;

    private final SchedulerService scheduler;

    public DisplayAnimator(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Interpolates {@code display} to {@code target} over {@code durationTicks}, starting
     * {@code delayTicks} after the client receives it. Applied on the next server tick so a display
     * spawned this tick animates instead of snapping.
     */
    public void appear(Display display, Transformation target, int durationTicks, int delayTicks) {
        scheduler.runSyncLater(() -> animateTo(display, target, durationTicks, delayTicks), 1L);
    }

    /** {@link #appear} for several displays sharing one target each, in order, staggered over {@code spreadTicks}. */
    public void appearAll(List<? extends Display> displays, List<Transformation> targets, int durationTicks, int spreadTicks) {
        if (displays.size() != targets.size()) {
            throw new IllegalArgumentException("Need one target per display");
        }
        scheduler.runSyncLater(() -> {
            for (int i = 0; i < displays.size(); i++) {
                animateTo(displays.get(i), targets.get(i), durationTicks, stagger(i, displays.size(), spreadTicks));
            }
        }, 1L);
    }

    /**
     * Shrinks {@code display} about {@code pivot} to nothing over {@code durationTicks}, after
     * {@code delayTicks}, and removes it afterwards when {@code remove} is true.
     */
    public void disappear(Display display, Vector3f pivot, int durationTicks, int delayTicks, boolean remove) {
        if (!display.isValid()) {
            return;
        }
        animateTo(display, collapsed(display.getTransformation(), pivot), durationTicks, delayTicks);
        if (remove) {
            scheduler.runSyncLater(() -> {
                if (display.isValid()) {
                    display.remove();
                }
            }, Math.max(1L, durationTicks + delayTicks + 1L));
        }
    }

    /** {@link #disappear} for several displays, staggered in order over {@code spreadTicks}. */
    public void disappearAll(List<? extends Display> displays, Vector3f pivot, int durationTicks, int spreadTicks, boolean remove) {
        for (int i = 0; i < displays.size(); i++) {
            disappear(displays.get(i), pivot, durationTicks, stagger(i, displays.size(), spreadTicks), remove);
        }
    }

    /** How long {@link #appearAll} or {@link #disappearAll} takes end to end, for scheduling what follows. */
    public static int totalTicks(int durationTicks, int spreadTicks) {
        return durationTicks + Math.max(0, spreadTicks) + 1;
    }

    /** Sets the interpolation and the end state in one go, for callers that manage the tick themselves. */
    public static void animateTo(Display display, Transformation target, int durationTicks, int delayTicks) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(Math.max(0, delayTicks));
        display.setInterpolationDuration(Math.max(0, durationTicks));
        display.setTransformation(target);
    }

    /**
     * {@code target} shrunk to {@link #COLLAPSED_SCALE} about {@code pivot}: the pivot point stays
     * exactly where it is in the full-size transformation, everything else collapses onto it.
     */
    public static Transformation collapsed(Transformation target, Vector3f pivot) {
        Vector3f scale = target.getScale();
        // Where the pivot lands at full size, relative to the translation:
        // translation + left * (scale * (right * pivot)).
        Vector3f atFull = new Vector3f(pivot);
        new Quaternionf(target.getRightRotation()).transform(atFull);
        atFull.mul(scale);
        new Quaternionf(target.getLeftRotation()).transform(atFull);
        // And at the collapsed scale.
        Vector3f atCollapsed = new Vector3f(pivot);
        new Quaternionf(target.getRightRotation()).transform(atCollapsed);
        atCollapsed.mul(new Vector3f(scale).mul(COLLAPSED_SCALE));
        new Quaternionf(target.getLeftRotation()).transform(atCollapsed);

        Vector3f translation = new Vector3f(target.getTranslation()).add(atFull).sub(atCollapsed);
        return new Transformation(translation, new Quaternionf(target.getLeftRotation()),
                new Vector3f(scale).mul(COLLAPSED_SCALE), new Quaternionf(target.getRightRotation()));
    }

    /**
     * The delay for item {@code index} of {@code count} when their starts are spread evenly over
     * {@code spreadTicks}: the first starts at once, the last at {@code spreadTicks}.
     */
    public static int stagger(int index, int count, int spreadTicks) {
        if (count <= 1 || spreadTicks <= 0) {
            return 0;
        }
        return Math.round((float) index * spreadTicks / (count - 1));
    }
}
