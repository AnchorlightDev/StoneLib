package dev.anchorlight.StoneLib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * MiniMessage parsing with <em>named</em> placeholders, for templates that read
 * {@code "<name> earned <points> points"} rather than {@code "{0} earned {1} points"}.
 *
 * <p>Positional placeholders are fine for a handful of short messages, but they get hard to author
 * and to re-order once a template carries three or four values. Named placeholders keep the template
 * readable and let the call site pass values in any order.
 *
 * <p>Values are substituted with {@link Placeholder#unparsed(String, String)}, exactly as
 * {@link MessageService} does, so a placeholder value can never inject live MiniMessage markup.
 * Only the plugin-authored template itself is parsed as markup. Genuinely untrusted values should
 * still be run through {@link UntrustedText#forDisplay(String, int)} first as defense in depth.
 */
public final class MiniMessages {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessages() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /**
     * Parses {@code template}, resolving {@code <key>} tags from alternating key/value pairs.
     *
     * @param template          plugin-authored MiniMessage text
     * @param keyValuePairs     placeholder name, then value, repeated; an odd trailing entry is ignored
     */
    public static Component parse(String template, Object... keyValuePairs) {
        return MINI_MESSAGE.deserialize(template == null ? "" : template, resolvers(keyValuePairs));
    }

    /** As {@link #parse}, but flattened to plain text - for scoreboards, item names and logs. */
    public static String plain(String template, Object... keyValuePairs) {
        return PlainTextComponentSerializer.plainText().serialize(parse(template, keyValuePairs));
    }

    /** Strips markup from an already-built component. */
    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Builds unparsed resolvers from alternating key/value pairs. */
    public static TagResolver[] resolvers(Object... keyValuePairs) {
        if (keyValuePairs == null || keyValuePairs.length < 2) {
            return new TagResolver[0];
        }
        List<TagResolver> out = new ArrayList<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            String key = String.valueOf(keyValuePairs[i]);
            Object value = keyValuePairs[i + 1];
            out.add(Placeholder.unparsed(key, value == null ? "" : String.valueOf(value)));
        }
        return out.toArray(new TagResolver[0]);
    }
}
