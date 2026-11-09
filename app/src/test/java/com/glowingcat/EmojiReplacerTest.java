package com.glowingcat;

import com.glowingcat.aichat.EmojiReplacer;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmojiReplacerTest {

    @Test
    public void testNullInput() {
        assertNull(EmojiReplacer.replaceEmoji(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", EmojiReplacer.replaceEmoji(""));
    }

    @Test
    public void testAsciiOnly() {
        String input = "<p>Hello, world!</p>";
        assertEquals(input, EmojiReplacer.replaceEmoji(input));
    }

    @Test
    public void testBmpOnly() {
        // BMP characters (including accented chars) should not be replaced
        String input = "<p>Héllo wörld café</p>";
        assertEquals(input, EmojiReplacer.replaceEmoji(input));
    }

    @Test
    public void testSingleEmoji() {
        // U+1F389 = party popper (🎉)
        String input = "Hello \uD83C\uDF89 world";
        String result = EmojiReplacer.replaceEmoji(input);
        assertTrue("Should contain img tag", result.contains("<img src=\""));
        assertTrue("Should reference twemoji CDN", result.contains("cdn.jsdelivr.net"));
        assertTrue("Should have correct code point", result.contains("1f389.svg"));
        assertTrue("Should have emoji class", result.contains("class=\"emoji\""));
        assertTrue("Should preserve surrounding text", result.startsWith("Hello "));
        assertTrue("Should preserve surrounding text", result.endsWith(" world"));
    }

    @Test
    public void testMultipleEmoji() {
        // U+1F680 = rocket (🚀), U+2764 = heart (BMP, should stay), U+1F44D = thumbs up (👍)
        String input = "\uD83D\uDE80 and \uD83D\uDC4D";
        String result = EmojiReplacer.replaceEmoji(input);
        assertTrue("Should have rocket", result.contains("1f680.svg"));
        assertTrue("Should have thumbs up", result.contains("1f44d.svg"));
        assertTrue("Should preserve 'and'", result.contains(" and "));
    }

    @Test
    public void testEmojiInHtml() {
        String input = "<p>Great job \uD83D\uDC4D</p>";
        String result = EmojiReplacer.replaceEmoji(input);
        assertTrue("Should start with p tag", result.startsWith("<p>"));
        assertTrue("Should end with p tag", result.endsWith("</p>"));
        assertTrue("Should contain emoji img", result.contains("1f44d.svg"));
    }

    @Test
    public void testOnerrorFallback() {
        // Verify the onerror handler strips -fe0f for fallback
        String input = "\uD83C\uDF89";  // 🎉
        String result = EmojiReplacer.replaceEmoji(input);
        assertTrue("Should have onerror fallback", result.contains("onerror="));
    }

    @Test
    public void testEmojiCssNotEmpty() {
        String css = EmojiReplacer.emojiCss();
        assertNotNull(css);
        assertTrue("Should contain emoji class", css.contains("img.emoji"));
        assertTrue("Should set height", css.contains("height"));
    }

    // ---- BMP emoji handling (routed through Twemoji for consistent color) ----

    @Test
    public void testTextDefaultBmpWithSelectorBecomesImage() {
        // ✔️ = U+2714 U+FE0F (heavy check mark). Text-default symbol + emoji selector.
        String result = EmojiReplacer.replaceEmoji("done \u2714\uFE0F here");
        assertTrue("Should emit twemoji img", result.contains("<img src=\""));
        assertTrue("Twemoji filename strips FE0F -> 2714.svg", result.contains("2714.svg"));
        assertFalse("Must not use the FE0F-suffixed filename", result.contains("2714-fe0f.svg"));
        assertTrue("Keeps surrounding text", result.startsWith("done ") && result.endsWith(" here"));
    }

    @Test
    public void testFrowningFaceBecomesImage() {
        // ☹️ = U+2639 U+FE0F
        String result = EmojiReplacer.replaceEmoji("\u2639\uFE0F");
        assertTrue(result.contains("2639.svg"));
    }

    @Test
    public void testEmojiDefaultBmpBecomesImageWithoutSelector() {
        // ⭐ = U+2B50 and ✅ = U+2705 are emoji-presentation by default (no FE0F needed).
        assertTrue(EmojiReplacer.replaceEmoji("\u2B50").contains("2b50.svg"));
        assertTrue(EmojiReplacer.replaceEmoji("\u2705").contains("2705.svg"));
    }

    @Test
    public void testBareTextDefaultSymbolsAreNotConverted() {
        // Without FE0F, text-default symbols must stay as plain text (no regression for
        // ordinary prose using ©, ™, or arrows).
        assertEquals("\u00A9 2026", EmojiReplacer.replaceEmoji("\u00A9 2026"));   // ©
        assertEquals("brand\u2122", EmojiReplacer.replaceEmoji("brand\u2122"));   // ™
        assertEquals("a\u2194b", EmojiReplacer.replaceEmoji("a\u2194b"));         // ↔
        assertEquals("warn \u26A0", EmojiReplacer.replaceEmoji("warn \u26A0"));   // ⚠ (no FE0F)
    }

    @Test
    public void testCopyrightWithSelectorIsConverted() {
        // ©️ with the emoji selector SHOULD become an image.
        String result = EmojiReplacer.replaceEmoji("\u00A9\uFE0F");
        assertTrue(result.contains("a9.svg"));
    }

    @Test
    public void testOnerrorAttributeIsHtmlEscaped() {
        // The onerror fallback JS must be HTML-escaped: an unescaped '&' (from '&&') in an
        // attribute value is invalid HTML and causes parsers to truncate the attribute and
        // spill the rest of the tag as visible text. Verify no raw '&' appears except as a
        // proper '&amp;' entity, and no raw '<' / '>' appear inside the onerror value.
        String html = EmojiReplacer.replaceEmoji("\uD83D\uDC26\u200D\u2B1B"); // black_bird (has fallbacks)
        int oi = html.indexOf("onerror=\"");
        assertTrue("expected an onerror attribute", oi >= 0);
        int start = oi + "onerror=\"".length();
        int end = html.indexOf('"', start);
        String onerror = html.substring(start, end);
        // Any '&' in the attribute must be the start of an entity (e.g. &amp;), never bare.
        for (int i = 0; i < onerror.length(); i++) {
            if (onerror.charAt(i) == '&') {
                assertTrue("bare '&' in onerror attribute: " + onerror,
                        onerror.startsWith("amp;", i + 1)
                        || onerror.startsWith("lt;", i + 1)
                        || onerror.startsWith("gt;", i + 1)
                        || onerror.startsWith("quot;", i + 1));
            }
        }
        assertFalse("onerror must not contain a raw '<'", onerror.contains("<"));
        assertFalse("onerror must not contain a raw '>'", onerror.contains(">"));
    }
}
