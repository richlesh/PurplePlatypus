/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.aichat.EmojiReplacer;
import com.glowingcat.aichat.SymbolReplacer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the interaction between {@link EmojiReplacer} and {@link SymbolReplacer} in the
 * preview pipeline. EmojiReplacer runs first and turns ⏏️ / ↩️ into {@code <img alt="⏏️">};
 * SymbolReplacer then runs over that HTML. Because ⏏ (U+23CF) and ↩ (U+21A9) are also
 * keyboard-symbol code points, SymbolReplacer must NOT rewrite the character sitting inside
 * the emoji img's alt attribute — doing so corrupts the tag and spills its attributes as text.
 */
public class EmojiSymbolInteractionTest {

    private static int countEmojiImgs(String s) {
        int n = 0, idx = 0;
        while ((idx = s.indexOf("class=\"emoji\"", idx)) >= 0) { n++; idx += 1; }
        return n;
    }

    @Test
    public void symbolReplacerDoesNotCorruptEmojiImg() {
        // Preview order: emoji first, then symbols.
        String emojiHtml = EmojiReplacer.replaceEmoji("<p>Test \u23CF\uFE0F and \u21A9\uFE0F end</p>");
        assertEquals("two emoji imgs expected", 2, countEmojiImgs(emojiHtml));

        String afterSymbols = SymbolReplacer.replaceSymbols(emojiHtml);

        // The emoji imgs must be intact — same count, attributes not spilled.
        assertEquals("emoji imgs must survive SymbolReplacer", 2, countEmojiImgs(afterSymbols));
        // The onerror handler attribute must still be inside a tag, not leaked as text.
        assertTrue(afterSymbols.contains("onerror=\"emojiFallback(this)\""));
        // SymbolReplacer must not have injected its SVG into the emoji alt (no <svg before the
        // first emoji img's closing '>').
        int firstImg = afterSymbols.indexOf("<img");
        int firstImgEnd = afterSymbols.indexOf('>', firstImg);
        String firstTag = afterSymbols.substring(firstImg, firstImgEnd + 1);
        assertFalse("symbol SVG must not be injected inside the emoji tag",
                firstTag.contains("<svg"));
    }

    @Test
    public void symbolReplacerStillWorksOnBareKeyboardGlyphInText() {
        // A bare keyboard glyph in text content (not inside a tag) is still converted.
        String out = SymbolReplacer.replaceSymbols("<p>Press \u23CF to eject</p>");
        assertTrue("bare symbol in text should be replaced", out.contains("<svg") || out.contains("<img"));
    }
}
