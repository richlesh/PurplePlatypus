/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat;

import org.fife.ui.rsyntaxtextarea.modes.MarkdownTokenMaker;

/**
 * Works around a token-generation bug in RSyntaxTextArea 3.5.3's
 * {@link MarkdownTokenMaker}.
 *
 * <p>When a {@code '#'} appears mid-line (i.e. it is <em>not</em> a valid ATX
 * heading), the stock tokenizer emits two <em>overlapping</em> tokens for the
 * character that follows the hash. For the input {@code "Test #1"} it produces:
 *
 * <pre>
 *   off=5 len=2 text=[#1]   &lt;- token covering "#1"
 *   off=6 len=1 text=[1]    &lt;- duplicate token covering "1"
 * </pre>
 *
 * <p>For longer runs such as {@code "Test #1234"} the second token only
 * <em>partially</em> overlaps the first:
 *
 * <pre>
 *   off=5 len=2 text=[#1]     &lt;- token covering "#1"
 *   off=6 len=4 text=[1234]   &lt;- token starting inside the previous one
 * </pre>
 *
 * In both cases the character at offset 6 ("1") is painted twice, so
 * {@code "#1234"} renders as {@code "#11234"}.
 *
 * <p>The token painter draws both, so the character after {@code '#'} is
 * rendered twice on screen (e.g. {@code "#1"} looks like {@code "#11"}). The
 * document text itself is correct; only the painting is wrong, and only after
 * {@code '#'} (other punctuation such as {@code '$'} or {@code '!'} tokenizes
 * cleanly).
 *
 * <p>A related manifestation of the same defect: when {@code '#'} is the last
 * character on a (non-heading) line, the stock tokenizer still emits a
 * length-2 token starting at the hash, reading <em>one character past the end
 * of the line</em>. That phantom character is painted as an undefined-glyph
 * box after the hash. This subclass also clamps every token to the line's last
 * valid index to suppress that box.
 *
 * <p>This subclass intercepts {@link #addToken(char[], int, int, int, int)} and
 * trims (or drops) any token that starts at or before the end of the token that
 * was just added, so no character is covered by two consecutive tokens and each
 * character is painted exactly once.
 */
public class FixedMarkdownTokenMaker extends MarkdownTokenMaker {

    /** End index (inclusive) of the most recently added token, or -1. */
    private int lastTokenEnd = -1;
    /** Last valid array index for the line currently being scanned, or -1. */
    private int lineEndIndex = -1;

    @Override
    public void addToken(char[] array, int start, int end, int tokenType, int startOffset) {
        // Clamp the token to the line's last valid index. RSTA's '#' token is
        // always length 2 (hash + next char); when the hash is the last char on
        // the line, this would otherwise read one char past end-of-line and
        // paint an undefined-glyph box.
        if (lineEndIndex >= 0 && end > lineEndIndex) {
            end = lineEndIndex;
        }
        if (end < start) {
            return;
        }
        if (lastTokenEnd >= 0 && start <= lastTokenEnd) {
            // The new token starts inside the previous token's range (the
            // spurious overlap RSTA emits after a non-heading '#'). Trim its
            // start so it begins right after the previous token ends.
            int newStart = lastTokenEnd + 1;
            int delta = newStart - start;
            if (newStart > end) {
                // Fully contained in the previous token: drop it entirely.
                return;
            }
            start = newStart;
            startOffset += delta;
        }
        super.addToken(array, start, end, tokenType, startOffset);
        lastTokenEnd = end;
    }

    @Override
    public org.fife.ui.rsyntaxtextarea.Token getTokenList(
            javax.swing.text.Segment text, int initialTokenType, int startOffset) {
        // Reset per-line overlap tracking before the superclass scans the line.
        lastTokenEnd = -1;
        // Last valid index of the backing char array for this line.
        lineEndIndex = text.offset + text.count - 1;
        return super.getTokenList(text, initialTokenType, startOffset);
    }
}
