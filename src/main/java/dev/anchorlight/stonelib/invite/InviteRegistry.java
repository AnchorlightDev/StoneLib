package dev.anchorlight.stonelib.invite;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Pending player-to-player invitations - duel challenges, party invites, teleport requests - that
 * expire, cannot be spammed, and are resolved by the recipient.
 *
 * <p>The rules every such feature needs, and tends to get slightly wrong:
 * <ul>
 *   <li>An invite expires after its time to live and then reads as absent.</li>
 *   <li>A sender has at most one outgoing invite at a time; sending another replaces it.</li>
 *   <li>A sender cannot invite the <em>same</em> recipient again until a resend interval has passed,
 *       even after the first invite was denied or expired, so a recipient cannot be flooded.</li>
 *   <li>Self-invites are refused.</li>
 * </ul>
 *
 * <p>Thread-safe and free of Bukkit types; clock injectable for tests.
 */
public final class InviteRegistry {

    /** One pending invitation. */
    public record Invite(UUID sender, UUID recipient, long createdAt, long expiresAt) {
    }

    /** Outcome of {@link #send}. */
    public enum SendResult {
        SENT,
        /** The sender already has a live invite to this recipient. */
        ALREADY_PENDING,
        /** The sender invited this recipient too recently. */
        TOO_SOON,
        /** Sender and recipient are the same player. */
        SELF
    }

    private final Map<UUID, Invite> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long ttlMillis;
    private volatile long resendMillis;

    /**
     * @param ttl    how long an invite stays open
     * @param resend minimum time between two invites from the same sender to the same recipient
     */
    public InviteRegistry(Duration ttl, Duration resend) {
        this(ttl, resend, System::currentTimeMillis);
    }

    /** For tests: supply the clock in milliseconds. */
    public InviteRegistry(Duration ttl, Duration resend, LongSupplier clock) {
        this.clock = clock;
        configure(ttl, resend);
    }

    /** Changes the timings for invites sent from now on, e.g. after a config reload. */
    public void configure(Duration ttl, Duration resend) {
        this.ttlMillis = Math.max(1, ttl.toMillis());
        this.resendMillis = Math.max(0, resend.toMillis());
    }

    public SendResult send(UUID sender, UUID recipient) {
        if (sender.equals(recipient)) {
            return SendResult.SELF;
        }
        long now = clock.getAsLong();
        Invite current = outgoing.get(sender);
        if (current != null && current.recipient().equals(recipient) && current.expiresAt() > now) {
            return SendResult.ALREADY_PENDING;
        }
        Long previous = lastSent.get(pairKey(sender, recipient));
        if (previous != null && now - previous < resendMillis) {
            return SendResult.TOO_SOON;
        }
        outgoing.put(sender, new Invite(sender, recipient, now, now + ttlMillis));
        lastSent.put(pairKey(sender, recipient), now);
        return SendResult.SENT;
    }

    /** Time left before {@code sender} may invite {@code recipient} again. */
    public Duration resendRemaining(UUID sender, UUID recipient) {
        Long previous = lastSent.get(pairKey(sender, recipient));
        if (previous == null) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.max(0, previous + resendMillis - clock.getAsLong()));
    }

    /** Live invites addressed to {@code recipient}, newest first. */
    public List<Invite> incoming(UUID recipient) {
        long now = clock.getAsLong();
        List<Invite> out = new ArrayList<>();
        for (Invite invite : outgoing.values()) {
            if (invite.recipient().equals(recipient) && invite.expiresAt() > now) {
                out.add(invite);
            }
        }
        out.sort(Comparator.comparingLong(Invite::createdAt).reversed());
        return out;
    }

    /** The sender's live outgoing invite, if any. */
    public Optional<Invite> outgoing(UUID sender) {
        Invite invite = outgoing.get(sender);
        return invite != null && invite.expiresAt() > clock.getAsLong() ? Optional.of(invite) : Optional.empty();
    }

    /**
     * Removes and returns the live invite from {@code sender} to {@code recipient}, or the newest
     * invite to {@code recipient} when {@code sender} is null. Use for both accept and deny.
     */
    public Optional<Invite> take(UUID recipient, UUID sender) {
        Invite match;
        if (sender != null) {
            Invite candidate = outgoing.get(sender);
            match = candidate != null && candidate.recipient().equals(recipient)
                    && candidate.expiresAt() > clock.getAsLong() ? candidate : null;
        } else {
            List<Invite> live = incoming(recipient);
            match = live.isEmpty() ? null : live.get(0);
        }
        if (match == null || !outgoing.remove(match.sender(), match)) {
            return Optional.empty();
        }
        return Optional.of(match);
    }

    /** Drops every invite sent by or to {@code player}, e.g. on logout. The resend guard is kept. */
    public void clear(UUID player) {
        outgoing.values().removeIf(invite -> invite.sender().equals(player) || invite.recipient().equals(player));
    }

    /** Removes expired invites and stale resend guards. @return the invites that expired */
    public List<Invite> purgeExpired() {
        long now = clock.getAsLong();
        List<Invite> expired = new ArrayList<>();
        for (Invite invite : outgoing.values()) {
            if (invite.expiresAt() <= now && outgoing.remove(invite.sender(), invite)) {
                expired.add(invite);
            }
        }
        lastSent.values().removeIf(sentAt -> now - sentAt >= resendMillis);
        return expired;
    }

    public void clearAll() {
        outgoing.clear();
        lastSent.clear();
    }

    private static String pairKey(UUID sender, UUID recipient) {
        return sender + ">" + recipient;
    }
}
