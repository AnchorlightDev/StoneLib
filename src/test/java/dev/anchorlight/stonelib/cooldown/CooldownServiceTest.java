package dev.anchorlight.stonelib.cooldown;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownServiceTest {

    @Test
    void sweepDropsExpiredAndKeepsLive() {
        CooldownService cooldowns = new CooldownService();
        UUID gone = UUID.randomUUID();
        UUID live = UUID.randomUUID();
        cooldowns.applyMillis(gone, "dash", 1);
        cooldowns.applyMillis(live, "dash", 60_000);

        long until = System.currentTimeMillis() + 5;
        while (System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        cooldowns.sweepExpired();

        assertEquals(1, cooldowns.trackedPlayers());
        assertTrue(cooldowns.isOnCooldown(live, "dash"));
    }

    @Test
    void applySweepsPeriodically() {
        CooldownService cooldowns = new CooldownService();
        UUID first = UUID.randomUUID();
        cooldowns.applyMillis(first, "dash", 1);
        long until = System.currentTimeMillis() + 5;
        while (System.currentTimeMillis() < until) {
            Thread.onSpinWait();
        }
        for (int i = 0; i < 255; i++) {
            cooldowns.applyMillis(UUID.randomUUID(), "dash", 60_000);
        }
        // The 256th apply sweeps before adding, taking the expired first player with it.
        assertEquals(255, cooldowns.trackedPlayers());
    }
}
