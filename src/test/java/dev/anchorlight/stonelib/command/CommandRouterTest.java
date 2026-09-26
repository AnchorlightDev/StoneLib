package dev.anchorlight.stonelib.command;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRouterTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private CommandRouter router;
    private ConsoleCommandSenderMock console;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        console = server.getConsoleSender();
        router = new CommandRouter(plugin, "test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A sub-command guarded by a permission the test can grant or withhold. */
    private SubCommand guarded(String name, String permission) {
        return new SubCommand() {
            public String getName() { return name; }
            public String getPermission() { return permission; }
            public void execute(CommandSender sender, String[] args) { }
            public List<String> tabComplete(CommandSender sender, String[] args) {
                return List.of("secret-argument");
            }
        };
    }

    @Test
    void tabCompletionHidesSubCommandsTheSenderCannotUse() {
        router.register(guarded("open", null));
        router.register(guarded("nuke", "stonelib.test.nuke"));

        var player = server.addPlayer();

        // Advertising every sub-command to everyone and only refusing on use turns tab completion
        // into a catalogue of what somebody else is allowed to do.
        assertEquals(List.of("open"), router.tabComplete(player, new String[]{""}));

        player.addAttachment(plugin, "stonelib.test.nuke", true);
        assertTrue(router.tabComplete(player, new String[]{""}).contains("nuke"));
    }

    @Test
    void argumentsOfAForbiddenSubCommandAreNotCompleted() {
        router.register(guarded("nuke", "stonelib.test.nuke"));
        var player = server.addPlayer();

        // An empty list for both "no such sub-command" and "not yours": completing its arguments
        // would confirm it exists.
        assertTrue(router.tabComplete(player, new String[]{"nuke", ""}).isEmpty());

        player.addAttachment(plugin, "stonelib.test.nuke", true);
        assertEquals(List.of("secret-argument"),
                router.tabComplete(player, new String[]{"nuke", ""}));
    }

    @Test
    void dispatchStillRefusesWithoutPermission() {
        boolean[] executed = {false};
        router.register(new SubCommand() {
            public String getName() { return "nuke"; }
            public String getPermission() { return "stonelib.test.nuke"; }
            public void execute(CommandSender sender, String[] args) { executed[0] = true; }
        });

        router.dispatch(server.addPlayer(), new String[]{"nuke"});

        assertFalse(executed[0], "hiding it from completion must not be the only guard");
    }

    @Test
    void dispatchesToMatchingSubCommand() {
        boolean[] executed = {false};
        router.register(new SubCommand() {
            public String getName() { return "ping"; }
            public void execute(CommandSender sender, String[] args) {
                executed[0] = true;
            }
        });

        boolean handled = router.dispatch(console, new String[]{"ping"});

        assertTrue(handled);
        assertTrue(executed[0]);
    }

    @Test
    void unknownSubCommandIsHandledWithoutThrowing() {
        boolean handled = router.dispatch(console, new String[]{"nope"});

        assertTrue(handled);
        String msg = console.nextMessage();
        assertTrue(msg.contains("Unknown sub-command"));
    }

    @Test
    void tabCompleteListsSubCommandNamesMatchingPrefix() {
        router.register(new SubCommand() {
            public String getName() { return "reload"; }
            public void execute(CommandSender sender, String[] args) {}
        });
        router.register(new SubCommand() {
            public String getName() { return "reset"; }
            public void execute(CommandSender sender, String[] args) {}
        });

        List<String> results = router.tabComplete(console, new String[]{"re"});

        assertEquals(2, results.size());
    }
}
