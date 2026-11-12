/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests "Copy Markdown" conversion of preview selections. Inputs mimic what
 * preview_selection.js produces (ancestor-wrapped HTML with PPZTOK placeholders).
 */
public class PreviewSelectionConverterTest {

    private static String md(String html, String... tokens) {
        return PreviewSelectionConverter.toMarkdown(html, List.of(tokens));
    }

    @Test
    public void inlineFormattingAndLinks() {
        assertEquals("Some **bold**, *em*, ~~gone~~, `code` and [link](https://x.io).",
                md("<p>Some <strong>bold</strong>, <em>em</em>, <del>gone</del>, <code>code</code>"
                        + " and <a href=\"https://x.io\" class=\"url-link\">link</a>.</p>"));
    }

    @Test
    public void appExtras() {
        assertEquals("H~2~O, x^2^ and ++under++.",
                md("<p>H<sub>2</sub>O, x<sup>2</sup> and <ins>under</ins>.</p>"));
    }

    @Test
    public void atxHeadingWithoutIdAttribute() {
        assertEquals("## Getting Started", md("<h2 id=\"getting-started\">Getting Started</h2>"));
    }

    @Test
    public void partialSelectionInsideListItemKeepsBullet() {
        // Selecting a word inside one list item: JS wraps it in the li and ul shells.
        assertEquals("- partial", md("<ul><li>partial</li></ul>"));
    }

    @Test
    public void nestedListsAndTaskItems() {
        String out = md("<ul><li>one<ul><li>nested</li></ul></li>"
                + "<li class=\"task-list-item\"><input type=\"checkbox\" disabled=\"\" checked=\"\"> done</li></ul>");
        assertEquals("- one\n  - nested\n- [x] done", out);
    }

    @Test
    public void tableKeepsAlignment() {
        String out = md("<table><thead><tr><th align=\"left\">A</th><th align=\"right\">B</th></tr></thead>"
                + "<tbody><tr><td align=\"left\">1</td><td align=\"right\">2</td></tr></tbody></table>");
        assertEquals("| A | B |\n|:--|--:|\n| 1 | 2 |", out);
    }

    @Test
    public void codeBlockKeepsLanguageAndContent() {
        String out = md("<pre><code class=\"language-java\"><span class=\"hljs-keyword\">int</span> x = a * ;</code></pre>");
        assertEquals("```java\nint x = a * ;\n```", out);
    }

    @Test
    public void mermaidBlockBecomesFencedSource() {
        String out = md("<pre><code class=\"language-mermaid\">graph TD\n  A --&gt; B</code></pre>");
        assertEquals("```mermaid\ngraph TD\n  A --> B\n```", out);
    }

    @Test
    public void tokensAreInsertedVerbatim() {
        // Math TeX with backslashes and underscores must not be escaped.
        String out = md("<p>Euler PPZTOK0PP and PPZTOK1PP</p>",
                "$e^{i\\pi} + 1 = 0$", "$$\\sum_{n=1}^{\\infty} x_n$$");
        assertEquals("Euler $e^{i\\pi} + 1 = 0$ and $$\\sum_{n=1}^{\\infty} x_n$$", out);
    }

    @Test
    public void imagesUseTokenNotEmojiShortcode() {
        // flexmark alone would turn cat.png into :cat:
        String out = md("<p>A PPZTOK0PP here</p>", "![A cat](img/cat.png){width=120}");
        assertEquals("A ![A cat](img/cat.png){width=120} here", out);
    }

    @Test
    public void alertMarkerHasNoBlankLineAfterIt() {
        String out = md("<blockquote><p>PPZTOK0PP</p><p>Alert body</p></blockquote>", "[!WARNING]");
        assertEquals("> [!WARNING]\n> Alert body", out);
    }

    @Test
    public void footnoteReferenceAndDefinition() {
        String out = md("<p>Text PPZTOK0PP</p><p>PPZTOK1PPThe note.</p>", "[^1]", "[^1]: ");
        assertEquals("Text [^1]\n\n[^1]: The note.", out);
    }

    @Test
    public void emojiTextSurvives() {
        assertEquals("Ship it 🎉 :shipit: ⌘", md("<p>Ship it 🎉 :shipit: ⌘</p>"));
    }

    @Test
    public void unknownTokenIndexIsLeftAlone() {
        assertTrue(md("<p>PPZTOK5PP</p>").contains("PPZTOK5PP"));
    }

    @Test
    public void blankInputGivesEmpty() {
        assertEquals("", PreviewSelectionConverter.toMarkdown("  ", null));
        assertEquals("", PreviewSelectionConverter.toMarkdown(null, null));
        assertFalse(md("<p>x</p>").isEmpty());
    }
}
