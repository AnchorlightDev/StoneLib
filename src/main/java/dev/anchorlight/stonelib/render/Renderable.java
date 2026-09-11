package dev.anchorlight.stonelib.render;

import java.util.UUID;

/**
 * Something the {@link RenderLoop} draws each tick for the players who can see it.
 *
 * <p>Implementations run off the server thread. They may send packets to the viewers they are
 * given — particles, entity metadata, sounds — and may read the immutable snapshot data on
 * {@link RenderContext}. They must not touch the world, spawn entities, or call anything on the
 * Bukkit API that mutates state; schedule that onto the main thread instead.</p>
 */
public interface Renderable {

    /**
     * The player this belongs to. The loop uses it to drop the renderable when its owner leaves,
     * and to find the anchor position.
     */
    UUID owner();

    /**
     * Distinguishes this from the owner's other renderables.
     *
     * <p>An owner may have several at once — a trail and a hat and wings — and the loop keys them
     * by owner and key together. Two renderables sharing a key replace each other, which is what
     * makes swapping one slot straightforward; the default means a caller that adds two without
     * thinking about keys gets one, not a silent duplicate.</p>
     */
    default String key() {
        return "default";
    }

    /**
     * Draws one frame.
     *
     * @param context the tick number, the owner's last known position, and the viewers that
     *                survived culling — never empty, since the loop skips a renderable nobody can
     *                see
     */
    void render(RenderContext context);

    /**
     * How far away this can be seen, in blocks. Defaults to the loop's configured distance; a
     * renderable with a larger effect can widen it.
     */
    default double viewDistance() {
        return -1;
    }

    /**
     * Whether the renderable should draw at all this tick. Checked before viewers are gathered, so
     * it is the cheap place to honour a per-world or per-server toggle, or a hidden cosmetic.
     */
    default boolean active() {
        return true;
    }

    /**
     * Called once when the loop stops drawing this — the owner left, the cosmetic was cleared, or
     * the plugin is disabling. Use it to despawn anything the renderable showed. Runs on the main
     * thread, so it may touch the world.
     */
    default void cleanUp() {
    }
}
