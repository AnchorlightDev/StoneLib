package dev.anchorlight.stonelib.render;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Everything a {@link Renderable} is given for one frame.
 *
 * <p>{@link #anchor()} is a copy taken from the last main-thread snapshot, so it is safe to read
 * and modify off-thread — it is up to a tick old, which is what interpolation is for.</p>
 *
 * @param tick    a counter incremented every loop tick, for animation phase
 * @param owner   the owning player, or null if they left between the snapshot and this frame
 * @param anchor  the owner's position as of the last snapshot
 * @param viewers the players who can see this renderable, already culled by distance and world
 */
public record RenderContext(long tick, Player owner, Location anchor, List<Player> viewers) {

    /** Seconds elapsed, derived from the tick counter and the loop period. Handy for animation. */
    public double seconds(long periodTicks) {
        return (tick * periodTicks) / 20.0;
    }
}
