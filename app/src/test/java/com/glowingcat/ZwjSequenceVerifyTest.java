/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.aichat.EmojiReplacer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests ensuring ZWJ emoji sequences (e.g. basketball_man ⛹️‍♂️, black_cat 🐈‍⬛)
 * render as a single combined Twemoji image rather than two separate emoji. These mix BMP and
 * non-BMP code points across the U+200D joiner, which the scanner must span as one unit.
 */
public class ZwjSequenceVerifyTest {

    private static int countImgs(String s) {
        int n = 0, idx = 0;
        while ((idx = s.indexOf("<img ", idx)) >= 0) { n++; idx += 5; }
        return n;
    }

    private static String firstFilename(String html) {
        int a = html.indexOf("assets/svg/") + "assets/svg/".length();
        int b = html.indexOf(".svg", a);
        return html.substring(a, b);
    }

    @Test
    public void basketballManIsOneImage() {
        // ⛹️‍♂️ = 26F9 FE0F 200D 2642 FE0F  (base is BMP)
        String in = "\u26F9\uFE0F\u200D\u2642\uFE0F";
        String out = EmojiReplacer.replaceEmoji(in);
        assertEquals(1, countImgs(out));
        assertEquals("26f9-fe0f-200d-2642-fe0f", firstFilename(out));
    }

    @Test
    public void basketballWomanIsOneImage() {
        // ⛹️‍♀️ = 26F9 FE0F 200D 2640 FE0F
        String in = "\u26F9\uFE0F\u200D\u2640\uFE0F";
        String out = EmojiReplacer.replaceEmoji(in);
        assertEquals(1, countImgs(out));
        assertEquals("26f9-fe0f-200d-2640-fe0f", firstFilename(out));
    }

    @Test
    public void blackCatIsOneImage() {
        // 🐈‍⬛ = 1F408 200D 2B1B  (base non-BMP, ZWJ to BMP ⬛)
        String in = "\uD83D\uDC08\u200D\u2B1B";
        String out = EmojiReplacer.replaceEmoji(in);
        assertEquals(1, countImgs(out));
        assertEquals("1f408-200d-2b1b", firstFilename(out));
    }

    @Test
    public void blackBirdIsOneImage() {
        // 🐦‍⬛ = 1F426 200D 2B1B  (base non-BMP, ZWJ to BMP ⬛)
        String in = "\uD83D\uDC26\u200D\u2B1B";
        String out = EmojiReplacer.replaceEmoji(in);
        // Even if Twemoji v14 lacks this file, it must still be ONE img, not two emoji.
        assertEquals(1, countImgs(out));
        assertEquals("1f426-200d-2b1b", firstFilename(out));
    }

    @Test
    public void zwjSequenceHasGithubFallback() {
        // Twemoji v14 lacks 🐦‍⬛; the fallback chain must include GitHub's hosted PNG,
        // named by code points with the ZWJ and selectors removed (1f426-2b1b.png).
        String out = EmojiReplacer.replaceEmoji("\uD83D\uDC26\u200D\u2B1B");
        assertTrue("should carry a fallback chain", out.contains("data-fallbacks="));
        assertTrue("fallback should point at GitHub's unicode image",
                out.contains("github") && out.contains("1f426-2b1b.png"));
        assertTrue("onerror should advance through fallbacks", out.contains("onerror="));
    }

    @Test
    public void basketballGithubFallbackStripsSelectorsAndJoiner() {
        // 26f9-fe0f-200d-2642-fe0f -> GitHub 26f9-2642.png
        String out = EmojiReplacer.replaceEmoji("\u26F9\uFE0F\u200D\u2642\uFE0F");
        assertTrue(out.contains("26f9-2642.png"));
        assertFalse("GitHub name must not contain the joiner or selector",
                out.contains("26f9-fe0f-200d-2642-fe0f.png"));
    }

    @Test
    public void plainThumbsUpStillOneImage() {
        // Regression: a simple non-BMP emoji still works.
        String out = EmojiReplacer.replaceEmoji("\uD83D\uDC4D");
        assertEquals(1, countImgs(out));
        assertEquals("1f44d", firstFilename(out));
    }
}
