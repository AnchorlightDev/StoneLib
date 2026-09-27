package dev.anchorlight.stonelib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.Arrays;
import java.util.Objects;

/**
 * Clickable chat buttons - {@code [Accept]}, {@code [Enable PvP]} - built in code, so the command
 * they run is never assembled from text a player controls.
 *
 * <p>The label and hover are ordinary components, usually parsed from a config template, so an
 * admin can restyle a button without touching what it does:
 *
 * <pre>{@code
 * Component accept = Buttons.runCommand(
 *         messages.getNamedUnprefixed("button_accept"),
 *         "/duel accept " + challenger.getName(),
 *         messages.getNamedUnprefixed("button_accept_hover"));
 * player.sendMessage(messages.getNamed("duel_request", "player", challenger.getName())
 *         .append(Component.space()).append(accept));
 * }</pre>
 *
 * <p>A player name is safe to put in a command here: Minecraft names are {@code [A-Za-z0-9_]}, and
 * the command is a click-event value rather than markup, so it is never parsed as MiniMessage.
 * Anything free-form still does not belong in a command string.
 */
public final class Buttons {

    private Buttons() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /** A button that runs {@code command} as the clicking player. A leading slash is added if missing. */
    public static Component runCommand(Component label, String command, Component hover) {
        return decorate(label, ClickEvent.runCommand(slash(command)), hover);
    }

    /** A button that puts {@code command} into the player's chat box for them to finish. */
    public static Component suggestCommand(Component label, String command, Component hover) {
        return decorate(label, ClickEvent.suggestCommand(slash(command)), hover);
    }

    /** Joins buttons with {@code separator} between them, skipping nulls. */
    public static Component row(Component separator, Component... buttons) {
        Component[] present = Arrays.stream(buttons).filter(Objects::nonNull).toArray(Component[]::new);
        return Component.join(JoinConfiguration.separator(separator == null ? Component.space() : separator), present);
    }

    private static Component decorate(Component label, ClickEvent click, Component hover) {
        Component base = label == null ? Component.empty() : label;
        Component withClick = base.clickEvent(click);
        return hover == null || hover.equals(Component.empty())
                ? withClick
                : withClick.hoverEvent(HoverEvent.showText(hover));
    }

    private static String slash(String command) {
        if (command == null || command.isEmpty()) {
            return "/";
        }
        return command.charAt(0) == '/' ? command : "/" + command;
    }
}
