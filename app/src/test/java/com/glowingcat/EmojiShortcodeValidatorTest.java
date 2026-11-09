/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.aichat.MarkdownExtras;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for emoji shortcode validation, similar-shortcode suggestions, and the
 * invalid-token scanner. A controlled set of known shortcodes is registered so the
 * assertions do not depend on the full bundled dataset.
 */
public class EmojiShortcodeValidatorTest {

    @BeforeClass
    public static void registerKnown() {
        // Register a known-good set. MarkdownExtras already seeds "smile", "tada", etc.,
        // but we add a few predictable ones so similarity results are deterministic.
        MarkdownExtras.registerEmojiShortcodes(Map.of(
                "smile", "\uD83D\uDE04",
                "smiley", "\uD83D\uDE03",
                "smirk", "\uD83D\uDE0F",
                "heart", "\u2764\uFE0F",
                "rocket", "\uD83D\uDE80"
        ));
    }

    @Test
    public void knownShortcodesAreValid() {
        assertTrue(EmojiShortcodeValidator.isValid("smile"));
        assertTrue(EmojiShortcodeValidator.isValid("ROCKET")); // case-insensitive
        assertTrue(MarkdownExtras.isKnownShortcode("heart"));
    }

    @Test
    public void unknownShortcodesAreInvalid() {
        assertFalse(EmojiShortcodeValidator.isValid("smilee"));
        assertFalse(EmojiShortcodeValidator.isValid("definitely_not_an_emoji_xyz"));
        assertFalse(EmojiShortcodeValidator.isValid(""));
        assertFalse(EmojiShortcodeValidator.isValid(null));
    }

    @Test
    public void prefixSuggestionsRankFirst() {
        // "smil" is a prefix of smile/smiley; both should appear, prefix tier before others.
        List<String> s = EmojiShortcodeValidator.suggest("smil", 8);
        assertTrue("expected smile in suggestions: " + s, s.contains("smile"));
        assertTrue("expected smiley in suggestions: " + s, s.contains("smiley"));
        // smile (len 5) ranks before smiley (len 6) within the prefix tier.
        assertTrue(s.indexOf("smile") < s.indexOf("smiley"));
    }

    @Test
    public void spelledLikeSuggestionsAreFound() {
        // "sm;le" is not a prefix/substring of anything but is edit-distance 1 from "smile".
        List<String> s = EmojiShortcodeValidator.suggest("smil3", 8);
        assertTrue("expected a close match for a typo: " + s, s.contains("smile"));
    }

    @Test
    public void suggestRespectsLimitAndNeverReturnsQuery() {
        List<String> s = EmojiShortcodeValidator.suggest("smile", 2);
        assertTrue(s.size() <= 2);
        assertFalse("must not suggest the query itself", s.contains("smile"));
    }

    @Test
    public void levenshteinBasics() {
        assertEquals(0, EmojiShortcodeValidator.levenshtein("abc", "abc", 3));
        assertEquals(1, EmojiShortcodeValidator.levenshtein("abc", "abd", 3));
        assertEquals(3, EmojiShortcodeValidator.levenshtein("abc", "xyz", 3));
        // Early-exit: distance beyond max returns max+1.
        assertEquals(2, EmojiShortcodeValidator.levenshtein("abc", "xyz", 1));
    }

    @Test
    public void scannerFlagsOnlyInvalidTokensOutsideCode() {
        String text = ""
                + "Valid :smile: and :rocket: here.\n"       // both valid -> not flagged
                + "Bad :smilee: and :notacode: here.\n"      // both invalid -> flagged
                + "`inline :bogus:` should be skipped.\n"    // inside inline code -> skipped
                + "```\n:alsoinvalid:\n```\n";               // inside fenced code -> skipped

        List<int[]> skip =
                com.glowingcat.spellcheck.MarkdownSpellFilter.computeSkipRegions(text);
        List<EmojiShortcodeChecker.InvalidToken> invalid =
                EmojiShortcodeChecker.findInvalid(text, skip);

        // Only the two invalid tokens on line 2 should be flagged.
        assertEquals("flagged: " + describe(invalid, text), 2, invalid.size());
        assertEquals("smilee", invalid.get(0).code());
        assertEquals("notacode", invalid.get(1).code());

        // Offsets point at the literal token including colons.
        for (EmojiShortcodeChecker.InvalidToken t : invalid) {
            String slice = text.substring(t.start(), t.end());
            assertTrue(slice.startsWith(":") && slice.endsWith(":"));
            assertEquals(":" + t.code() + ":", slice);
        }
    }

    private static String describe(List<EmojiShortcodeChecker.InvalidToken> ts, String text) {
        StringBuilder sb = new StringBuilder();
        for (EmojiShortcodeChecker.InvalidToken t : ts) {
            sb.append("[").append(text, t.start(), t.end()).append("]");
        }
        return sb.toString();
    }
}
