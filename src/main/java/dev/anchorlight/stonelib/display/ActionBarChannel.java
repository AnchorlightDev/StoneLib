package dev.anchorlight.stonelib.display;

import dev.anchorlight.stonelib.scheduler.SchedulerService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An action-bar line that stays up for as long as you want it to, shown to a set of players.
 *
 * <p>The client fades the action bar about three seconds after the last packet, so anything meant
 * to stay visible (a lyric line, a status, a countdown label) has to be re-sent. This channel
 * sends the text to its members as soon as it changes and re-sends it on a short timer while
 * there is text to show. {@link #clear} blanks it for everyone once.
 *
 * <pre>{@code
 * ActionBarChannel lyrics = new ActionBarChannel(scheduler);
 * lyrics.add(player);
 * lyrics.show(Component.text("Amazing grace, how sweet the sound"));
 * lyrics.clear();
 * // onDisable
 * lyrics.close();
 * }</pre>
 *
 * <p>Main thread only. Ported from Gracewave.
 */
public final class ActionBarChannel {

    /** How often the text is re-sent; the client shows an action bar for about 60 ticks. */
    public static final long DEFAULT_REFRESH_TICKS = 40L;

    private final Set<UUID> members = new LinkedHashSet<>();
    private final BukkitTask refresh;
    private Component current;

    public ActionBarChannel(SchedulerService scheduler) {
        this(scheduler, DEFAULT_REFRESH_TICKS);
    }

    public ActionBarChannel(SchedulerService scheduler, long refreshTicks) {
        this.refresh = scheduler.runTimer(this::resend, refreshTicks, refreshTicks);
    }

    /** Adds a member and shows them the current text straight away. */
    public void add(Player player) {
        if (members.add(player.getUniqueId()) && current != null) {
            player.sendActionBar(current);
        }
    }

    /** Removes a member and blanks their action bar. */
    public void remove(Player player) {
        if (members.remove(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
        }
    }

    /** Forgets a disconnecting member without sending them anything. */
    public void quit(Player player) {
        members.remove(player.getUniqueId());
    }

    public boolean contains(Player player) {
        return members.contains(player.getUniqueId());
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    /** The text being shown, or {@code null} when cleared. */
    public Component current() {
        return current;
    }

    /** Shows {@code text} to every member now and keeps it up. {@code null} is the same as {@link #clear}. */
    public void show(Component text) {
        if (text == null) {
            clear();
            return;
        }
        current = text;
        resend();
    }

    /** Stops showing anything and blanks every member's action bar once. */
    public void clear() {
        boolean wasShowing = current != null;
        current = null;
        if (wasShowing) {
            for (Player player : online()) {
                player.sendActionBar(Component.empty());
            }
        }
    }

    /** Clears, forgets every member and stops the refresh timer. */
    public void close() {
        refresh.cancel();
        clear();
        members.clear();
    }

    /** Members who are online right now. */
    public List<Player> online() {
        List<Player> players = new ArrayList<>(members.size());
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private void resend() {
        Component text = current;
        if (text == null || members.isEmpty()) {
            return;
        }
        for (Player player : online()) {
            player.sendActionBar(text);
        }
    }
}
