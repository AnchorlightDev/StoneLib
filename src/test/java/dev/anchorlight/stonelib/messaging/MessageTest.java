package dev.anchorlight.StoneLib.messaging;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTest {

    @Test
    void roundTripsThroughEncoding() {
        Message original = new Message("cosmetic-changed", "hub-1", "0f3c-uuid-ish");

        Message decoded = Message.decode(original.encode());

        assertNotNull(decoded);
        assertEquals(original, decoded);
    }

    @Test
    void nullPayloadBecomesEmptyString() {
        Message message = new Message("type", "origin", null);

        assertEquals("", message.payload());
        assertEquals("", Message.decode(message.encode()).payload());
    }

    @Test
    void blankTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Message("  ", "origin", "payload"));
    }

    @Test
    void decodeReturnsNullForGarbage() {
        // A remote sender is not trusted: malformed bytes must not throw into the channel listener.
        assertNull(Message.decode(new byte[]{1, 2, 3}));
        assertNull(Message.decode(new byte[0]));
        assertNull(Message.decode(null));
    }

    @Test
    void decodeReturnsNullForTruncatedMessage() {
        byte[] encoded = new Message("type", "origin", "payload").encode();
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 3);

        assertNull(Message.decode(truncated));
    }

    @Test
    void oversizedPayloadIsRejectedAtEncodeTime() {
        Message huge = new Message("type", "origin", "x".repeat(Message.MAX_ENCODED_BYTES + 1));

        // Better to fail loudly here than to have the packet silently dropped in transit.
        assertThrows(IllegalArgumentException.class, huge::encode);
    }
}
