/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import com.glowingcat.aichat.MarkdownExtras;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that a GitHub-only emoji shortcode (one with a hosted image but no Unicode
 * character, as registered from https://api.github.com/emojis) renders as an inline
 * {@code <img>} rather than being left as literal text.
 */
public class GitHubEmojiImageTest {

    @Test
    public void githubOnlyShortcodeRendersAsImage() {
        // Simulate what EmojiShortcodeValidator.refreshFromGitHub does for a custom image.
        String url = "https://github.githubassets.com/images/icons/emoji/shipit.png?v8";
        MarkdownExtras.registerCustomEmojiImages(Map.of("shipit_test", url));

        String out = MarkdownExtras.preprocessMarkdown("ship it :shipit_test: now");
        assertTrue("should emit an <img> for the custom shortcode: " + out,
                out.contains("<img") && out.contains(url));
        assertTrue("img should carry the shortcode as alt/title", out.contains(":shipit_test:"));
        assertFalse("literal shortcode should be gone", out.contains("it :shipit_test: now"));
    }

    @Test
    public void unknownShortcodeIsLeftLiteral() {
        // A shortcode that is neither Unicode nor a registered custom image stays literal.
        String out = MarkdownExtras.preprocessMarkdown("nope :definitely_unknown_zzz: nope");
        assertTrue(out.contains(":definitely_unknown_zzz:"));
        assertFalse(out.contains("<img"));
    }
}
