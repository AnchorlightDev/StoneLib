package dev.anchorlight.StoneLib.messaging;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * One message on a {@link MessageBus}.
 *
 * <p>Deliberately small: a {@code type} the receiver switches on, the {@code origin} server that
 * sent it, and an opaque {@code payload} string. Anything richer is the caller's encoding — a UUID,
 * a comma-joined pair, a JSON blob.</p>
 *
 * <p>Messages are a fast path for telling other servers that something changed. They are not
 * storage: they can be dropped when no player is connecting the two servers, so the database stays
 * the source of truth and a message only ever prompts a re-read.</p>
 */
public record Message(String type, String origin, String payload) {

    /** Cap on an encoded message, well under the plugin-message frame limit. */
    static final int MAX_ENCODED_BYTES = 30_000;

    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        if (payload == null) {
            payload = "";
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("Message type must not be blank");
        }
    }

    /** Encodes the message for transport over a plugin-message channel. */
    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(type);
            out.writeUTF(origin);
            out.writeUTF(payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode message " + type, e);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Encoded message is " + encoded.length
                    + " bytes, over the " + MAX_ENCODED_BYTES + " byte limit. Send an id, not a document.");
        }
        return encoded;
    }

    /**
     * Decodes a message produced by {@link #encode()}.
     *
     * @return the message, or null if the bytes are not a well-formed message
     */
    public static Message decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_ENCODED_BYTES) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String type = in.readUTF();
            String origin = in.readUTF();
            String payload = in.readUTF();
            if (type.isBlank()) {
                return null;
            }
            return new Message(type, origin, payload);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
