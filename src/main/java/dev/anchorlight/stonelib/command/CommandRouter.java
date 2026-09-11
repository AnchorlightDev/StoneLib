package dev.anchorlight.stonelib.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a root command's arguments to registered {@link SubCommand} instances.
 */
public class CommandRouter {

    private final JavaPlugin plugin;
    private final String rootLabel;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public CommandRouter(JavaPlugin plugin, String rootLabel) {
        this.plugin = plugin;
        this.rootLabel = rootLabel;
    }

    public void register(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    public Collection<SubCommand> getSubCommands() {
        return Collections.unmodifiableCollection(subCommands.values());
    }

    /**
     * Routes {@code args[0]} to the matching sub-command. Returns true if handled
     * (including "unknown sub-command" and "no permission" cases), matching Bukkit's
     * {@code onCommand} return convention.
     */
    public boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + rootLabel + " <" + String.join("|", subCommands.keySet()) + ">");
            return true;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + args[0]);
            return true;
        }

        String permission = sub.getPermission();
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        sub.execute(sender, remaining);
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> names = new ArrayList<>(subCommands.keySet());
            if (args.length == 1) {
                names.removeIf(name -> !name.startsWith(args[0].toLowerCase()));
            }
            return names;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            return Collections.emptyList();
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        return sub.tabComplete(sender, remaining);
    }
}
