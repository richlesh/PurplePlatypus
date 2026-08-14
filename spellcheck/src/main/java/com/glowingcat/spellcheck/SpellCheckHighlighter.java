/*
 * (c) 2026 Glowing Cat Software
 */
package com.glowingcat.spellcheck;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages squiggly-underline highlights on the editor for misspelled words.
 */
public class SpellCheckHighlighter {

    /** Custom painter that draws a red squiggly underline beneath text. */
    private static final Highlighter.HighlightPainter SQUIGGLY_PAINTER = new SquigglyPainter();

    private final RSyntaxTextArea textArea;
    private final List<Object> highlightTags = new ArrayList<>();
    private List<SpellCheckService.SpellError> currentErrors = Collections.emptyList();

    public SpellCheckHighlighter(RSyntaxTextArea textArea) {
        this.textArea = textArea;
    }

    /**
     * Applies squiggly-underline highlights for the given errors, filtering out
     * any that fall within skip regions.
     *
     * @param errors      spelling errors found in the text
     * @param skipRegions regions of text to ignore (code blocks, URLs, etc.)
     */
    public void applyHighlights(List<SpellCheckService.SpellError> errors, List<int[]> skipRegions) {
        clearHighlights();

        List<SpellCheckService.SpellError> filtered = new ArrayList<>();
        for (SpellCheckService.SpellError error : errors) {
            if (!isInSkipRegion(error.startOffset(), error.endOffset(), skipRegions)) {
                filtered.add(error);
            }
        }

        Highlighter highlighter = textArea.getHighlighter();
        for (SpellCheckService.SpellError error : filtered) {
            try {
                Object tag = highlighter.addHighlight(
                        error.startOffset(), error.endOffset(), SQUIGGLY_PAINTER);
                highlightTags.add(tag);
            } catch (BadLocationException e) {
                // Offset out of range — skip this highlight
            }
        }
        currentErrors = filtered;
    }

    /** Removes all spell-check highlights from the editor. */
    public void clearHighlights() {
        Highlighter highlighter = textArea.getHighlighter();
        for (Object tag : highlightTags) {
            highlighter.removeHighlight(tag);
        }
        highlightTags.clear();
        currentErrors = Collections.emptyList();
    }

    /**
     * Returns the spell error at the given document offset, or null if none.
     *
     * @param offset caret/mouse position in the document
     * @return the error spanning that offset, or null
     */
    public SpellCheckService.SpellError getErrorAtOffset(int offset) {
        for (SpellCheckService.SpellError error : currentErrors) {
            if (offset >= error.startOffset() && offset <= error.endOffset()) {
                return error;
            }
        }
        return null;
    }

    /** Returns the list of currently highlighted errors. */
    public List<SpellCheckService.SpellError> getCurrentErrors() {
        return Collections.unmodifiableList(currentErrors);
    }

    private boolean isInSkipRegion(int start, int end, List<int[]> skipRegions) {
        for (int[] region : skipRegions) {
            // Error overlaps with a skip region
            if (start < region[1] && end > region[0]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paints a red squiggly (wave) underline beneath the highlighted text.
     */
    private static class SquigglyPainter implements Highlighter.HighlightPainter {

        private static final Color SQUIGGLY_COLOR = new Color(255, 0, 0, 180);
        private static final int WAVE_HEIGHT = 2;
        private static final int WAVE_WIDTH = 4;

        @Override
        public void paint(Graphics g, int offs0, int offs1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView(offs0);
                Rectangle r1 = c.modelToView(offs1);
                if (r0 == null || r1 == null) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SQUIGGLY_COLOR);
                g2.setStroke(new BasicStroke(1.0f));

                // Draw on each line the text spans
                if (r0.y == r1.y) {
                    // Single line
                    drawWave(g2, r0.x, r1.x + r1.width, r0.y + r0.height - 1);
                } else {
                    // Multi-line: draw wave on each line
                    int textWidth = c.getWidth();
                    // First line: from start to end of line
                    drawWave(g2, r0.x, textWidth, r0.y + r0.height - 1);
                    // Last line: from start of line to end of highlight
                    drawWave(g2, 0, r1.x + r1.width, r1.y + r1.height - 1);
                    // Middle lines (if any)
                    int lineHeight = r0.height;
                    for (int y = r0.y + lineHeight; y < r1.y; y += lineHeight) {
                        drawWave(g2, 0, textWidth, y + lineHeight - 1);
                    }
                }
                g2.dispose();
            } catch (BadLocationException e) {
                // Cannot compute view — skip painting
            }
        }

        private void drawWave(Graphics2D g2, int x1, int x2, int y) {
            int x = x1;
            while (x < x2) {
                int nextX = Math.min(x + WAVE_WIDTH, x2);
                int midX = x + WAVE_WIDTH / 2;
                if (midX > x2) midX = x2;
                // Draw a small arc up then down
                g2.drawLine(x, y, midX, y - WAVE_HEIGHT);
                g2.drawLine(midX, y - WAVE_HEIGHT, nextX, y);
                x = nextX;
            }
        }
    }
}
