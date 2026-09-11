package dev.anchorlight.StoneLib.message;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UntrustedTextTest {

    private static boolean containsUnescapedAngleBracket(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '<' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return true;
            }
        }
        return false;
    }

    @Test
    void stripsMiniMessageTagsInsteadOfInterpretingThem() {
        String input = "Check this out <bold><red>free stuff</red></bold> <click:run_command:/op me>";
        String result = UntrustedText.forDisplay(input, 200);

        // Every "<" must be escaped with a preceding backslash: an *unescaped* "<bold>" must not
        // survive (note: the escaped substring "\<bold>" necessarily still contains the literal
        // text "<bold>", so we check for an unescaped occurrence rather than mere substring absence).
        assertTrue(result.contains("\\<bold>"), "the tag must survive as escaped literal text");
        assertFalse(containsUnescapedAngleBracket(result), "raw angle-bracket tags must not be silently swallowed");
        assertEquals("Check this out \\<bold>\\<red>free stuff\\</red>\\</bold> \\<click:run_command:/op me>", result);
    }

    @Test
    void collapsesNewlinesAndControlCharacters() {
        String input = "line one\nline two\r\ttabbed\u0007bell";
        String result = UntrustedText.forDisplay(input, 200);

        assertEquals("line one line two  tabbedbell", result);
    }

    @Test
    void truncatesAtMaxLengthWithEllipsis() {
        String input = "a".repeat(300);
        String result = UntrustedText.forDisplay(input, 50);

        assertEquals(50, result.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    void nullTextBecomesEmptyString() {
        assertEquals("", UntrustedText.forDisplay(null, 50));
    }

    @Test
    void tinyMaxLengthTruncatesWithoutEllipsis() {
        String result = UntrustedText.forDisplay("hello world", 2);
        assertEquals(2, result.length());
        assertEquals("he", result);
    }

    @Test
    void shortInputUnderMaxLengthIsUnchanged() {
        assertEquals("hi there", UntrustedText.forDisplay("hi there", 200));
    }

    @Test
    void unicodeAndEmojiPassThroughUnchanged() {
        String input = "caf\u00e9 \ud83d\ude00 \u65e5\u672c\u8a9e sparkles \u2728";
        assertEquals(input, UntrustedText.forDisplay(input, 200));
    }

    @Test
    void clickAndHoverTagsSurviveAsInertEscapedText() {
        String input = "<click:run_command:/op me><hover:show_text:'nice'>hover text</hover>";
        String result = UntrustedText.forDisplay(input, 300);

        // MiniMessage-deserializing the formatted output must NOT produce click/hover events or
        // bold decoration: the escaped angle brackets must render as literal text.
        var component = MiniMessage.miniMessage().deserialize(result);
        assertNull(component.clickEvent(), "escaped output must not produce a live click event");
        assertNull(component.hoverEvent(), "escaped output must not produce a live hover event");
        assertFalse(component.hasDecoration(TextDecoration.BOLD));
        component.children().forEach(child -> {
            assertNull(child.clickEvent());
            assertNull(child.hoverEvent());
        });

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(component);
        assertTrue(plain.contains("<click:run_command:/op me>"));
        assertTrue(plain.contains("<hover:show_text:'nice'>hover text</hover>"));
    }

    @Test
    void deeplyNestedAdversarialTagsRoundTripAsPlainTextWithNoLiveEvents() {
        String input = "<bold><click:run_command:/op me><hover:show_text:'<red>gotcha</red>'>"
            + "<italic>nested</italic></hover></click></bold>";
        String result = UntrustedText.forDisplay(input, 500);

        var component = MiniMessage.miniMessage().deserialize(result);
        assertNull(component.clickEvent());
        assertNull(component.hoverEvent());
        assertFalse(component.hasDecoration(TextDecoration.BOLD));
        assertFalse(component.hasDecoration(TextDecoration.ITALIC));
        // Recurse: no child component may carry a live click/hover event either.
        component.children().forEach(child -> {
            assertNull(child.clickEvent());
            assertNull(child.hoverEvent());
        });
    }

    @Test
    void multilineTextWithExcessiveWhitespaceIsNormalisedToSingleLine() {
        String input = "  Hello\n\n\n   world!\t\t\r\n  How are you?  ";
        String result = UntrustedText.forDisplay(input, 200);

        // Each whitespace character becomes exactly one space (runs are not collapsed further),
        // and leading/trailing whitespace is trimmed.
        assertEquals("Hello      world!      How are you?", result);
    }

    @Test
    void veryLongTextWithTagsIsTruncatedAndStillInert() {
        String tag = "<click:run_command:/op me>";
        String input = tag.repeat(50);
        String result = UntrustedText.forDisplay(input, 80);

        assertEquals(80, result.length());
        assertTrue(result.endsWith("..."));

        var component = MiniMessage.miniMessage().deserialize(result);
        assertNull(component.clickEvent());
        assertNull(component.hoverEvent());
    }

    @Test
    void backslashPrefixedTagDoesNotBecomeLiveAfterEscaping() {
        // A raw backslash immediately before a tag is the classic bypass: if we escape "<" without
        // first escaping the pre-existing "\", the output becomes "\\<bold>x", which MiniMessage
        // reads as an escaped literal backslash followed by a real, live "<bold>" tag.
        String input = "\\<bold>x";
        String result = UntrustedText.forDisplay(input, 200);

        var component = MiniMessage.miniMessage().deserialize(result);
        assertFalse(component.hasDecoration(TextDecoration.BOLD), "bold must not be live");
        component.children().forEach(child ->
            assertFalse(child.hasDecoration(TextDecoration.BOLD), "no child may carry live bold either"));

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(component);
        assertTrue(plain.contains("\\<bold>x") || plain.contains("<bold>x"),
            "the original text must survive, inertly, as plain text");
    }

    @Test
    void sectionSignFormattingCodesAreStripped() {
        // The legacy Minecraft formatting code marker U+00A7 is interpreted by legacy chat-code
        // parsing paths (e.g. TextDisplay#setText(String)) -- untrusted text containing "§k"/"§4"
        // must not survive into the formatted output as raw bytes, since that would apply live
        // obfuscation/coloring wherever the text is displayed.
        String input = "Big sale §kobfuscated§4red text";
        String result = UntrustedText.forDisplay(input, 200);

        assertFalse(result.contains("§"), "raw section-sign formatting codes must not survive");
        assertEquals("Big sale kobfuscated4red text", result);
    }

    @Test
    void truncationDoesNotSplitSurrogatePair() {
        // Build a string where the truncation boundary (maxLength - 3, for the "..." case) lands
        // exactly on the low surrogate of an emoji, so a naive UTF-16 char-index cut would split
        // the pair and leave a lone high surrogate dangling at the end of the string.
        String emoji = "😀"; // 😀, a surrogate pair
        int maxLength = 50;
        String prefix = "a".repeat(maxLength - 3 - 1); // one char short of the cut point
        String input = prefix + emoji + "trailing text to force truncation".repeat(3);

        String result = UntrustedText.forDisplay(input, maxLength);

        assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)) && !result.endsWith("..."),
            "must not end with a lone high surrogate");
        assertFalse(result.endsWith("\ud83d..."), "surrogate pair must not be split by the cut");
        // The result must be well-formed: no unpaired surrogate anywhere.
        for (int i = 0; i < result.length(); i++) {
            if (Character.isHighSurrogate(result.charAt(i))) {
                assertTrue(i + 1 < result.length() && Character.isLowSurrogate(result.charAt(i + 1)),
                    "high surrogate at index " + i + " must be followed by its low surrogate");
            }
        }
    }
}
