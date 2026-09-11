package dev.anchorlight.StoneLib.message;

/**
 * Turns untrusted text (player-typed input, chat messages, data pulled from an external API,
 * anything not authored by the plugin itself) into plain text that is safe to substitute into a
 * MiniMessage template before it is deserialized. The output must never be re-parsed by
 * {@code MiniMessage.miniMessage().deserialize(...)} on its own and treated as trusted again: this
 * class does not merely "escape" tags in a way a MiniMessage parser might still recognise, it
 * neutralises every {@code <} character (the one MiniMessage requires to open a tag) so that even a
 * template that concatenates this output directly into a larger MiniMessage string cannot have a
 * click or hover event smuggled through it. Every {@code <} in the input becomes a literal
 * backslash-escaped {@code \<}.
 *
 * <p>Ported from a Minecraft-plugin project's Instagram-caption sanitizer, which went through
 * several security-review rounds (including a caught-and-fixed backslash-prefix bypass) before
 * landing on this exact escaping order. Use this for any placeholder value substituted into
 * {@link MessageService} (or any other MiniMessage template) that did not originate from your own
 * plugin's config/code.
 */
public final class UntrustedText {

    private UntrustedText() {
    }

    /**
     * Sanitizes {@code raw} for safe embedding in a MiniMessage template, collapsing whitespace and
     * truncating to at most {@code maxLength} characters (with a trailing {@code "..."} if
     * truncated). Returns {@code ""} for {@code null} input.
     */
    public static String forDisplay(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        // Existing backslashes must be escaped FIRST, before "<" is escaped. Otherwise a raw
        // backslash immediately preceding a tag (e.g. "\<bold>") would combine with the backslash
        // we insert to form "\\<bold>", which MiniMessage reads as an escaped literal backslash
        // followed by a live, unescaped "<bold>" tag - a parser injection bypass. Escaping "\" to
        // "\\" first, then "<" to "\<", mirrors MiniMessage's own escaper and keeps every "<" in
        // the output preceded by exactly one (now-escaped) backslash that cannot be reinterpreted.
        String escaped = raw.replace("\\", "\\\\").replace("<", "\\<");

        StringBuilder collapsed = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (Character.isWhitespace(c)) {
                collapsed.append(' ');
            } else if (Character.isISOControl(c) || c == '§') {
                // Non-whitespace control characters (e.g. BEL) carry no visual meaning and are
                // dropped entirely rather than turning them into a space. The legacy Minecraft
                // formatting code marker U+00A7 ("section sign") is stripped the same way: legacy
                // chat rendering paths interpret it as a live color/format code, so untrusted text
                // containing e.g. "§k"/"§l"/"§4" would apply obfuscation/coloring if left in. This
                // can't inject click/hover events the way an unescaped MiniMessage tag could, but it
                // is still untrusted text reaching a format-interpreting API.
                continue;
            } else {
                collapsed.append(c);
            }
        }

        String result = collapsed.toString().strip();
        if (result.length() <= maxLength) {
            return result;
        }
        if (maxLength <= 3) {
            return result.substring(0, safeCutIndex(result, maxLength));
        }
        return result.substring(0, safeCutIndex(result, maxLength - 3)) + "...";
    }

    /**
     * Returns a cut index no greater than {@code cut} that does not fall between the two UTF-16
     * chars of a surrogate pair. {@code String.substring} operates on UTF-16 char index, not code
     * point; cutting in the middle of a surrogate pair (e.g. an emoji near the boundary) would
     * leave a lone high surrogate dangling at the end of the truncated string. If {@code cut}
     * would split a pair, back off by one character.
     */
    private static int safeCutIndex(String s, int cut) {
        if (cut > 0 && cut < s.length() && Character.isHighSurrogate(s.charAt(cut - 1))
                && Character.isLowSurrogate(s.charAt(cut))) {
            return cut - 1;
        }
        return cut;
    }
}
