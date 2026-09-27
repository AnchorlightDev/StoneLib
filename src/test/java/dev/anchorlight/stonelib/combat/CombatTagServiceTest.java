package dev.anchorlight.stonelib.combat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatTagServiceTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final CombatTagService tags = new CombatTagService(now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void tagReportsEnteringOnlyOnce() {
        List<UUID> entered = new ArrayList<>();
        tags.onEnter(entered::add);

        assertTrue(tags.tag(player, Duration.ofSeconds(10)));
        assertFalse(tags.tag(player, Duration.ofSeconds(10)), "a refresh is not a new entry");
        assertEquals(List.of(player), entered);
    }

    @Test
    void retaggingExtendsButNeverShortens() {
        tags.tag(player, Duration.ofSeconds(10));
        now.addAndGet(5_000);
        tags.tag(player, Duration.ofSeconds(10));
        assertEquals(10_000, tags.remainingMillis(player));

        tags.tag(player, Duration.ofSeconds(1));
        assertEquals(10_000, tags.remainingMillis(player));
    }

    @Test
    void expiryFiresLeaveOnce() {
        List<CombatTagService.LeaveReason> reasons = new ArrayList<>();
        tags.onLeave((id, reason) -> reasons.add(reason));
        tags.tag(player, Duration.ofSeconds(2));

        now.addAndGet(1_999);
        assertTrue(tags.isTagged(player));
        assertTrue(tags.expireDue().isEmpty());

        now.addAndGet(1);
        assertFalse(tags.isTagged(player));
        assertEquals(List.of(player), tags.expireDue());
        assertTrue(tags.expireDue().isEmpty());
        assertEquals(List.of(CombatTagService.LeaveReason.EXPIRED), reasons);
    }

    @Test
    void untagFiresClearedOnlyForALiveTag() {
        List<CombatTagService.LeaveReason> reasons = new ArrayList<>();
        tags.onLeave((id, reason) -> reasons.add(reason));

        assertFalse(tags.untag(player));
        tags.tag(player, Duration.ofSeconds(5));
        assertTrue(tags.untag(player));
        assertEquals(List.of(CombatTagService.LeaveReason.CLEARED), reasons);
    }

    @Test
    void reenteringAfterExpiryCountsAsNew() {
        tags.tag(player, Duration.ofSeconds(1));
        now.addAndGet(2_000);
        assertTrue(tags.tag(player, Duration.ofSeconds(1)));
    }
}
