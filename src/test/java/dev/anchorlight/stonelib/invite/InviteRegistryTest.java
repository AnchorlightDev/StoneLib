package dev.anchorlight.stonelib.invite;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteRegistryTest {

    private final AtomicLong now = new AtomicLong(0);
    private final InviteRegistry invites = new InviteRegistry(Duration.ofSeconds(60), Duration.ofSeconds(30), now::get);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void sendAndAccept() {
        assertEquals(InviteRegistry.SendResult.SENT, invites.send(alice, bob));
        assertEquals(alice, invites.take(bob, null).orElseThrow().sender());
        assertTrue(invites.take(bob, null).isEmpty(), "taken once");
    }

    @Test
    void cannotSpamTheSameRecipient() {
        invites.send(alice, bob);
        assertEquals(InviteRegistry.SendResult.ALREADY_PENDING, invites.send(alice, bob));
        invites.take(bob, alice);
        assertEquals(InviteRegistry.SendResult.TOO_SOON, invites.send(alice, bob), "a deny does not reset the guard");
        now.set(30_000);
        assertEquals(InviteRegistry.SendResult.SENT, invites.send(alice, bob));
    }

    @Test
    void oneOutgoingInvitePerSender() {
        invites.send(alice, bob);
        invites.send(alice, carol);
        assertTrue(invites.incoming(bob).isEmpty());
        assertEquals(1, invites.incoming(carol).size());
    }

    @Test
    void invitesExpire() {
        invites.send(alice, bob);
        now.set(60_000);
        assertTrue(invites.take(bob, alice).isEmpty());
        assertEquals(1, invites.purgeExpired().size());
    }

    @Test
    void selfInvitesAreRefusedAndLogoutClears() {
        assertEquals(InviteRegistry.SendResult.SELF, invites.send(alice, alice));
        invites.send(alice, bob);
        invites.clear(bob);
        assertTrue(invites.outgoing(alice).isEmpty());
    }
}
