package dev.anchorlight.StoneLib.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.command.ConsoleCommandSenderMock;
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
