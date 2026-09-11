package dev.anchorlight.stonelib.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Contract for a single sub-command registered into a {@link CommandRouter}.
 */
public interface SubCommand {

    /** The sub-command keyword, lower-case, no leading slash. */
    String getName();

    /** Permission node required to execute this sub-command, or null for no check. */
    default String getPermission() {
        return null;
    }

    /** One-line usage hint shown in help output. */
    default String getUsage() {
        return "/" + getName();
    }

    /** Short description shown in help output. */
    default String getDescription() {
        return "";
    }

    /**
     * Executes this sub-command. Arguments do NOT include the sub-command name itself.
     */
    void execute(CommandSender sender, String[] args);

    /** Tab-complete suggestions for arguments. Return empty list for no suggestions. */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
