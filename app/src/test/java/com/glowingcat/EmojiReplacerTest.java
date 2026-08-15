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
}
