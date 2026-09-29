package dev.anchorlight.stonelib.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a root command's arguments to registered {@link SubCommand} instances.
 *
 * <p>The router's own replies (usage, unknown sub-command, no permission, players only) default to
 * plain English. A plugin whose messages live in config replaces them with {@link #feedback}, so no
 * player-facing string is hardcoded in the router.
 */
public class CommandRouter {

    /**
     * The router's own replies. Override any subset; the defaults are the router's original
     * messages.
     */
    public interface Feedback {

        /** No arguments and no {@linkplain #onNoArguments default handler}. */
        default void usage(CommandSender sender, String rootLabel, Collection<String> visibleNames) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + rootLabel + " <" + String.join("|", visibleNames) + ">");
        }

        /** {@code input} matched no sub-command. */
        default void unknown(CommandSender sender, String rootLabel, String input) {
            sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + input);
        }

        default void noPermission(CommandSender sender, SubCommand sub) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
        }

        /** A {@linkplain SubCommand#playerOnly() player-only} sub-command run from the console. */
        default void playersOnly(CommandSender sender, SubCommand sub) {
            sender.sendMessage(ChatColor.RED + "Only players can use that.");
        }
    }

    private static final Feedback DEFAULT_FEEDBACK = new Feedback() {
    };

    private final JavaPlugin plugin;
    private final String rootLabel;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private Feedback feedback = DEFAULT_FEEDBACK;
    private SubCommand noArguments;

    public CommandRouter(JavaPlugin plugin, String rootLabel) {
        this.plugin = plugin;
        this.rootLabel = rootLabel;
    }

    /** Replaces the router's built-in replies. Null restores the defaults. */
    public CommandRouter feedback(Feedback feedback) {
        this.feedback = feedback == null ? DEFAULT_FEEDBACK : feedback;
        return this;
    }

    /**
     * Runs {@code handler} when the root command is used with no arguments, instead of printing
     * usage - e.g. {@code /pvp} showing the sender's status. Its permission and player-only rules
     * apply as for any sub-command. It is not registered by name unless you also {@link #register}
     * it.
     */
    public CommandRouter onNoArguments(SubCommand handler) {
        this.noArguments = handler;
        return this;
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
            if (noArguments != null) {
                run(sender, noArguments, args);
            } else {
                feedback.usage(sender, rootLabel, visibleNames(sender));
            }
            return true;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            feedback.unknown(sender, rootLabel, args[0]);
            return true;
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        run(sender, sub, remaining);
        return true;
    }

    private void run(CommandSender sender, SubCommand sub, String[] args) {
        if (!allowed(sender, sub)) {
            feedback.noPermission(sender, sub);
            return;
        }
        if (sub.playerOnly() && !(sender instanceof Player)) {
            feedback.playersOnly(sender, sub);
            return;
        }
        sub.execute(sender, args);
    }

    /** Sub-command names {@code sender} is allowed to run, in registration order. */
    public List<String> visibleNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, SubCommand> entry : subCommands.entrySet()) {
            if (allowed(sender, entry.getValue())) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    /**
     * Completions the sender is actually allowed to act on.
     *
     * <p>Filtered by permission, which {@link #dispatch} already enforces. Without this the router
     * advertises every sub-command to everyone and only refuses on use - so a moderator holding
     * one permission is shown the whole staff vocabulary, including the destructive entries they
     * cannot run. Tab completion is a list of what you can do; it should not be a catalogue of
     * what somebody else can.
     */
    /** Whether {@code sender} may use {@code sub}. A null permission means anyone may. */
    private static boolean allowed(CommandSender sender, SubCommand sub) {
        String permission = sub.getPermission();
        return permission == null || sender.hasPermission(permission);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> names = visibleNames(sender);
            if (args.length == 1) {
                names.removeIf(name -> !name.startsWith(args[0].toLowerCase()));
            }
            return names;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null || !allowed(sender, sub)) {
            // Same answer for "no such sub-command" and "not yours": completing the arguments of
            // something they cannot run would confirm it exists.
            return Collections.emptyList();
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        List<String> suggestions = sub.tabComplete(sender, remaining);
        return suggestions == null ? Collections.emptyList() : suggestions;
    }
}
