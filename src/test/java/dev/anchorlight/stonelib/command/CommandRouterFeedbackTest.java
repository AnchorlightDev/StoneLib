package dev.anchorlight.stonelib.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRouterFeedbackTest {

    private ServerMock server;
    private CommandRouter router;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin("StoneLibTest");
        router = new CommandRouter(plugin, "test").feedback(new CommandRouter.Feedback() {
            @Override
            public void usage(CommandSender sender, String rootLabel, Collection<String> names) {
                events.add("usage:" + names);
            }

            @Override
            public void unknown(CommandSender sender, String rootLabel, String input) {
                events.add("unknown:" + input);
            }

            @Override
            public void noPermission(CommandSender sender, SubCommand sub) {
                events.add("perm:" + sub.getName());
            }

            @Override
            public void playersOnly(CommandSender sender, SubCommand sub) {
                events.add("players:" + sub.getName());
            }
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SubCommand sub(String name, String permission, boolean playerOnly, List<String> completions) {
        return new SubCommand() {
            public String getName() { return name; }
            public String getPermission() { return permission; }
            public boolean playerOnly() { return playerOnly; }
            public void execute(CommandSender sender, String[] args) { events.add("ran:" + name); }
            public List<String> tabComplete(CommandSender sender, String[] args) { return completions; }
        };
    }

    @Test
    void customFeedbackReplacesHardcodedReplies() {
        router.register(sub("admin", "test.admin", false, null));
        router.register(sub("me", null, true, null));
        PlayerMock player = server.addPlayer();

        router.dispatch(player, new String[0]);
        router.dispatch(player, new String[]{"nope"});
        router.dispatch(player, new String[]{"admin"});
        router.dispatch(server.getConsoleSender(), new String[]{"me"});
        router.dispatch(player, new String[]{"me"});

        assertEquals(List.of("usage:[me]", "unknown:nope", "perm:admin", "players:me", "ran:me"), events);
        assertTrue(player.nextMessage() == null, "nothing hardcoded was sent");
    }

    @Test
    void noArgumentsHandlerRunsInsteadOfUsage() {
        router.onNoArguments(sub("status", null, false, null));
        router.dispatch(server.getConsoleSender(), new String[0]);
        assertEquals(List.of("ran:status"), events);
    }

    @Test
    void nullCompletionsBecomeAnEmptyList() {
        router.register(sub("x", null, false, null));
        List<String> out = router.tabComplete(server.getConsoleSender(), new String[]{"x", ""});
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }
}
